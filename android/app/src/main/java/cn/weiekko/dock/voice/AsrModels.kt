package cn.weiekko.dock.voice

object AsrModels {
    const val MODEL = "asr/model.int8.onnx"
    const val TOKENS = "asr/tokens.txt"

    fun display(raw: String): String {
        return raw.replace("▁", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
