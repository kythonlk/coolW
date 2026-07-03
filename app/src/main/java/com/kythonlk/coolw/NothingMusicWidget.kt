package com.kythonlk.coolw

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import android.graphics.Color
import android.widget.RemoteViews
import android.graphics.BitmapFactory
import java.io.File

class NothingMusicWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.kythonlk.coolw.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.kythonlk.coolw.ACTION_NEXT"
        const val ACTION_PREV = "com.kythonlk.coolw.ACTION_PREV"
        const val ACTION_MUSIC_UPDATE = "com.kythonlk.coolw.ACTION_MUSIC_UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        
        if (action == ACTION_PLAY_PAUSE || action == ACTION_NEXT || action == ACTION_PREV) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val keyCode = when (action) {
                ACTION_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                ACTION_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                ACTION_PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> return
            }

            val eventTime = SystemClock.uptimeMillis()
            
            // Dispatch key down
            val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            
            // Dispatch key up
            val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(upEvent)

            // Toggling local playing state to make widget immediately responsive
            if (action == ACTION_PLAY_PAUSE) {
                val prefs = CoolWPrefs.prefs(context)
                val isPlaying = prefs.getBoolean("music_is_playing", false)
                prefs.edit { putBoolean("music_is_playing", !isPlaying) }
            }

            // Trigger widget redraw
            val updateIntent = Intent(context, NothingMusicWidget::class.java).apply {
                this.action = ACTION_MUSIC_UPDATE
            }
            context.sendBroadcast(updateIntent)
        } else if (action == ACTION_MUSIC_UPDATE || action == "com.kythonlk.coolw.UPDATE_ALL_WIDGETS") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, NothingMusicWidget::class.java.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = CoolWPrefs.prefs(context)
        val title = prefs.getString("music_title", "Nothing Track") ?: "Nothing Track"
        val artist = prefs.getString("music_artist", "Nothing OS") ?: "Nothing OS"
        val isPlaying = prefs.getBoolean("music_is_playing", false)
        val hasArtwork = prefs.getBoolean("music_has_artwork", false)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_nothing_music)

            views.setTextViewText(R.id.music_title, title)
            views.setTextViewText(R.id.music_artist, artist)
            views.setTextViewText(R.id.music_status, if (isPlaying) "NOW PLAYING" else "PAUSED")
            views.setImageViewResource(R.id.music_dot, if (isPlaying) R.drawable.nothing_red_dot else R.drawable.nothing_red_dot_dim)
            views.setImageViewResource(R.id.btn_play_pause, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

            if (hasArtwork) {
                val file = File(context.cacheDir, "music_artwork.png")
                if (file.exists()) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            views.setImageViewBitmap(R.id.music_artwork_bg, bitmap)
                            views.setViewVisibility(R.id.music_artwork_bg, android.view.View.VISIBLE)
                        } else {
                            views.setViewVisibility(R.id.music_artwork_bg, android.view.View.GONE)
                        }
                    } catch (_: Exception) {
                        views.setViewVisibility(R.id.music_artwork_bg, android.view.View.GONE)
                    }
                } else {
                    views.setViewVisibility(R.id.music_artwork_bg, android.view.View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.music_artwork_bg, android.view.View.GONE)
            }
            
            // Tint the play/pause icon black because it sits on a red background
            views.setInt(R.id.btn_play_pause, "setColorFilter", Color.BLACK)

            // Set up PendingIntents for controls
            views.setOnClickPendingIntent(R.id.btn_prev, getPendingSelfIntent(context, ACTION_PREV, 10))
            views.setOnClickPendingIntent(R.id.btn_play_pause, getPendingSelfIntent(context, ACTION_PLAY_PAUSE, 11))
            views.setOnClickPendingIntent(R.id.btn_next, getPendingSelfIntent(context, ACTION_NEXT, 12))

            // Open app when clicking song text
            val appIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 2, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.music_title, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun getPendingSelfIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, NothingMusicWidget::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
