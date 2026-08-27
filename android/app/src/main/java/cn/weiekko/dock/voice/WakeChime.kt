package cn.weiekko.dock.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.exp
import kotlin.math.sin

/** 轻、短、偏低的确认音，避免尖促两声。 */
object WakeChime {
    fun play() {
        thread(name = "wake-chime", isDaemon = true) {
            runCatching { playInternal() }
        }
    }

    private fun playInternal() {
        val sampleRate = 16_000
        val samples = mix(
            bell(hz = 523.25, start = 0.0, seconds = 0.34, amp = 0.055, sampleRate),
            bell(hz = 659.25, start = 0.06, seconds = 0.30, amp = 0.042, sampleRate),
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            Thread.sleep(((samples.size / sampleRate.toFloat()) * 1000L).toLong() + 40L)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun mix(vararg layers: FloatArray): FloatArray {
        val n = layers.maxOf { it.size }
        val out = FloatArray(n)
        for (layer in layers) {
            for (i in layer.indices) {
                out[i] = (out[i] + layer[i]).coerceIn(-0.18f, 0.18f)
            }
        }
        return out
    }

    private fun bell(
        hz: Double,
        start: Double,
        seconds: Double,
        amp: Double,
        sampleRate: Int,
    ): FloatArray {
        val delay = (start * sampleRate).toInt().coerceAtLeast(0)
        val n = (seconds * sampleRate).toInt().coerceAtLeast(1)
        val attack = (0.018 * sampleRate).toInt().coerceAtLeast(1)
        val tau = 0.09 * sampleRate
        val out = FloatArray(delay + n)
        for (i in 0 until n) {
            val envAttack = if (i < attack) i / attack.toFloat() else 1f
            val decay = exp(-i / tau).toFloat()
            val t = i / sampleRate.toDouble()
            val wave = sin(2.0 * Math.PI * hz * t) + 0.16 * sin(4.0 * Math.PI * hz * t)
            out[delay + i] = (amp * envAttack * decay * wave).toFloat()
        }
        return out
    }
}
