package cn.weiekko.dock.data

enum class DockFont {
    System,
    Sans,
    Serif,
    Mono,
    Inter,
    Outfit,
    JetBrainsMono,
    Smiley,
    ;

    val id: String
        get() = when (this) {
            System -> "system"
            Sans -> "sans"
            Serif -> "serif"
            Mono -> "mono"
            Inter -> "inter"
            Outfit -> "outfit"
            JetBrainsMono -> "jetbrains"
            Smiley -> "smiley"
        }

    val label: String
        get() = when (this) {
            System -> "系统默认"
            Sans -> "系统黑体"
            Serif -> "系统宋体"
            Mono -> "系统等宽"
            Inter -> "Inter"
            Outfit -> "Outfit"
            JetBrainsMono -> "JetBrains Mono"
            Smiley -> "得意黑"
        }

    companion object {
        fun fromId(id: String?): DockFont =
            entries.firstOrNull { it.id == id } ?: System
    }
}

data class TypeLook(
    val font: DockFont = DockFont.System,
    val clockScalePercent: Int = 88,
    val statsSize: Int = 15,
    val tileSize: Int = 15,
    val chipSize: Int = 15,
) {
    companion object {
        const val CLOCK_MIN = 60
        const val CLOCK_MAX = 140
        const val STATS_MIN = 11
        const val STATS_MAX = 26
        const val TILE_MIN = 11
        const val TILE_MAX = 22
        const val CHIP_MIN = 11
        const val CHIP_MAX = 22
    }
}
