package cn.weiekko.dock.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

enum class DockModule {
    Clock,
    Pc,
    Win0,
    Win1,
    Win2,
    Device0,
    Device1,
    Media,
    Weather,
    Indoor,
    ;

    val label: String
        get() = when (this) {
            Clock -> "时钟"
            Pc -> "CPU 信息"
            Win0 -> "启动 1"
            Win1 -> "启动 2"
            Win2 -> "启动 3"
            Device0 -> "设备 1"
            Device1 -> "设备 2"
            Media -> "媒体"
            Weather -> "室外天气"
            Indoor -> "室内温湿度"
        }
}

@Serializable
data class ModuleRect(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val visible: Boolean = true,
    val chrome: Boolean = true,
) {
    fun isPlaceholder(): Boolean = w < 0.05f || h < 0.05f

    fun clamped(): ModuleRect {
        val nw = w.coerceIn(0.08f, 1f)
        val nh = h.coerceIn(0.08f, 1f)
        return copy(
            x = x.coerceIn(0f, 1f - nw),
            y = y.coerceIn(0f, 1f - nh),
            w = nw,
            h = nh,
        )
    }
}

@Serializable
data class DockLayout(
    val version: Int = 1,
    val modules: Map<String, ModuleRect> = emptyMap(),
) {
    fun rect(id: DockModule): ModuleRect? = modules[id.name]

    fun with(id: DockModule, rect: ModuleRect): DockLayout {
        val stored = if (rect.isPlaceholder()) {
            rect.copy(x = 0f, y = 0f, w = 0f, h = 0f)
        } else {
            rect.clamped()
        }
        return copy(modules = modules + (id.name to stored))
    }

    fun mergedWith(defaults: DockLayout): DockLayout {
        if (modules.isEmpty()) return defaults
        val merged = defaults.modules.toMutableMap()
        modules.forEach { (key, rect) ->
            val base = merged[key]
            merged[key] = if (base != null && rect.isPlaceholder()) {
                base.copy(visible = rect.visible, chrome = rect.chrome)
            } else {
                rect.clamped()
            }
        }
        return copy(version = 1, modules = merged)
    }

    companion object {
        const val GRID_PX = 8f
    }
}

fun defaultDockLayout(canvasW: Float, canvasH: Float, tileW: Float, tileH: Float): DockLayout {
    val gap = 10f
    val lift = -canvasH * 0.04f
    val colH = tileH * 3f + gap * 2f
    val leftY = ((canvasH - colH) / 2f + lift * 0.25f).coerceAtLeast(0f)
    val rightX = (canvasW - tileW).coerceAtLeast(0f)

    val clockW = (canvasW * 0.44f).coerceAtMost(canvasH * 0.95f)
    val clockH = (canvasH * 0.44f).coerceAtMost(clockW * 0.78f)
    val clockX = (canvasW - clockW) / 2f
    val clockY = (canvasH - clockH) / 2f + lift

    val pcW = (canvasW * 0.40f).coerceAtMost(340f)
    val pcH = (canvasH * 0.26f).coerceAtMost(110f)
    val pcX = (canvasW - pcW) / 2f
    val pcY = 2f

    val chipW = (canvasW * 0.26f).coerceAtMost(210f)
    val chipH = (canvasH * 0.15f).coerceAtMost(56f)
    val chipY = (canvasH - chipH).coerceAtLeast(0f)
    val weatherX = (canvasW / 2f - chipW - 6f).coerceAtLeast(0f)
    val indoorX = (canvasW / 2f + 6f).coerceAtMost(canvasW - chipW)

    fun rect(x: Float, y: Float, w: Float, h: Float) = ModuleRect(
        x = (x / canvasW).coerceIn(0f, 1f),
        y = (y / canvasH).coerceIn(0f, 1f),
        w = (w / canvasW).coerceIn(0.08f, 1f),
        h = (h / canvasH).coerceIn(0.08f, 1f),
    )

    return DockLayout(
        modules = mapOf(
            DockModule.Clock.name to rect(clockX, clockY, clockW, clockH),
            DockModule.Pc.name to rect(pcX, pcY, pcW, pcH),
            DockModule.Win0.name to rect(0f, leftY, tileW, tileH),
            DockModule.Win1.name to rect(0f, leftY + tileH + gap, tileW, tileH),
            DockModule.Win2.name to rect(0f, leftY + (tileH + gap) * 2f, tileW, tileH),
            DockModule.Device0.name to rect(rightX, leftY, tileW, tileH),
            DockModule.Device1.name to rect(rightX, leftY + tileH + gap, tileW, tileH),
            DockModule.Media.name to rect(rightX, leftY + (tileH + gap) * 2f, tileW, tileH),
            DockModule.Weather.name to rect(weatherX, chipY, chipW, chipH),
            DockModule.Indoor.name to rect(indoorX, chipY, chipW, chipH),
        ),
    )
}

fun ModuleRect.nudge(dxFrac: Float, dyFrac: Float): ModuleRect =
    copy(x = x + dxFrac, y = y + dyFrac).clamped()

fun ModuleRect.grow(dwFrac: Float, dhFrac: Float): ModuleRect =
    copy(w = w + dwFrac, h = h + dhFrac).clamped()

fun ModuleRect.snap(canvasW: Float, canvasH: Float, grid: Float = DockLayout.GRID_PX): ModuleRect {
    fun snapValue(frac: Float, total: Float): Float {
        val px = frac * total
        return ((px / grid).roundToInt() * grid) / total
    }
    return copy(
        x = snapValue(x, canvasW),
        y = snapValue(y, canvasH),
        w = snapValue(w, canvasW).coerceAtLeast(0.08f),
        h = snapValue(h, canvasH).coerceAtLeast(0.08f),
    ).clamped()
}
