package com.example.playback

import com.example.data.PlaybackStateEntity
import java.io.File
import kotlinx.serialization.json.Json

data class RestoredPlaybackState(
    val queue: List<TrackItem>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int
)

object PlaybackStateRestoration {
    fun restore(state: PlaybackStateEntity, fileExists: (String) -> Boolean = { File(it).isFile }): RestoredPlaybackState? {
        return runCatching {
            val queue = Json.decodeFromString<List<TrackItem>>(state.queueJson)
                .filter { it.localPath == null || fileExists(it.localPath) }
            if (queue.isEmpty()) return null
            RestoredPlaybackState(
                queue = queue,
                currentIndex = state.currentIndex.coerceIn(0, queue.lastIndex),
                positionMs = state.positionMs.coerceAtLeast(0L),
                shuffleEnabled = state.shuffleEnabled,
                repeatMode = state.repeatMode
            )
        }.getOrNull()
    }
}
