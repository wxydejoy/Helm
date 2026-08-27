package cn.weiekko.dock.voice

import android.os.SystemClock
import android.util.Log
import java.util.UUID

/** One-line voice-pipeline timings. Grep `HelmLatency` + `turn=`. */
object HelmLatency {
    const val TAG = "HelmLatency"

    fun newTurn(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    fun now(): Long = try {
        SystemClock.elapsedRealtime()
    } catch (_: RuntimeException) {
        System.nanoTime() / 1_000_000L
    }

    fun ms(since: Long): Long = (now() - since).coerceAtLeast(0L)

    fun log(turn: String, stage: String, extra: String = "") {
        val id = turn.ifBlank { "-" }
        val suffix = if (extra.isBlank()) "" else " $extra"
        val line = "turn=$id stage=$stage$suffix"
        try {
            Log.i(TAG, line)
        } catch (_: RuntimeException) {
            // android.jar stubs throw in JVM unit tests
        }
    }
}
