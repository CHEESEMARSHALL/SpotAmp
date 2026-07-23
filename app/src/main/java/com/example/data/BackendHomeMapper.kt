package com.example.data

import com.example.playback.TrackItem

/** Maps SpotCore's transport DTOs into the track type already used by ExoPlayer and Compose. */
object BackendHomeMapper {
    fun track(dto: BackendTrackDto): TrackItem = TrackItem(
        ratingKey = "companion:${dto.id}",
        title = dto.title,
        artist = dto.artist,
        album = dto.album,
        key = dto.streamUrl,
        thumb = dto.coverUrl,
        duration = dto.duration,
        genres = dto.genre?.let { listOf(it) } ?: emptyList()
    )
}
