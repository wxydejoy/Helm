package cn.weiekko.dock.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.sin

/** 短促两音，接近音箱「叮」一下的确认。 */
object WakeChime {
    fun play() {
        thread(name = "wake-chime", isDaemon = true) {
            runCatching { playInternal() }
        }
    }

    private fun playInternal() {
        val sampleRate = 16_000
        val notes = listOf(
            880.0 to 0.09,
            1320.0 to 0.16,
        )
        val samples = notes.flatMap { (hz, seconds) ->
            tone(hz, seconds, sampleRate)
        }
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
            track.write(samples.toFloatArray(), 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            Thread.sleep(((samples.size / sampleRate.toFloat()) * 1000L).toLong() + 40L)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun tone(hz: Double, seconds: Double, sampleRate: Int): List<Float> {
        val n = (seconds * sampleRate).toInt().coerceAtLeast(1)
        val fade = (0.008 * sampleRate).toInt().coerceAtLeast(1)
        return List(n) { i ->
            val env = when {
                i < fade -> i / fade.toFloat()
                i > n - fade -> (n - i) / fade.toFloat()
                else -> 1f
            }
            (0.22 * env * sin(2.0 * Math.PI * hz * i / sampleRate)).toFloat()
        }
    }
}
