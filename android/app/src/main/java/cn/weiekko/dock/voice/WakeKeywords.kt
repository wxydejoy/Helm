package cn.weiekko.dock.voice

/** sherpa-onnx 中文 KWS 用声母+韵母（ppinyin），「岸宝」无需再训练。 */
object WakeKeywords {
    const val PHRASE = "岸宝"
    const val ALT = "嘿岸宝"

    val LINES = listOf(
        "àn b ǎo :2.0 #0.25 @岸宝",
        "h ēi àn b ǎo :2.2 #0.20 @嘿岸宝",
    )

    /** createStream 用 `/` 分隔多行关键词。 */
    val STREAM: String = LINES.joinToString("/")

    const val ENCODER = "kws/encoder.int8.onnx"
    const val DECODER = "kws/decoder.onnx"
    const val JOINER = "kws/joiner.int8.onnx"
    const val TOKENS = "kws/tokens.txt"
    const val KEYWORDS_FILE = "kws/keywords.txt"
}
