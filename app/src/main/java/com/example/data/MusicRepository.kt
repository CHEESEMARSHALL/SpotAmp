package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.playback.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(context: Context) {
    private val musicDao = MusicDatabase.getDatabase(context).musicDao()
    val settings = PlexSettingsManager(context)

    // Flow for recently played tracks
    val recentlyPlayed: Flow<List<RecentTrack>> = musicDao.getRecentlyPlayed()

    // Flow for cached tracks count (to see if indexed)
    val cachedTracksCount: Flow<List<CachedTrack>> = musicDao.getAllCachedTracks()

    suspend fun getCachedCount(): Int = withContext(Dispatchers.IO) {
        musicDao.getCachedTracksCount()
    }

    suspend fun getCachedTracksList(): List<CachedTrack> = withContext(Dispatchers.IO) {
        musicDao.getCachedTracksList()
    }

    // Playlists
    val playlists: Flow<List<PlaylistEntity>> = musicDao.getAllPlaylists()

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        musicDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlistId: Int) = withContext(Dispatchers.IO) {
        musicDao.deletePlaylist(playlistId)
        musicDao.clearPlaylistTracks(playlistId)
    }

    fun getPlaylistTracks(playlistId: Int): Flow<List<PlaylistTrackEntity>> {
        return musicDao.getTracksForPlaylist(playlistId)
    }

    suspend fun getPlaylistTracksList(playlistId: Int): List<PlaylistTrackEntity> = withContext(Dispatchers.IO) {
        musicDao.getTracksForPlaylistList(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Int, track: TrackItem) = withContext(Dispatchers.IO) {
        musicDao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.artist,
                album = track.album,
                key = track.key,
                thumb = track.thumb,
                duration = track.duration
            )
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Int, ratingKey: String) = withContext(Dispatchers.IO) {
        musicDao.removeTrackFromPlaylist(playlistId, ratingKey)
    }

    // Downloads
    val downloadedTracks: Flow<List<DownloadedTrackEntity>> = musicDao.getAllDownloadedTracks()

    suspend fun isTrackDownloaded(ratingKey: String): Boolean = withContext(Dispatchers.IO) {
        musicDao.isTrackDownloaded(ratingKey)
    }

    // Liked Songs
    val likedTracks: Flow<List<LikedTrackEntity>> = musicDao.getAllLikedTracks()

    suspend fun isTrackLiked(ratingKey: String): Boolean = withContext(Dispatchers.IO) {
        musicDao.isTrackLiked(ratingKey)
    }

    fun isTrackLikedFlow(ratingKey: String): Flow<Boolean> {
        return musicDao.isTrackLikedFlow(ratingKey)
    }

    suspend fun addLikedTrack(track: TrackItem) = withContext(Dispatchers.IO) {
        musicDao.insertLikedTrack(
            LikedTrackEntity(
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.artist,
                album = track.album,
                key = track.key,
                thumb = track.thumb,
                duration = track.duration
            )
        )
    }

    suspend fun deleteLikedTrack(ratingKey: String) = withContext(Dispatchers.IO) {
        musicDao.deleteLikedTrack(ratingKey)
    }

    suspend fun addDownloadedTrack(track: TrackItem, fileSize: Long) = withContext(Dispatchers.IO) {
        musicDao.insertDownloadedTrack(
            DownloadedTrackEntity(
                ratingKey = track.ratingKey,
                title = track.title,
                artist = track.artist,
                album = track.album,
                key = track.key,
                thumb = track.thumb,
                duration = track.duration,
                fileSize = fileSize
            )
        )
    }

    suspend fun deleteDownloadedTrack(ratingKey: String) = withContext(Dispatchers.IO) {
        musicDao.deleteDownloadedTrack(ratingKey)
    }

    private fun getService(): PlexApiService {
        if (!settings.isConfigured) {
            throw IllegalStateException("Plex server URL and token are not configured.")
        }
        return PlexClientManager.getApiService(settings.baseUrl)
    }

    suspend fun getLibraries(): List<PlexDirectory> = withContext(Dispatchers.IO) {
        try {
            val response = getService().getLibraries(settings.token)
            response.mediaContainer.directory?.filter { it.type == "artist" } ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching libraries", e)
            emptyList()
        }
    }

    suspend fun getArtists(sectionId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        try {
            val response = getService().getLibraryItems(sectionId, "8", settings.token)
            response.mediaContainer.metadata ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching artists", e)
            emptyList()
        }
    }

    suspend fun getAlbums(sectionId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        try {
            val response = getService().getLibraryItems(sectionId, "9", settings.token)
            response.mediaContainer.metadata ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching albums", e)
            emptyList()
        }
    }

    suspend fun getRecentlyAddedAlbums(sectionId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        try {
            val response = getService().getRecentlyAddedAlbums(sectionId, "9", settings.token)
            response.mediaContainer.metadata ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching recently added", e)
            emptyList()
        }
    }

    suspend fun getMetadataDetail(ratingKey: String): PlexMetadata? = withContext(Dispatchers.IO) {
        try {
            val response = getService().getMetadataDetail(ratingKey, settings.token)
            response.mediaContainer.metadata?.firstOrNull()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching metadata detail", e)
            null
        }
    }

    suspend fun getArtistAlbums(artistId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        try {
            val response = getService().getChildren(artistId, settings.token)
            response.mediaContainer.metadata ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching artist albums", e)
            emptyList()
        }
    }

    suspend fun getAlbumTracks(albumId: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        try {
            val response = getService().getChildren(albumId, settings.token)
            response.mediaContainer.metadata ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error fetching album tracks", e)
            emptyList()
        }
    }

    suspend fun searchPlex(query: String): List<PlexMetadata> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val response = getService().globalSearch(query, settings.token)
            response.mediaContainer.metadata?.filter { 
                it.type == "artist" || it.type == "album" || it.type == "track" 
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error searching Plex", e)
            emptyList()
        }
    }

    suspend fun syncTrackCache(sectionId: String) = withContext(Dispatchers.IO) {
        try {
            val response = getService().getLibraryItems(sectionId, "10", settings.token)
            val tracks = response.mediaContainer.metadata ?: emptyList()
            
            val entities = tracks.map { track ->
                val trackKey = track.media?.firstOrNull()?.part?.firstOrNull()?.key ?: ""
                CachedTrack(
                    ratingKey = track.ratingKey,
                    title = track.title,
                    artist = track.grandparentTitle ?: track.parentTitle ?: "Unknown Artist",
                    album = track.parentTitle ?: "Unknown Album",
                    key = trackKey,
                    thumb = track.thumb ?: "",
                    duration = track.duration ?: 0L
                )
            }.filter { it.key.isNotEmpty() }

            if (entities.isNotEmpty()) {
                musicDao.clearCachedTracks()
                musicDao.insertCachedTracks(entities)
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error syncing track cache", e)
            throw e
        }
    }

    suspend fun generateAiPlaylist(prompt: String): List<TrackItem> = withContext(Dispatchers.IO) {
        val cachedTracks = musicDao.getCachedTracksList()
        if (cachedTracks.isEmpty()) {
            throw IllegalStateException("Your music library has not been indexed yet. Please go to Settings to index your music library.")
        }

        // Diversified sampling to respect token limit. Max 200 tracks.
        val sampledTracks = if (cachedTracks.size <= 200) {
            cachedTracks
        } else {
            // Select 200 tracks randomly distributed across the library
            cachedTracks.shuffled().take(200)
        }

        // Build list for the model prompt
        val tracksText = sampledTracks.joinToString("\n") { 
            "[${it.ratingKey}] ${it.artist} - ${it.title} (${it.album})"
        }

        val systemInstructionText = """
            You are an expert music playlist curator for a self-hosted audio streaming application.
            Your task is to review the list of available tracks provided by the user, and select between 8 and 15 tracks that match their thematic, genre, or mood request.
            
            CRITICAL DIRECTIVES:
            1. You MUST ONLY select tracks from the provided list. Do NOT invent or hallucinate songs.
            2. Return ONLY a plain JSON array of strings containing the selected track ratingKeys.
            3. Do not include any explanation, intro/outro, conversational filler, markdown formatting, or triple backticks.
            4. Example correct response: ["101", "204", "150"]
        """.trimIndent()

        val userPrompt = """
            User Request: "$prompt"
            
            Available Tracks in Library:
            $tracksText
        """.trimIndent()

        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        if (geminiApiKey.isEmpty() || geminiApiKey.contains("GEMINI_API_KEY")) {
            throw IllegalStateException("Gemini API key is not configured in the Secrets panel.")
        }

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.5f
            )
        )

        try {
            val response = GeminiClient.service.generateContent(geminiApiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Log.d("MusicRepository", "Gemini response: $jsonText")

            // Parse response: extract ratingKeys
            val ratingKeys = parseRatingKeysFromJson(jsonText)
            
            // Map keys back to original entities, maintaining the order of Gemini's selection
            val selectedTracks = ratingKeys.mapNotNull { key ->
                cachedTracks.find { it.ratingKey == key }
            }

            selectedTracks.map { 
                TrackItem(
                    ratingKey = it.ratingKey,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    key = it.key,
                    thumb = it.thumb,
                    duration = it.duration
                )
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error generating AI playlist", e)
            throw e
        }
    }

    private fun parseRatingKeysFromJson(jsonText: String): List<String> {
        val clean = jsonText.trim()
            .removeSurrounding("```json", "```")
            .removeSurrounding("```", "```")
            .trim()
            .removeSurrounding("[", "]")

        if (clean.isEmpty()) return emptyList()

        return clean.split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            .filter { it.isNotEmpty() }
    }
}
