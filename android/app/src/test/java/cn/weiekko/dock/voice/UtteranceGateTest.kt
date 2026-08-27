package cn.weiekko.dock.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtteranceGateTest {
    @Test
    fun emptySilenceStopsWithoutWaitingForMax() {
        val gate = UtteranceGate()
        assertEquals(UtteranceGate.StopReason.Empty, runUntilStop(gate, rms = 0.004f, limitMs = 8_000L))
    }

    @Test
    fun emptyWaitsSeveralSeconds() {
        val gate = UtteranceGate()
        feed(gate, rms = 0.004f, ms = 4_000L)
        assertNull(gate.shouldStop(4_000L))
    }

    @Test
    fun speechThenPauseStopsOnTail() {
        val gate = UtteranceGate()
        feed(gate, rms = 0.006f, ms = 280L)
        feed(gate, rms = 0.09f, ms = 240L)
        assertEquals(true, gate.heard)
        assertNull(gate.shouldStop(520L))
        feed(gate, rms = 0.003f, ms = 1_000L)
        assertEquals(UtteranceGate.StopReason.Tail, gate.shouldStop(1_520L))
    }

    @Test
    fun shortClickIsNotSpeech() {
        val gate = UtteranceGate()
        feed(gate, rms = 0.006f, ms = 280L)
        feed(gate, rms = 0.08f, ms = 50L)
        feed(gate, rms = 0.004f, ms = 400L)
        assertEquals(false, gate.heard)
        assertNull(gate.shouldStop(800L))
    }

    @Test
    fun asrTextCountsAsHeard() {
        val gate = UtteranceGate()
        feed(gate, rms = 0.004f, ms = 280L)
        gate.noteHeard()
        feed(gate, rms = 0.003f, ms = 1_300L)
        assertEquals(UtteranceGate.StopReason.Tail, gate.shouldStop(1_580L))
    }

    @Test
    fun quieterContinuationDoesNotCountAsEnd() {
        val gate = UtteranceGate()
        feed(gate, rms = 0.006f, ms = 280L)
        feed(gate, rms = 0.09f, ms = 240L)
        assertEquals(true, gate.heard)
        feed(gate, rms = 0.028f, ms = 2_000L)
        assertNull(gate.shouldStop(2_600L))
    }

    private fun runUntilStop(gate: UtteranceGate, rms: Float, limitMs: Long): UtteranceGate.StopReason? {
        var elapsed = 0L
        while (elapsed < limitMs) {
            elapsed += 50L
            gate.onAudio(rms, 50L)
            gate.shouldStop(elapsed)?.let { return it }
        }
        return null
    }

    private fun feed(gate: UtteranceGate, rms: Float, ms: Long) {
        var left = ms
        while (left > 0L) {
            val dt = minOf(50L, left)
            gate.onAudio(rms, dt)
            left -= dt
        }
    }
}
