package com.example.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.playback.TrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

object LastFmScrobbler {
    private const val TAG = "LastFmScrobbler"
    private const val API_KEY = "0b58e7b99c7fde6ee928c04bc493c06d" // Pre-configured dev API key
    private const val API_SECRET = "8f307373f1d939e6a98fba0e633ca5e0"
    private const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun updateNowPlaying(context: Context, settings: PlexSettingsManager, track: TrackItem) {
        if (!settings.lastFmEnabled || settings.lastFmUsername.isEmpty()) return

        scope.launch {
            Log.d(TAG, "Updating Now Playing on Last.fm for track: ${track.title} by ${track.artist}")
            val sessionKey = settings.lastFmSessionKey
            if (sessionKey.isEmpty()) {
                showToast(context, "Last.fm (Demo): Now playing '${track.title}'")
                return@launch
            }

            try {
                val params = mutableMapOf(
                    "method" to "track.updateNowPlaying",
                    "artist" to track.artist,
                    "track" to track.title,
                    "album" to track.album,
                    "api_key" to API_KEY,
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
                    val respStr = response.body?.string() ?: ""
                    Log.d(TAG, "UpdateNowPlaying response: $respStr")
                    if (response.isSuccessful) {
                        showToast(context, "Last.fm: Updated Now Playing")
                    } else {
                        Log.e(TAG, "Failed updateNowPlaying: $respStr")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating now playing", e)
            }
        }
    }

    fun scrobble(context: Context, settings: PlexSettingsManager, track: TrackItem) {
        if (!settings.lastFmEnabled || settings.lastFmUsername.isEmpty()) return

        scope.launch {
            Log.d(TAG, "Scrobbling to Last.fm: ${track.title} by ${track.artist}")
            val sessionKey = settings.lastFmSessionKey
            if (sessionKey.isEmpty()) {
                showToast(context, "Last.fm (Demo): Scrobbling '${track.title}'")
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
                    "api_key" to API_KEY,
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
                    val respStr = response.body?.string() ?: ""
                    Log.d(TAG, "Scrobble response: $respStr")
                    if (response.isSuccessful) {
                        showToast(context, "Last.fm scrobble successful!")
                    } else {
                        Log.e(TAG, "Failed scrobble: $respStr")
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
        signatureBuilder.append(API_SECRET)
        return md5(signatureBuilder.toString())
    }

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
