package com.example.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.MusicDao
import com.example.data.PlexSettingsManager
import com.example.data.RecentTrack
import com.example.data.LastFmScrobbler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrackItem(
    val ratingKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val key: String, // e.g. "/library/parts/123/..."
    val thumb: String, // e.g. "/library/metadata/456/thumb/..."
    val duration: Long
)

class PlaybackManager private constructor(private val appContext: Context) {
    private var exoPlayer: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _currentTrack = MutableStateFlow<TrackItem?>(null)
    val currentTrack: StateFlow<TrackItem?> = _currentTrack.asStateFlow()

    private val _queue = MutableStateFlow<List<TrackItem>>(emptyList())
    val queue: StateFlow<List<TrackItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow<Int>(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow<Boolean>(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _progress = MutableStateFlow<Long>(0L)
    val progress: StateFlow<Long> = _progress.asStateFlow()

    private val _duration = MutableStateFlow<Long>(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow<Boolean>(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow<Int>(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // Database helper to insert into recently played history
    private var musicDao: MusicDao? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Base URL & Token reference (populated from Settings)
    var baseUrl: String = ""
        private set
    var token: String = ""
        private set

    // Last.fm Scrobbling support
    private var hasScrobbledCurrent = false

    // Equalizer & Audio processing
    private var equalizer: android.media.audiofx.Equalizer? = null
    private var dynamicsProcessing: android.media.audiofx.DynamicsProcessing? = null

    companion object {
        @Volatile
        private var INSTANCE: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PlaybackManager(context)
                INSTANCE = instance
                instance
            }
        }
    }

    init {
        mainHandler.post {
            initializePlayer()
        }
    }

    fun setCredentials(baseUrl: String, token: String) {
        this.baseUrl = baseUrl
        this.token = token
    }

    fun setMusicDao(dao: MusicDao) {
        this.musicDao = dao
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return

        exoPlayer = ExoPlayer.Builder(appContext).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) {
                        startProgressUpdates()
                        // Start Foreground Service to prevent background suspend and show controls
                        try {
                            val serviceIntent = android.content.Intent(appContext, MusicPlaybackService::class.java).apply {
                                action = MusicPlaybackService.ACTION_START
                            }
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                appContext.startForegroundService(serviceIntent)
                            } else {
                                appContext.startService(serviceIntent)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        stopProgressUpdates()
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    _isLoading.value = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) {
                        _duration.value = duration
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    _progress.value = newPosition.positionMs
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mainHandler.post {
                        mediaItem?.let { item ->
                            val ratingKey = item.mediaId
                            val track = _queue.value.find { it.ratingKey == ratingKey }
                            if (track != null) {
                                _currentTrack.value = track
                                val idx = _queue.value.indexOfFirst { it.ratingKey == ratingKey }
                                if (idx != -1) {
                                    _currentIndex.value = idx
                                }
                                _duration.value = track.duration
                                saveToRecentlyPlayed(track)
                                
                                // Reset Last.fm triggers and Notify Now Playing
                                hasScrobbledCurrent = false
                                val settings = PlexSettingsManager(appContext)
                                LastFmScrobbler.updateNowPlaying(appContext, settings, track)
                            }
                        }
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    applyAudioEffects()
                }
            })
        }
    }

    private fun saveToRecentlyPlayed(track: TrackItem) {
        coroutineScope.launch {
            val dao = musicDao ?: return@launch
            dao.deleteRecentTrackByKey(track.ratingKey)
            dao.insertRecentTrack(
                RecentTrack(
                    ratingKey = track.ratingKey,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    key = track.key,
                    thumb = track.thumb
                )
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun updateExoPlayerQueue(queueList: List<TrackItem>, startIndex: Int) {
        if (baseUrl.isEmpty() || token.isEmpty()) return
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

        mainHandler.post {
            exoPlayer?.let { player ->
                player.stop()
                player.clearMediaItems()
                val mediaItems = queueList.map { track ->
                    val streamUrl = "$normalizedBaseUrl${track.key}?X-Plex-Token=$token"
                    MediaItem.Builder()
                        .setUri(streamUrl)
                        .setMediaId(track.ratingKey)
                        .build()
                }
                player.addMediaItems(mediaItems)
                if (startIndex in mediaItems.indices) {
                    player.seekTo(startIndex, 0L)
                }
                player.prepare()
                player.playWhenReady = true
                _isPlaying.value = true
                applyAudioEffects()
            }
        }
    }

    fun playTrack(track: TrackItem, queueList: List<TrackItem>) {
        _queue.value = queueList
        val index = queueList.indexOfFirst { it.ratingKey == track.ratingKey }
        _currentIndex.value = if (index != -1) index else 0
        _currentTrack.value = track
        updateExoPlayerQueue(queueList, if (index != -1) index else 0)
    }

    fun playQueue(queueList: List<TrackItem>, startIndex: Int = 0) {
        if (queueList.isEmpty()) return
        _queue.value = queueList
        val index = if (startIndex in queueList.indices) startIndex else 0
        _currentIndex.value = index
        _currentTrack.value = queueList[index]
        updateExoPlayerQueue(queueList, index)
    }

    fun playNext(track: TrackItem) {
        val currentQueue = _queue.value.toMutableList()
        val currentIndexVal = _currentIndex.value

        val existingIndex = currentQueue.indexOfFirst { it.ratingKey == track.ratingKey }
        if (existingIndex != -1) {
            currentQueue.removeAt(existingIndex)
            mainHandler.post {
                exoPlayer?.removeMediaItem(existingIndex)
            }
        }

        val insertIndex = if (currentIndexVal == -1) 0 else currentIndexVal + 1
        currentQueue.add(insertIndex, track)
        _queue.value = currentQueue

        mainHandler.post {
            if (baseUrl.isEmpty() || token.isEmpty()) return@post
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            val streamUrl = "$normalizedBaseUrl${track.key}?X-Plex-Token=$token"
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(track.ratingKey)
                .build()
            exoPlayer?.addMediaItem(insertIndex, mediaItem)
            if (currentIndexVal == -1) {
                _currentIndex.value = 0
                _currentTrack.value = track
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }
        }
    }

    fun playTracksNext(tracks: List<TrackItem>) {
        val currentQueue = _queue.value.toMutableList()
        val currentIndexVal = _currentIndex.value

        val insertIndex = if (currentIndexVal == -1) 0 else currentIndexVal + 1
        currentQueue.addAll(insertIndex, tracks)
        _queue.value = currentQueue

        mainHandler.post {
            if (baseUrl.isEmpty() || token.isEmpty()) return@post
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            val mediaItems = tracks.map { track ->
                val streamUrl = "$normalizedBaseUrl${track.key}?X-Plex-Token=$token"
                MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaId(track.ratingKey)
                    .build()
            }
            exoPlayer?.addMediaItems(insertIndex, mediaItems)
            if (currentIndexVal == -1 && tracks.isNotEmpty()) {
                _currentIndex.value = 0
                _currentTrack.value = tracks.first()
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }
        }
    }

    fun addToQueue(track: TrackItem) {
        val currentQueue = _queue.value.toMutableList()
        if (currentQueue.any { it.ratingKey == track.ratingKey }) return
        currentQueue.add(track)
        _queue.value = currentQueue

        mainHandler.post {
            if (baseUrl.isEmpty() || token.isEmpty()) return@post
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            val streamUrl = "$normalizedBaseUrl${track.key}?X-Plex-Token=$token"
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(track.ratingKey)
                .build()
            exoPlayer?.addMediaItem(mediaItem)
            if (_currentIndex.value == -1) {
                _currentIndex.value = 0
                _currentTrack.value = track
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }
        }
    }

    fun addTracksToQueue(tracks: List<TrackItem>) {
        val currentQueue = _queue.value.toMutableList()
        currentQueue.addAll(tracks)
        _queue.value = currentQueue

        mainHandler.post {
            if (baseUrl.isEmpty() || token.isEmpty()) return@post
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
            val mediaItems = tracks.map { track ->
                val streamUrl = "$normalizedBaseUrl${track.key}?X-Plex-Token=$token"
                MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaId(track.ratingKey)
                    .build()
            }
            exoPlayer?.addMediaItems(mediaItems)
            if (_currentIndex.value == -1 && tracks.isNotEmpty()) {
                _currentIndex.value = 0
                _currentTrack.value = tracks.first()
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            }
        }
    }

    fun play() {
        mainHandler.post {
            exoPlayer?.play()
        }
    }

    fun pause() {
        mainHandler.post {
            exoPlayer?.pause()
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun next() {
        mainHandler.post {
            exoPlayer?.let { player ->
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && _queue.value.isNotEmpty()) {
                    player.seekTo(0, 0L)
                }
            }
        }
    }

    fun prev() {
        mainHandler.post {
            exoPlayer?.let { player ->
                if (player.currentPosition > 3000) {
                    player.seekTo(0L)
                } else if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && _queue.value.isNotEmpty()) {
                    player.seekTo(_queue.value.size - 1, 0L)
                } else {
                    player.seekTo(0L)
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mainHandler.post {
            exoPlayer?.seekTo(positionMs)
            _progress.value = positionMs
        }
    }

    fun toggleShuffle() {
        val newValue = !_shuffleModeEnabled.value
        _shuffleModeEnabled.value = newValue
        mainHandler.post {
            exoPlayer?.shuffleModeEnabled = newValue
        }
    }

    fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        mainHandler.post {
            exoPlayer?.repeatMode = nextMode
        }
    }

    private fun checkScrobbleProgress(progressMs: Long, durationMs: Long, track: TrackItem) {
        if (durationMs <= 0 || hasScrobbledCurrent) return
        val settings = PlexSettingsManager(appContext)
        if (!settings.lastFmEnabled) return

        val halfDuration = durationMs / 2
        val maxScrobbleLimit = 240000L // 4 minutes
        val targetMs = if (halfDuration < maxScrobbleLimit) halfDuration else maxScrobbleLimit

        if (progressMs >= targetMs) {
            hasScrobbledCurrent = true
            LastFmScrobbler.scrobble(appContext, settings, track)
        }
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    val pos = player.currentPosition
                    _progress.value = pos
                    _currentTrack.value?.let { track ->
                        checkScrobbleProgress(pos, player.duration, track)
                    }
                }
            }
            mainHandler.postDelayed(this, 250)
        }
    }

    private fun startProgressUpdates() {
        mainHandler.removeCallbacks(progressRunnable)
        mainHandler.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        mainHandler.removeCallbacks(progressRunnable)
    }

    fun refreshAudioSettings() {
        mainHandler.post {
            applyAudioEffects()
        }
    }

    fun applyAudioEffects() {
        val sessionId = exoPlayer?.audioSessionId ?: return
        if (sessionId == android.media.C.AUDIO_SESSION_ID_UNSPECIFIED) return
        val settings = PlexSettingsManager(appContext)

        // Equalizer Setup
        if (settings.equalizerEnabled) {
            try {
                if (equalizer == null || equalizer?.id != sessionId) {
                    equalizer?.release()
                    equalizer = android.media.audiofx.Equalizer(0, sessionId).apply {
                        enabled = true
                    }
                }
                val eq = equalizer
                if (eq != null) {
                    val preset = settings.equalizerPreset
                    val numPresets = eq.numberOfPresets
                    var found = false
                    for (i in 0 until numPresets) {
                        if (eq.getPresetName(i.toShort()).equals(preset, ignoreCase = true)) {
                            eq.usePreset(i.toShort())
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        val numBands = eq.numberOfBands
                        val range = eq.bandLevelRange
                        val maxLevel = range[1]
                        val midLevel = 0.toShort()
                        when (preset.lowercase()) {
                            "bass boost" -> {
                                if (numBands > 0) eq.setBandLevel(0, (maxLevel * 0.7).toInt().toShort())
                                if (numBands > 1) eq.setBandLevel(1, (maxLevel * 0.5).toInt().toShort())
                            }
                            "vocal boost" -> {
                                if (numBands > 2) eq.setBandLevel(2, (maxLevel * 0.6).toInt().toShort())
                                if (numBands > 3) eq.setBandLevel(3, (maxLevel * 0.4).toInt().toShort())
                            }
                            else -> {
                                for (b in 0 until numBands) eq.setBandLevel(b.toShort(), midLevel)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                equalizer?.enabled = false
                equalizer?.release()
                equalizer = null
            } catch (e: Exception) { e.printStackTrace() }
        }

        // Volume Normalization Setup
        if (settings.normalizationEnabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    if (dynamicsProcessing == null) {
                        val builder = android.media.audiofx.DynamicsProcessing.Config.Builder(
                            android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                            1,
                            true, 1,
                            false, 0,
                            false, 0,
                            false, 0
                        )
                        dynamicsProcessing = android.media.audiofx.DynamicsProcessing(0, sessionId, builder.build())
                    }
                    dynamicsProcessing?.let { dp ->
                        dp.enabled = true
                        val limiter = dp.getLimiterByChannelIndex(0)
                        limiter.isEnabled = true
                        limiter.linkGroup = 0
                        limiter.attackTime = 1.0f
                        limiter.releaseTime = 100.0f
                        limiter.ratio = 10.0f
                        limiter.threshold = -2.0f
                        limiter.postGain = 2.0f
                        dp.setLimiterByChannelIndex(0, limiter)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            try {
                dynamicsProcessing?.enabled = false
                dynamicsProcessing?.release()
                dynamicsProcessing = null
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun release() {
        mainHandler.post {
            stopProgressUpdates()
            equalizer?.release()
            dynamicsProcessing?.release()
            exoPlayer?.release()
            exoPlayer = null
        }
        coroutineScope.cancel()
    }
}
