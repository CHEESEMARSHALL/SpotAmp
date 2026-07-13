package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.playback.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

// ==============================================================
// 1. Sonic Sage Structured PlaylistIntent JSON Schema
// ==============================================================
@Serializable
data class PlaylistIntent(
    val seedArtists: List<String> = emptyList(),
    val seedAlbums: List<String> = emptyList(),
    val seedTracks: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val energyLevel: String? = null, // "high", "medium", "low"
    val decadeRange: String? = null, // e.g. "2000s", "1990s"
    val includeCollections: Boolean = true,
    val excludeGenres: List<String> = emptyList(),
    val excludeArtists: List<String> = emptyList(),
    val preferFamiliar: Boolean = true,
    val preferDeepCuts: Boolean = false,
    val targetTrackCount: Int = 30,
    val sortStrategy: String? = null,
    val explanation: String? = null
)

// ==============================================================
// 2. AIProvider Interface & Implementations (Cloud, Local, NoAI)
// ==============================================================
interface AIProvider {
    val name: String
    suspend fun generatePlaylistIntent(prompt: String, cachedTracks: List<CachedTrack>): PlaylistIntent
}

class CloudAIProvider : AIProvider {
    override val name = "Cloud AI (Gemini 3.5 Flash)"

    override suspend fun generatePlaylistIntent(prompt: String, cachedTracks: List<CachedTrack>): PlaylistIntent = withContext(Dispatchers.IO) {
        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        if (geminiApiKey.isEmpty() || geminiApiKey.contains("GEMINI_API_KEY")) {
            throw IllegalStateException("Gemini API key is not configured in the Secrets panel.")
        }

        // We summarize artists and genres to help Gemini select correct seed info
        val availableArtists = cachedTracks.map { it.artist }.distinct().take(100).joinToString(", ")
        val availableAlbums = cachedTracks.map { it.album }.distinct().take(50).joinToString(", ")

        val systemInstructionText = """
            You are a Sonic Sage AI music interpreter. You translate natural language user queries into a structured playlist intent JSON object.
            You must analyze the user prompt and extract seeds, genres, and styles.
            
            IMPORTANT:
            - Never invent track titles from memory.
            - Only reference the provided lists of available artists and albums if possible.
            - Ensure output strictly matches the PlaylistIntent JSON schema.
            - Do not return any markdown formatting, backticks, or other text. Only valid raw JSON.
            
            AVAILABLE ARTISTS SAMPLE: $availableArtists
            AVAILABLE ALBUMS SAMPLE: $availableAlbums
            
            JSON Schema:
            {
              "seedArtists": ["ArtistName"],
              "seedAlbums": ["AlbumName"],
              "seedTracks": [],
              "genres": ["metalcore", "soundtrack"],
              "moods": ["dark", "energetic"],
              "energyLevel": "high",
              "decadeRange": "2000s",
              "includeCollections": true,
              "excludeGenres": [],
              "excludeArtists": [],
              "preferFamiliar": true,
              "preferDeepCuts": false,
              "targetTrackCount": 30,
              "sortStrategy": "high_energy",
              "explanation": "Brief explanation of interpretation"
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            )
        )

        try {
            val response = GeminiClient.service.generateContent(geminiApiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Log.d("CloudAIProvider", "Raw JSON from Gemini: $jsonText")

            val json = Json { 
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
            json.decodeFromString<PlaylistIntent>(jsonText)
        } catch (e: Exception) {
            Log.e("CloudAIProvider", "Error parsing Gemini response, falling back to Local parser", e)
            // Fallback to local parser on parse error or network error
            LocalAIProvider().generatePlaylistIntent(prompt, cachedTracks)
        }
    }
}

class LocalAIProvider : AIProvider {
    override val name = "Local AI (On-Device Parser)"

    // -------------------------------------------------------------------------------------
    // NOTE: ON-DEVICE LLM INTEGRATION ARCHITECTURE COMMENTS
    // -------------------------------------------------------------------------------------
    // To implement a fully local offline LLM model later:
    // 1. MediaPipe LLM Inference API:
    //    Add 'com.google.mediapipe:tasks-genai:0.10.14' to dependencies.
    //    Load a quantized Gemma 2B or Llama 3 8B (int4) `.bin` or `.task` file from assets or external storage.
    //    Use LlmInference.createFromOptions(context, options) and call inference.generateResponse(prompt).
    // 2. llama.cpp / ONNX Runtime Mobile:
    //    Package native shared libraries (.so files) via CMake or JNI.
    //    Expose native method: external fun nativeGenerate(prompt: String): String
    //    Load standard GGUF model files.
    // 3. MLC LLM:
    //    Use mlc-app framework to host local LLM engines in WebGPU / OpenCL Vulkan pipelines.
    // -------------------------------------------------------------------------------------

    override suspend fun generatePlaylistIntent(prompt: String, cachedTracks: List<CachedTrack>): PlaylistIntent = withContext(Dispatchers.Default) {
        Log.d("LocalAIProvider", "Using fast, private Local Rules-based engine for prompt: $prompt")
        
        val lowercasePrompt = prompt.lowercase(Locale.ROOT)
        
        // Simple regex/keyword matching for seeds
        val matchedArtists = mutableListOf<String>()
        val matchedAlbums = mutableListOf<String>()
        val matchedGenres = mutableListOf<String>()
        val matchedMoods = mutableListOf<String>()
        var energy = "medium"
        var decade: String? = null

        // Scan library artists & albums to see if they are explicitly mentioned
        cachedTracks.forEach { track ->
            val art = track.artist
            if (lowercasePrompt.contains(art.lowercase(Locale.ROOT)) && !matchedArtists.contains(art)) {
                matchedArtists.add(art)
            }
            val alb = track.album
            if (lowercasePrompt.contains(alb.lowercase(Locale.ROOT)) && !matchedAlbums.contains(alb)) {
                matchedAlbums.add(alb)
            }
        }

        // Genre matcher
        val genreKeywords = listOf("rock", "metal", "metalcore", "pop", "jazz", "classical", "rap", "hip hop", "soundtrack", "anime", "electronic", "dance", "ambient")
        for (genre in genreKeywords) {
            if (lowercasePrompt.contains(genre)) {
                matchedGenres.add(genre)
            }
        }

        // Mood / Energy matcher
        if (lowercasePrompt.contains("workout") || lowercasePrompt.contains("energetic") || lowercasePrompt.contains("fast") || lowercasePrompt.contains("intense") || lowercasePrompt.contains("heavy")) {
            energy = "high"
            matchedMoods.add("intense")
            matchedMoods.add("energetic")
        } else if (lowercasePrompt.contains("relax") || lowercasePrompt.contains("chill") || lowercasePrompt.contains("calm") || lowercasePrompt.contains("sleep") || lowercasePrompt.contains("ambient")) {
            energy = "low"
            matchedMoods.add("calm")
            matchedMoods.add("peaceful")
        }

        if (lowercasePrompt.contains("dark") || lowercasePrompt.contains("sad") || lowercasePrompt.contains("melancholic")) {
            matchedMoods.add("dark")
        }

        // Decade matcher
        val decadeRegex = "(\\d{4})s|(\\d{4})".toRegex()
        val matchResult = decadeRegex.find(lowercasePrompt)
        if (matchResult != null) {
            val valStr = matchResult.value
            decade = if (valStr.endsWith("s")) valStr else "${valStr.take(3)}0s"
        }

        PlaylistIntent(
            seedArtists = matchedArtists,
            seedAlbums = matchedAlbums,
            genres = matchedGenres,
            moods = matchedMoods,
            energyLevel = energy,
            decadeRange = decade,
            explanation = "Local intent parsing extracted ${matchedArtists.size} artists, ${matchedGenres.size} genres, and ${matchedMoods.size} moods dynamically.",
            targetTrackCount = 25
        )
    }
}

class NoAIProvider : AIProvider {
    override val name = "No AI (Pure Algorithms)"

    override suspend fun generatePlaylistIntent(prompt: String, cachedTracks: List<CachedTrack>): PlaylistIntent = withContext(Dispatchers.Default) {
        Log.d("NoAIProvider", "No AI fallback activated. Converting to generic random mix of library tracks.")
        PlaylistIntent(
            explanation = "Pure local algorithmic fallback selection.",
            targetTrackCount = 20,
            preferFamiliar = true
        )
    }
}

// ==============================================================
// 3. Robust RecommendationEngine Scoring and Pipeline
// ==============================================================
class RecommendationEngine(private val context: Context) {
    private val database = MusicDatabase.getDatabase(context)
    private val musicDao = database.musicDao()

    suspend fun buildRecommendationQueue(intent: PlaylistIntent, cachedTracks: List<CachedTrack>): List<TrackItem> = withContext(Dispatchers.Default) {
        val recentlyPlayed = try {
            musicDao.getRecentlyPlayed().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val trackScores = mutableListOf<Pair<CachedTrack, Int>>()

        for (track in cachedTracks) {
            var score = 0
            val trackArtistLower = track.artist.lowercase(Locale.ROOT)
            val trackAlbumLower = track.album.lowercase(Locale.ROOT)
            val trackTitleLower = track.title.lowercase(Locale.ROOT)

            // Exclude rules (negative constraints have priority!)
            var excluded = false
            for (exArtist in intent.excludeArtists) {
                if (trackArtistLower.contains(exArtist.lowercase(Locale.ROOT))) {
                    excluded = true
                    break
                }
            }
            if (excluded) continue

            // 1. Seed Artists (+100)
            for (artist in intent.seedArtists) {
                if (trackArtistLower.contains(artist.lowercase(Locale.ROOT))) {
                    score += 100
                }
            }

            // 2. Seed Albums (+100)
            for (album in intent.seedAlbums) {
                if (trackAlbumLower.contains(album.lowercase(Locale.ROOT))) {
                    score += 100
                }
            }

            // 3. Genre matching (+40 per match)
            for (genre in intent.genres) {
                val genLower = genre.lowercase(Locale.ROOT)
                if (trackArtistLower.contains(genLower) || trackAlbumLower.contains(genLower) || trackTitleLower.contains(genLower)) {
                    score += 40
                }
            }

            // 4. Mood matching (+30 per match)
            for (mood in intent.moods) {
                val moodLower = mood.lowercase(Locale.ROOT)
                if (trackTitleLower.contains(moodLower) || trackAlbumLower.contains(moodLower) || trackArtistLower.contains(moodLower)) {
                    score += 30
                }
            }

            // 5. Energy alignment (+30)
            if (intent.energyLevel == "high") {
                val highEnergyKeywords = listOf("rock", "metal", "intense", "loud", "heavy", "workout", "action", "epic", "battle", "drum", "bass")
                if (highEnergyKeywords.any { trackTitleLower.contains(it) || trackAlbumLower.contains(it) }) {
                    score += 30
                }
            } else if (intent.energyLevel == "low") {
                val lowEnergyKeywords = listOf("relax", "chill", "calm", "sleep", "acoustic", "piano", "ambient", "quiet", "soft", "slow", "peace")
                if (lowEnergyKeywords.any { trackTitleLower.contains(it) || trackAlbumLower.contains(it) }) {
                    score += 30
                }
            }

            // 6. Familiarity vs. Deep Cuts
            val isFamiliar = recentlyPlayed.any { it.ratingKey == track.ratingKey }
            if (intent.preferFamiliar && isFamiliar) {
                score += 25
            } else if (intent.preferDeepCuts && !isFamiliar) {
                score += 30
            }

            // If prompt matched anything or we have no custom filters, add to candidates
            if (score > 0 || (intent.seedArtists.isEmpty() && intent.genres.isEmpty() && intent.moods.isEmpty())) {
                trackScores.add(track to score)
            }
        }

        // Sort candidates by score descending
        val sortedCandidates = trackScores.sortedByDescending { it.second }.map { it.first }

        // Remove duplicates & cap artist tracks (max 3 tracks per artist to keep the mix varied!)
        val artistCounters = mutableMapOf<String, Int>()
        val finalSelection = mutableListOf<CachedTrack>()

        for (track in sortedCandidates) {
            val count = artistCounters.getOrDefault(track.artist, 0)
            val isSeededArtist = intent.seedArtists.any { it.lowercase(Locale.ROOT) == track.artist.lowercase(Locale.ROOT) }
            
            // Allow more tracks if the user explicitly seeded this artist
            val maxLimit = if (isSeededArtist) 8 else 3

            if (count < maxLimit) {
                finalSelection.add(track)
                artistCounters[track.artist] = count + 1
            }

            if (finalSelection.size >= intent.targetTrackCount) {
                break
            }
        }

        // If selection is too small, fill it up with random tracks to meet the user's requested count
        if (finalSelection.size < intent.targetTrackCount && cachedTracks.isNotEmpty()) {
            val extraTracks = cachedTracks.shuffled()
            for (track in extraTracks) {
                if (!finalSelection.contains(track)) {
                    finalSelection.add(track)
                }
                if (finalSelection.size >= intent.targetTrackCount) {
                    break
                }
            }
        }

        finalSelection.map { track ->
            TrackItem(
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.artist,
                album = track.album,
                key = track.key,
                thumb = track.thumb,
                duration = track.duration
            )
        }
    }
}

