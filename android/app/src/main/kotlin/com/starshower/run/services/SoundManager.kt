//
//  SoundManager.kt
//  Starshower Run
//
//  Plays the synthesised effects (SoundPool, loaded from WAVs rendered into
//  the cache at launch) and the music loop (a looping static AudioTrack).
//  No audio focus is requested, so the game mixes with other audio just like
//  the iOS ambient session.
//

package com.starshower.run.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.SystemClock
import com.starshower.run.core.audio.SoundEffect
import com.starshower.run.core.audio.SynthSamples
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

class SoundManager(private val context: Context, private val preferences: Preferences) {

    private val sampleRate = 44_100
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "starshower-audio").apply { isDaemon = true }
    }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var soundPool: SoundPool? = null
    private val soundIDs = HashMap<SoundEffect, Int>()
    private val loaded = HashSet<Int>()
    private val lastPlayed = HashMap<SoundEffect, Long>()
    private var isSetUp = false

    @Volatile private var musicTrack: AudioTrack? = null
    @Volatile private var musicLoading = false
    /** What the game wants the music to be doing once the loop is ready. */
    @Volatile private var wantsMusic = false

    val effectsEnabled: Boolean get() = preferences.soundEnabled
    val musicEnabled: Boolean get() = preferences.musicEnabled

    // MARK: Setup

    fun prepare() {
        if (isSetUp) return
        isSetUp = true
        val pool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(attributes)
            .build()
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(loaded) { loaded += sampleId }
        }
        soundPool = pool

        executor.execute {
            val directory = File(context.cacheDir, "sfx-$SYNTH_VERSION").apply { mkdirs() }
            for (effect in SoundEffect.entries) {
                val file = File(directory, "${effect.name.lowercase()}.wav")
                if (!file.exists() || file.length() == 0L) {
                    writeWav(file, SynthSamples.samples(effect, sampleRate.toDouble()))
                }
                val id = pool.load(file.path, 1)
                synchronized(soundIDs) { soundIDs[effect] = id }
            }
        }
    }

    // MARK: Effects

    fun play(effect: SoundEffect) {
        if (!effectsEnabled || !isSetUp) return
        val pool = soundPool ?: return
        val id = synchronized(soundIDs) { soundIDs[effect] } ?: return
        if (synchronized(loaded) { id !in loaded }) return

        // Avoid machine-gunning the same sound within a couple of frames.
        val now = SystemClock.uptimeMillis()
        val last = lastPlayed[effect]
        if (last != null && now - last < 35) return
        lastPlayed[effect] = now

        pool.play(id, 0.85f, 0.85f, 1, 0, 1f)
    }

    // MARK: Music

    fun startMusic() {
        if (!musicEnabled || !isSetUp) return
        wantsMusic = true
        val track = musicTrack
        if (track != null) {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) runCatching { track.play() }
            return
        }
        if (musicLoading) return
        musicLoading = true
        executor.execute {
            val samples = SynthSamples.musicLoop(sampleRate.toDouble())
            val pcm = toPcm16(samples)
            val created = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                    .apply {
                        write(pcm, 0, pcm.size)
                        setLoopPoints(0, pcm.size, -1)
                        setVolume(0.32f)
                    }
            }.getOrNull()
            synchronized(this) {
                musicTrack = created
                musicLoading = false
                if (wantsMusic && musicEnabled) runCatching { created?.play() }
            }
        }
    }

    fun pauseMusic() {
        wantsMusic = false
        musicTrack?.let { runCatching { it.pause() } }
    }

    fun stopMusic() {
        wantsMusic = false
        musicTrack?.let { track ->
            runCatching {
                track.pause()
                // Rewind so the next run starts the groove from the top.
                track.stop()
                track.reloadStaticData()
                track.setLoopPoints(0, track.bufferSizeInFrames, -1)
            }
        }
    }

    // MARK: Encoding

    private fun toPcm16(samples: FloatArray): ShortArray =
        ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }

    private fun writeWav(file: File, samples: FloatArray) {
        val pcm = toPcm16(samples)
        val dataSize = pcm.size * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(1) // mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        for (sample in pcm) buffer.putShort(sample)
        FileOutputStream(file).use { it.write(buffer.array()) }
    }

    private companion object {
        /** Bump when the synth changes so cached WAVs are re-rendered. */
        const val SYNTH_VERSION = 1
    }
}
