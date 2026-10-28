package com.priorityring.app.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.getSystemService
import com.priorityring.engine.alert.SoundSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the alarm sound on the *alarm* audio stream (AudioAttributes.USAGE_ALARM). On stock
 * Android the alarm stream is not muted by silent/vibrate ringer mode and is allowed through Do Not
 * Disturb by default — but users can change that, and OEM skins may differ (device checklist).
 */
@Singleton
class AlarmSound @Inject constructor(@ApplicationContext private val context: Context) {
    private val audio = context.getSystemService<AudioManager>()
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var savedVolume: Int? = null

    /** Starts looping playback. Returns a human-readable warning or error, or null if all is well. */
    @Synchronized
    fun start(spec: SoundSpec): String? {
        stop()
        val notes = mutableListOf<String>()
        val am = audio
        if (am != null) {
            if (spec.fullVolume) {
                try {
                    savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                    am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
                } catch (e: SecurityException) {
                    notes += "couldn't raise alarm volume (${e.message})"
                }
            } else if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                notes += "alarm volume is 0, so it will be silent"
            }
            // Transient focus asks music etc. to pause while we ring.
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).build()
                .also { req ->
                    if (am.requestAudioFocus(req) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        notes += "audio focus denied (on Android 17 this means background audio was blocked)"
                    }
                }
        }
        val candidates = listOfNotNull(
            spec.uri.takeIf { it.isNotBlank() }?.let(Uri::parse),
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        ).distinct()
        for ((i, uri) in candidates.withIndex()) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(context, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                if (i > 0 && spec.uri.isNotBlank()) notes += "chosen sound unavailable, used a fallback"
                return notes.joinToString("; ").ifEmpty { null }
            } catch (e: Exception) {
                player?.release()
                player = null
            }
        }
        return (notes + "no playable alarm sound found").joinToString("; ")
    }

    val isPlaying: Boolean @Synchronized get() = player?.isPlaying == true

    @Synchronized
    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        focus?.let { audio?.abandonAudioFocusRequest(it) }
        focus = null
        savedVolume?.let { v -> runCatching { audio?.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) } }
        savedVolume = null
    }
}
