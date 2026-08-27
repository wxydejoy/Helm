package cn.weiekko.dock.voice

import android.content.Context
import android.media.MediaPlayer
import java.io.File

/** Plays companion wav. Do not touch the looping video player's volume. */
class CompanionVoice(context: Context) {
    private val cache = File(context.cacheDir, "companion-tts.wav")
    private var player: MediaPlayer? = null

    @Synchronized
    fun play(bytes: ByteArray, turnId: String = "") {
        stop()
        if (bytes.isEmpty()) return
        val started = HelmLatency.now()
        cache.writeBytes(bytes)
        val next = MediaPlayer()
        try {
            next.setDataSource(cache.absolutePath)
            next.setOnCompletionListener { stop() }
            next.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            next.prepare()
            next.start()
            player = next
            HelmLatency.log(turnId, "player_start", "ms=${HelmLatency.ms(started)} bytes=${bytes.size}")
        } catch (e: Exception) {
            HelmLatency.log(turnId, "player_error", "ms=${HelmLatency.ms(started)} err=${e.javaClass.simpleName}")
            next.release()
            player = null
        }
    }

    @Synchronized
    fun stop() {
        val current = player
        player = null
        if (current == null) return
        runCatching { current.stop() }
        current.release()
    }

    fun release() {
        stop()
        cache.delete()
    }
}
