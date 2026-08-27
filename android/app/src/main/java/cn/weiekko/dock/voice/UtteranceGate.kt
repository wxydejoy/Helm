package cn.weiekko.dock.voice

import kotlin.math.sqrt

/**
 * 用音量判断一句什么时候算说完。
 * sherpa 的 CTC 端点在这套模型上经常拖到上限才停，所以听感要靠这个。
 */
internal class UtteranceGate(
    private val endSilenceMs: Long = 2_400L,
    private val emptyMs: Long = 6_500L,
    private val maxMs: Long = 16_000L,
    private val minTailMs: Long = 3_000L,
    private val minSpeechMs: Long = 140L,
    private val calibrateMs: Long = 280L,
) {
    var heard = false
        private set
    private var speechRunMs = 0L
    private var silenceMs = 0L
    private var calibratedMs = 0L
    private var noise = 0.012f

    fun noteHeard() {
        heard = true
        silenceMs = 0L
        speechRunMs = minSpeechMs
    }

    fun onAudio(rms: Float, dtMs: Long) {
        if (dtMs <= 0L) return
        if (calibratedMs < calibrateMs) {
            calibratedMs += dtMs
            noise = noise * 0.72f + rms * 0.28f
            if (rms < 0.06f) return
        }
        val speechRms = (noise * 3.4f).coerceIn(0.014f, 0.048f)
        val silenceRms = (noise * 1.45f).coerceIn(0.006f, 0.016f)
        when {
            rms >= speechRms -> {
                speechRunMs += dtMs
                silenceMs = 0L
                if (speechRunMs >= minSpeechMs) heard = true
            }
            rms <= silenceRms -> {
                speechRunMs = 0L
                silenceMs += dtMs
            }
            else -> {
                // 两字之间音量掉一点，不要算成说完
                speechRunMs = 0L
            }
        }
    }

    fun shouldStop(elapsedMs: Long): StopReason? {
        if (elapsedMs >= maxMs) return StopReason.Max
        if (heard && elapsedMs >= minTailMs && silenceMs >= endSilenceMs) return StopReason.Tail
        if (!heard && elapsedMs >= emptyMs) return StopReason.Empty
        return null
    }

    enum class StopReason { Tail, Empty, Max, Endpoint }
}

internal fun audioRms(samples: FloatArray): Float {
    if (samples.isEmpty()) return 0f
    var sum = 0.0
    for (sample in samples) sum += sample * sample
    return sqrt(sum / samples.size).toFloat()
}
