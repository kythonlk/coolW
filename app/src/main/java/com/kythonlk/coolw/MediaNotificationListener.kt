package com.kythonlk.coolw

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.content.edit
import android.graphics.Bitmap
import java.io.File

class MediaNotificationListener : NotificationListenerService(), MediaSessionManager.OnActiveSessionsChangedListener {

    private var mediaSessionManager: MediaSessionManager? = null
    private val controllerCallbacks = HashMap<MediaController, MediaController.Callback>()

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val componentName = ComponentName(this, MediaNotificationListener::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(this, componentName)
            updateFromActiveSessions()
        } catch (e: Exception) {
            Log.e("MediaListener", "Failed to query active sessions", e)
        }
    }

    override fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        try {
            updateFromActiveSessions()
        } catch (e: Exception) {
            Log.e("MediaListener", "Failed to update sessions", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(this)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun updateFromActiveSessions() {
        val manager = mediaSessionManager ?: return
        val componentName = ComponentName(this, MediaNotificationListener::class.java)
        
        try {
            val controllers = manager.getActiveSessions(componentName)
            
            // Clean up old callbacks
            for ((controller, callback) in controllerCallbacks) {
                try {
                    controller.unregisterCallback(callback)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            controllerCallbacks.clear()

            val primaryController = controllers.firstOrNull()
            if (primaryController != null) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        super.onMetadataChanged(metadata)
                        saveMetadata(metadata)
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        super.onPlaybackStateChanged(state)
                        savePlaybackState(state)
                    }
                }
                
                primaryController.registerCallback(callback)
                controllerCallbacks[primaryController] = callback
                
                saveMetadata(primaryController.metadata)
                savePlaybackState(primaryController.playbackState)
            }
        } catch (e: SecurityException) {
            Log.e("MediaListener", "No permission to access active sessions", e)
        }
    }

    private fun saveMetadata(metadata: MediaMetadata?) {
        if (metadata == null) return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Nothing Track"
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Nothing OS"
        
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)

        var hasArtwork = false
        if (art != null) {
            try {
                // Scale down artwork to save memory and ensure widget can render it
                val scaledArt = if (art.width > 512 || art.height > 512) {
                    val ratio = art.width.toFloat() / art.height.toFloat()
                    val targetW = if (ratio > 1) 512 else (512 * ratio).toInt()
                    val targetH = if (ratio > 1) (512 / ratio).toInt() else 512
                    Bitmap.createScaledBitmap(art, targetW, targetH, true)
                } else {
                    art
                }

                val file = File(cacheDir, "music_artwork.png")
                file.outputStream().use {
                    scaledArt.compress(Bitmap.CompressFormat.JPEG, 80, it)
                }
                hasArtwork = true
            } catch (e: Exception) {
                Log.e("MediaListener", "Failed to save artwork", e)
            }
        } else {
            File(cacheDir, "music_artwork.png").delete()
        }
        
        CoolWPrefs.prefs(this).edit {
            putString("music_title", title)
            putString("music_artist", artist)
            putBoolean("music_has_artwork", hasArtwork)
        }
        triggerWidgetUpdate()
    }

    private fun savePlaybackState(state: PlaybackState?) {
        if (state == null) return
        val isPlaying = state.state == PlaybackState.STATE_PLAYING
        
        CoolWPrefs.prefs(this).edit {
            putBoolean("music_is_playing", isPlaying)
        }
        
        triggerWidgetUpdate()
    }

    private fun triggerWidgetUpdate() {
        val intent = Intent(this, NothingMusicWidget::class.java).apply {
            action = NothingMusicWidget.ACTION_MUSIC_UPDATE
        }
        sendBroadcast(intent)
    }
}
