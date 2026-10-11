package com.financier.app.common

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.financier.app.R

/** Optional, low-volume ambient music and short UI feedback sounds. */
object AppAudio {
    private const val PREFS = "audio_preferences"
    private const val MUSIC_ENABLED = "background_music_enabled"
    private const val SFX_ENABLED = "sound_effects_enabled"

    @Volatile private var player: MediaPlayer? = null
    @Volatile private var soundPool: SoundPool? = null
    @Volatile private var clickSoundId = 0
    @Volatile private var clickLoaded = false

    fun isMusicEnabled(context: Context) = prefs(context).getBoolean(MUSIC_ENABLED, false)
    fun areSoundEffectsEnabled(context: Context) = prefs(context).getBoolean(SFX_ENABLED, true)
    fun setMusicEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(MUSIC_ENABLED, enabled).apply()
    fun setSoundEffectsEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(SFX_ENABLED, enabled).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun startMusic(context: Context) {
        if (!isMusicEnabled(context)) return
        try {
            val current = player ?: MediaPlayer.create(context.applicationContext, R.raw.anime_ambient)?.also {
                it.isLooping = true
                it.setVolume(0.18f, 0.18f)
                player = it
            }
            if (current != null && !current.isPlaying) current.start()
        } catch (_: RuntimeException) { releaseMusic() }
    }

    @Synchronized
    fun pauseMusic() {
        try { if (player?.isPlaying == true) player?.pause() }
        catch (_: RuntimeException) { releaseMusic() }
    }

    @Synchronized
    fun stopMusic() = releaseMusic()

    private fun releaseMusic() {
        try { player?.release() } catch (_: RuntimeException) { }
        player = null
    }

    @Synchronized
    fun playClick(context: Context) {
        if (!areSoundEffectsEnabled(context)) return
        try {
            var pool = soundPool
            if (pool == null) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val createdPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
                pool = createdPool
                soundPool = createdPool
                clickLoaded = false
                createdPool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (sampleId == clickSoundId && status == 0) clickLoaded = true
                }
                clickSoundId = createdPool.load(context.applicationContext, R.raw.ui_click, 1)
            }
            if (clickLoaded && clickSoundId != 0) pool?.play(clickSoundId, .45f, .45f, 1, 0, 1f)
        } catch (_: RuntimeException) { /* Audio is optional; never block app interaction. */ }
    }
}
