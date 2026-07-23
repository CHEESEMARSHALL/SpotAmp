package com.example.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.playback.TrackItem
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

object LastFmScrobbler {
    private const val TAG = "LastFmScrobbler"
    private const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"

    private val client = OkHttpClient.Builder()
        .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun requestAuthorization(settings: PlexSettingsManager): String = withContext(Dispatchers.IO) {
        requireApiConfiguration()
        val params = mutableMapOf("method" to "auth.getToken", "api_key" to BuildConfig.LASTFM_API_KEY)
        params["api_sig"] = calculateSignature(params)
        params["format"] = "json"
        val body = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build()
        val response = client.newCall(Request.Builder().url(BASE_URL).post(body).build()).execute()
        response.use {
            check(it.isSuccessful) { "Last.fm authorization request failed (HTTP ${it.code})" }
            val json = JSONObject(it.body?.string().orEmpty())
            if (json.has("error")) error(json.optString("message", "Last.fm authorization failed"))
            val token = json.optString("token").trim()
            if (token.isBlank()) error("Last.fm did not return an authorization token")
            settings.lastFmPendingToken = token
            "https://www.last.fm/api/auth/?api_key=${urlEncode(BuildConfig.LASTFM_API_KEY)}&token=${urlEncode(token)}"
        }
    }

    suspend fun completeAuthorization(settings: PlexSettingsManager): String = withContext(Dispatchers.IO) {
        requireApiConfiguration()
        val token = settings.lastFmPendingToken
        if (token.isBlank()) error("Start Last.fm authorization first")
        val params = mutableMapOf("method" to "auth.getSession", "api_key" to BuildConfig.LASTFM_API_KEY, "token" to token)
        params["api_sig"] = calculateSignature(params)
        params["format"] = "json"
        val body = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build()
        val response = client.newCall(Request.Builder().url(BASE_URL).post(body).build()).execute()
        response.use {
            check(it.isSuccessful) { "Last.fm session request failed (HTTP ${it.code})" }
            val json = JSONObject(it.body?.string().orEmpty())
            if (json.has("error")) error(json.optString("message", "Last.fm rejected authorization"))
            val session = json.optJSONObject("session") ?: error("Last.fm did not return a session")
            val sessionKey = session.optString("key").trim()
            val username = session.optString("name").trim()
            if (sessionKey.isBlank() || username.isBlank()) error("Last.fm returned an incomplete session")
            settings.lastFmSessionKey = sessionKey
            settings.lastFmUsername = username
            settings.lastFmPendingToken = ""
            settings.lastFmEnabled = true
            settings.lastFmUsername
        }
    }

    fun updateNowPlaying(context: Context, settings: PlexSettingsManager, track: TrackItem) {
        if (!isConfigured(settings)) return

        scope.launch {
            Log.d(TAG, "Updating Now Playing on Last.fm for track: ${track.title} by ${track.artist}")
            val sessionKey = settings.lastFmSessionKey
            if (sessionKey.isEmpty()) {
                showToast(context, "Last.fm: Now playing '${track.title}'")
                return@launch
            }

            try {
                val params = mutableMapOf(
                    "method" to "track.updateNowPlaying",
                    "artist" to track.artist,
                    "track" to track.title,
                    "album" to track.album,
                    "api_key" to BuildConfig.LASTFM_API_KEY,
                    "sk" to sessionKey
                )
                val signature = calculateSignature(params)
                params["api_sig"] = signature
                params["format"] = "json"

                val bodyBuilder = FormBody.Builder()
                params.forEach { (k, v) -> bodyBuilder.add(k, v) }

                val request = Request.Builder()
                    .url(BASE_URL)
                    .post(bodyBuilder.build())
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "UpdateNowPlaying completed: HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    val json = body.toJsonOrNull()
                    if (response.isSuccessful && json?.has("error") != true) {
                        showToast(context, "Last.fm: Updated Now Playing")
                    } else {
                        Log.e(TAG, "Failed updateNowPlaying: HTTP ${response.code}, ${json?.optString("message") ?: body.take(200)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating now playing", e)
            }
        }
    }

    fun scrobble(context: Context, settings: PlexSettingsManager, track: TrackItem) {
        if (!isConfigured(settings)) return

        scope.launch {
            Log.d(TAG, "Scrobbling to Last.fm: ${track.title} by ${track.artist}")
            val sessionKey = settings.lastFmSessionKey
            if (sessionKey.isEmpty()) {
                showToast(context, "Last.fm: Scrobbling '${track.title}'")
                return@launch
            }

            try {
                val timestamp = (System.currentTimeMillis() / 1000).toString()
                val params = mutableMapOf(
                    "method" to "track.scrobble",
                    "artist" to track.artist,
                    "track" to track.title,
                    "album" to track.album,
                    "timestamp" to timestamp,
                    "api_key" to BuildConfig.LASTFM_API_KEY,
                    "sk" to sessionKey
                )
                val signature = calculateSignature(params)
                params["api_sig"] = signature
                params["format"] = "json"

                val bodyBuilder = FormBody.Builder()
                params.forEach { (k, v) -> bodyBuilder.add(k, v) }

                val request = Request.Builder()
                    .url(BASE_URL)
                    .post(bodyBuilder.build())
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "Scrobble completed: HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    val json = body.toJsonOrNull()
                    if (response.isSuccessful && json?.has("error") != true) {
                        showToast(context, "Last.fm scrobble successful!")
                    } else {
                        Log.e(TAG, "Failed scrobble: HTTP ${response.code}, ${json?.optString("message") ?: body.take(200)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scrobbling", e)
            }
        }
    }

    private fun calculateSignature(params: Map<String, String>): String {
        val sortedKeys = params.keys.sorted()
        val signatureBuilder = StringBuilder()
        for (key in sortedKeys) {
            signatureBuilder.append(key).append(params[key])
        }
        signatureBuilder.append(BuildConfig.LASTFM_API_SECRET)
        return md5(signatureBuilder.toString())
    }

    private fun isConfigured(settings: PlexSettingsManager): Boolean {
        return settings.lastFmEnabled &&
            settings.lastFmUsername.isNotEmpty() &&
            settings.lastFmSessionKey.isNotEmpty() &&
            BuildConfig.LASTFM_API_KEY.isNotBlank() &&
            !BuildConfig.LASTFM_API_KEY.contains("YOUR_LASTFM") &&
            BuildConfig.LASTFM_API_SECRET.isNotBlank() &&
            !BuildConfig.LASTFM_API_SECRET.contains("YOUR_LASTFM")
    }

    private fun requireApiConfiguration() {
        if (BuildConfig.LASTFM_API_KEY.isBlank() || BuildConfig.LASTFM_API_KEY.contains("PLACEHOLDER") ||
            BuildConfig.LASTFM_API_SECRET.isBlank() || BuildConfig.LASTFM_API_SECRET.contains("PLACEHOLDER")) {
            error("Last.fm API key/secret is missing. Add LASTFM_API_KEY and LASTFM_API_SECRET to the ignored .env file, then rebuild.")
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun String.toJsonOrNull(): JSONObject? =
        runCatching { JSONObject(this) }.getOrNull()

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun showToast(context: Context, message: String) {
        scope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
