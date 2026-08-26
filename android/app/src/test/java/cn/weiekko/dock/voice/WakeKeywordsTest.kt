package cn.weiekko.dock.voice

import org.junit.Assert.assertTrue
import org.junit.Test

class WakeKeywordsTest {
    @Test
    fun anbaoUsesInitialFinalPinyin() {
        val line = WakeKeywords.LINES.first { it.contains("@岸宝") }
        assertTrue(line.startsWith("àn b ǎo"))
        assertTrue(line.contains("#"))
    }

    @Test
    fun streamJoinsWithSlash() {
        assertTrue(WakeKeywords.STREAM.contains("/"))
        assertTrue(WakeKeywords.STREAM.contains("@岸宝"))
        assertTrue(WakeKeywords.STREAM.contains("@嘿岸宝"))
        assertTrue(!WakeKeywords.STREAM.contains("\n"))
    }
}
