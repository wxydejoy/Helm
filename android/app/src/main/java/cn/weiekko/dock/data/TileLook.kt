package cn.weiekko.dock.data

enum class TileStyle {
    Frost,
    Ink,
    Solid,
    Line,
    ;

    val id: String
        get() = name.lowercase()

    val label: String
        get() = when (this) {
            Frost -> "磨砂白"
            Ink -> "深色"
            Solid -> "实心"
            Line -> "细线"
        }

    companion object {
        fun fromId(id: String?): TileStyle =
            entries.firstOrNull { it.id == id } ?: Ink
    }
}

data class TileLook(
    val style: TileStyle = TileStyle.Ink,
    val opacityPercent: Int = 42,
    val cornerDp: Int = 20,
) {
    val opacity: Float
        get() = opacityPercent.coerceIn(0, 100) / 100f

    companion object {
        const val OPACITY_MIN = 0
        const val OPACITY_MAX = 100
        const val CORNER_MIN = 8
        const val CORNER_MAX = 32
    }
}
