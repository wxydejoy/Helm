package cn.weiekko.dock.data

data class WeatherInfo(
    val city: String,
    val celsius: Double,
    val condition: String,
)

data class WinApp(
    val id: String,
    val name: String,
    val icon: String? = null,
    val online: Boolean = true,
)

object DemoSnapshot {
    val weather = WeatherInfo(
        city = "室外",
        celsius = 26.0,
        condition = "多云",
    )

    val winApps = listOf(
        WinApp(id = "chrome", name = "Chrome", icon = "chrome"),
        WinApp(id = "cursor", name = "Cursor", icon = "cursor"),
        WinApp(id = "terminal", name = "终端", icon = "terminal"),
    )

    val media = MediaInfo(
        online = true,
        playing = true,
        title = "Night Drive",
        artist = "Demo",
        app = "Music",
        updatedAt = "2026-08-15T16:00:00Z",
    )

    fun create(): Snapshot = Snapshot(
        protocol = 1,
        hub = HubStatus(name = "Helm", mijia = "ok"),
        temperature = Temperature(
            id = "desk",
            name = "室内",
            celsius = 24.6,
            humidity = 48.0,
            updatedAt = "2026-08-15T16:00:00Z",
            online = true,
        ),
        pc = PcStatus(
            online = true,
            updatedAt = "2026-08-15T16:00:00Z",
            cpu = PcCpu(percent = 24.0, tempCelsius = 58.0),
            memory = PcMemory(percent = 47.0, usedGb = 15.0, totalGb = 32.0),
            gpu = PcGpu(name = "RTX 4070", percent = 12.0, tempCelsius = 49.0),
            fps = 144.0,
        ),
        media = media,
        devices = listOf(
            HubDevice(
                id = "lamp",
                name = "台灯",
                type = "light",
                online = true,
                on = true,
                brightness = 72,
            ),
            HubDevice(
                id = "monitor-plug",
                name = "显示器",
                type = "switch",
                online = true,
                on = false,
            ),
        ),
    )
}
