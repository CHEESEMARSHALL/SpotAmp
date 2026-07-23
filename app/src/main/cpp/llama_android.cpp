#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <android/log.h>
#include <sys/stat.h>
#include <cerrno>
#include "llama.h"
#include "ggml-backend.h"

struct NativeModel { llama_model * model; };

static bool model_load_progress(float progress, void *) {
    static int lastPercent = -1;
    const int percent = static_cast<int>(progress * 100.0f);
    if (percent >= 100 || percent - lastPercent >= 10) {
        lastPercent = percent;
        __android_log_print(ANDROID_LOG_INFO, "LlamaCppBridge", "GGUF load progress=%d%%", percent);
    }
    return true;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_data_LlamaCppBridge_initModel(JNIEnv *env, jobject, jstring path, jint contextSize, jfloat) {
    llama_backend_init();
    const char *modelPath = env->GetStringUTFChars(path, nullptr);
    struct stat fileStat{};
    if (stat(modelPath, &fileStat) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, "LlamaCppBridge", "GGUF stat failed path=%s errno=%d", modelPath, errno);
    } else {
        __android_log_print(ANDROID_LOG_INFO, "LlamaCppBridge", "Loading GGUF size=%lld path=%s", (long long) fileStat.st_size, modelPath);
    }
    llama_model_params params = llama_model_default_params();
    // Phones should use the CPU backend explicitly. The default -1 asks llama.cpp
    // to offload all layers to any discovered GPU backend, which is unreliable on
    // this Android build and can cause very long allocation/repacking stalls.
    params.n_gpu_layers = 0;
    // The Android app-private file can fault with SIGBUS when llama.cpp reads
    // quantized weights through mmap under memory pressure. Load the weights
    // into resident native buffers instead so a bad/incomplete file fails during
    // model loading rather than crashing during the first matmul.
    params.use_mmap = false;
    params.use_mlock = false;
    params.use_extra_bufts = false;
    params.progress_callback = model_load_progress;
    llama_model *model = llama_model_load_from_file(modelPath, params);
    env->ReleaseStringUTFChars(path, modelPath);
    if (!model) {
        __android_log_print(ANDROID_LOG_ERROR, "LlamaCppBridge", "Failed to load GGUF model");
        return 0;
    }
    auto *holder = new NativeModel{model};
    return reinterpret_cast<jlong>(holder);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_data_LlamaCppBridge_generate(JNIEnv *env, jobject, jlong handle, jstring prompt, jstring systemPrompt, jint maxTokens, jfloat temperature) {
    auto *holder = reinterpret_cast<NativeModel *>(handle);
    if (!holder || !holder->model) return env->NewStringUTF("{}");
    const char *promptText = env->GetStringUTFChars(prompt, nullptr);
    const char *systemText = env->GetStringUTFChars(systemPrompt, nullptr);
    std::string text;
    const char *chatTemplate = llama_model_chat_template(holder->model, nullptr);
    if (chatTemplate) {
        llama_chat_message messages[2] = {
            {"system", systemText},
            {"user", promptText}
        };
        int32_t needed = llama_chat_apply_template(chatTemplate, messages, 2, true, nullptr, 0);
        if (needed > 0) {
            std::vector<char> formatted(needed + 1);
            llama_chat_apply_template(chatTemplate, messages, 2, true, formatted.data(), formatted.size());
            text.assign(formatted.data(), needed);
        }
    }
    if (text.empty()) {
        text = std::string(systemText) + "\n\nUser: " + promptText + "\nAssistant:";
    }
    env->ReleaseStringUTFChars(prompt, promptText);
    env->ReleaseStringUTFChars(systemPrompt, systemText);
    const llama_vocab *vocab = llama_model_get_vocab(holder->model);
    int n = -llama_tokenize(vocab, text.c_str(), text.size(), nullptr, 0, true, true);
    __android_log_print(ANDROID_LOG_INFO, "LlamaCppBridge", "Preparing inference promptBytes=%zu promptTokens=%d maxTokens=%d", text.size(), n, maxTokens);
    std::vector<llama_token> tokens(n);
    llama_tokenize(vocab, text.c_str(), text.size(), tokens.data(), tokens.size(), true, true);
    llama_context_params ctxParams = llama_context_default_params();
    // Keep the temporary KV/work buffers proportional to this short JSON task.
    // A full 2048 context plus a 512-token batch can push Android into reclaim
    // pressure even after a sub-768 MB model has loaded successfully.
    ctxParams.n_ctx = std::max(512, n + (int)maxTokens + 32);
    ctxParams.n_batch = std::min(256, std::max(32, n));
    ctxParams.n_threads = 2;
    ctxParams.n_threads_batch = 2;
    llama_context *ctx = llama_init_from_model(holder->model, ctxParams);
    if (!ctx) {
        __android_log_print(ANDROID_LOG_ERROR, "LlamaCppBridge", "Failed to create inference context nCtx=%u nBatch=%u", ctxParams.n_ctx, ctxParams.n_batch);
        return env->NewStringUTF("{}");
    }
    __android_log_print(ANDROID_LOG_INFO, "LlamaCppBridge", "Inference context ready nCtx=%u nBatch=%u", ctxParams.n_ctx, ctxParams.n_batch);
    auto samplerParams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(samplerParams);
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    int promptDecode = llama_decode(ctx, batch);
    __android_log_print(ANDROID_LOG_INFO, "LlamaCppBridge", "Prompt decode completed result=%d", promptDecode);
    if (promptDecode != 0) { llama_sampler_free(sampler); llama_free(ctx); return env->NewStringUTF("{}"); }
    std::string output;
    for (int i = 0; i < maxTokens; ++i) {
        llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        char piece[256]; int len = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (len > 0) output.append(piece, len);
        if ((i + 1) % 16 == 0) __android_log_print(ANDROID_LOG_INFO, "LlamaCppBridge", "Generation progress tokens=%d outputBytes=%zu", i + 1, output.size());
        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) break;
    }
    __android_log_print(ANDROID_LOG_INFO, "LlamaCppBridge", "Generation completed tokens=%d outputBytes=%zu", maxTokens, output.size());
    llama_sampler_free(sampler); llama_free(ctx);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_data_LlamaCppBridge_freeModel(JNIEnv *, jobject, jlong handle) {
    auto *holder = reinterpret_cast<NativeModel *>(handle);
    if (holder) { llama_model_free(holder->model); delete holder; }
}
