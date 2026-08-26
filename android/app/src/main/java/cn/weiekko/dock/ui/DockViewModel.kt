package cn.weiekko.dock.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.weiekko.dock.data.DemoSnapshot
import cn.weiekko.dock.data.DeviceType
import cn.weiekko.dock.data.DockLayout
import cn.weiekko.dock.data.DockModule
import cn.weiekko.dock.data.Health
import cn.weiekko.dock.data.HubClient
import cn.weiekko.dock.data.HubConnection
import cn.weiekko.dock.data.HubDevice
import cn.weiekko.dock.data.HubException
import cn.weiekko.dock.data.HubNetworkException
import cn.weiekko.dock.data.HubPreferences
import cn.weiekko.dock.data.MediaInfo
import cn.weiekko.dock.data.ModuleRect
import cn.weiekko.dock.data.Snapshot
import cn.weiekko.dock.data.WeatherClient
import cn.weiekko.dock.data.WeatherInfo
import cn.weiekko.dock.data.WeatherPlaces
import cn.weiekko.dock.data.TileLook
import cn.weiekko.dock.data.TypeLook
import cn.weiekko.dock.data.WinApp
import cn.weiekko.dock.data.defaultDockLayout
import cn.weiekko.dock.data.deviceType
import cn.weiekko.dock.data.grow
import cn.weiekko.dock.data.isLoginRequired
import cn.weiekko.dock.data.nudge
import cn.weiekko.dock.data.snap
import cn.weiekko.dock.data.toWinApp
import cn.weiekko.dock.media.PhoneMedia
import cn.weiekko.dock.power.ScreenCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DockUiState(
    val prefsReady: Boolean = false,
    val connection: HubConnection = HubConnection(),
    val snapshot: Snapshot? = null,
    val stale: Boolean = false,
    val banner: String? = null,
    val busyIds: Set<String> = emptySet(),
    val settingsStatus: String? = null,
    val settingsOk: Boolean = false,
    val testing: Boolean = false,
    val loading: Boolean = false,
    val preview: Boolean = true,
    val weather: WeatherInfo? = null,
    val weatherEnabled: Boolean = true,
    val weatherCity: String = HubPreferences.DEFAULT_WEATHER_CITY,
    val weatherError: String? = null,
    val winApps: List<WinApp> = DemoSnapshot.winApps,
    val selectedWinId: String? = null,
    val media: MediaInfo = MediaInfo.phoneIdle(),
    val backgroundVideoUri: String? = null,
    val tileLook: TileLook = TileLook(),
    val typeLook: TypeLook = TypeLook(),
    val powerScreen: Boolean = true,
    val hubSleepDelaySec: Int = HubPreferences.DEFAULT_HUB_SLEEP_DELAY_SEC,
    val hubReconnectSec: Int = HubPreferences.DEFAULT_HUB_RECONNECT_SEC,
    val hubSleeping: Boolean = false,
    val wakeWordEnabled: Boolean = true,
    val voiceListening: Boolean = false,
    val layout: DockLayout = DockLayout(),
    val editing: Boolean = false,
    val selectedModule: DockModule? = null,
) {
    val configured: Boolean get() = connection.isConfigured
}

class DockViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = HubPreferences(application)
    private val client = HubClient()
    private val weatherClient = WeatherClient()
    private val phoneMedia = PhoneMedia(application)

    private val _ui = MutableStateFlow(DockUiState())
    val ui: StateFlow<DockUiState> = _ui.asStateFlow()

    private val _goHome = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val goHome: SharedFlow<Unit> = _goHome.asSharedFlow()

    private val _goSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val goSettings: SharedFlow<Unit> = _goSettings.asSharedFlow()

    private val _screenCommands = MutableSharedFlow<ScreenCommand>(extraBufferCapacity = 8)
    val screenCommands: SharedFlow<ScreenCommand> = _screenCommands.asSharedFlow()

    private var pollJob: Job? = null
    private var hubSleepJob: Job? = null
    private var listeningJob: Job? = null
    private var disconnectedAt: Long? = null
    private var tileLookJob: Job? = null
    private var typeLookJob: Job? = null
    private var layoutJob: Job? = null
    private var weatherCityJob: Job? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                val local = phoneMedia.snapshot()
                _ui.update { it.copy(media = local) }
                delay(PHONE_MEDIA_MS)
            }
        }
        viewModelScope.launch {
            prefs.videoUri.collect { uri ->
                _ui.update { it.copy(backgroundVideoUri = resolveVideoUri(uri)) }
            }
        }
        viewModelScope.launch {
            val look = prefs.tileLook.first()
            _ui.update { it.copy(tileLook = look) }
        }
        viewModelScope.launch {
            val look = prefs.typeLook.first()
            _ui.update { it.copy(typeLook = look) }
        }
        viewModelScope.launch {
            prefs.powerScreen.collect { enabled ->
                _ui.update { it.copy(powerScreen = enabled) }
                if (!enabled) cancelHubSleep(wake = false)
                else if (disconnectedAt != null) scheduleHubSleep()
            }
        }
        viewModelScope.launch {
            prefs.hubSleepDelaySec.collect { sec ->
                _ui.update { it.copy(hubSleepDelaySec = sec) }
                if (disconnectedAt != null && !_ui.value.hubSleeping) scheduleHubSleep()
            }
        }
        viewModelScope.launch {
            prefs.hubReconnectSec.collect { sec ->
                _ui.update { it.copy(hubReconnectSec = sec) }
            }
        }
        viewModelScope.launch {
            prefs.wakeWordEnabled.collect { enabled ->
                _ui.update { it.copy(wakeWordEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            prefs.layout.collect { layout ->
                if (!_ui.value.editing) {
                    _ui.update { it.copy(layout = layout) }
                }
            }
        }
        viewModelScope.launch {
            combine(prefs.weatherEnabled, prefs.weatherCity) { enabled, city -> enabled to city }
                .collectLatest { (enabled, city) ->
                    _ui.update {
                        it.copy(
                            weatherEnabled = enabled,
                            weatherCity = city,
                            weather = if (enabled) it.weather else null,
                            weatherError = if (enabled) it.weatherError else null,
                        )
                    }
                    if (!enabled) return@collectLatest
                    val cache = prefs.weatherCache.first()
                    if (cache != null && cacheMatches(cache, city)) {
                        _ui.update { it.copy(weather = cache, weatherError = null) }
                    } else if (_ui.value.weather?.let { info -> cacheMatches(info, city) } != true) {
                        _ui.update { it.copy(weather = null) }
                    }
                    while (isActive) {
                        val ok = refreshWeather(city)
                        delay(if (ok) WEATHER_OK_MS else WEATHER_RETRY_MS)
                    }
                }
        }
        viewModelScope.launch {
            prefs.connection.collect { connection ->
                if (connection.isConfigured) {
                    _ui.update {
                        it.copy(
                            prefsReady = true,
                            connection = connection,
                            preview = false,
                            // 真连接时不要沿用预览假数据
                            snapshot = if (it.preview) null else it.snapshot,
                            winApps = emptyList(),
                            banner = null,
                        )
                    }
                    restartPolling(connection)
                } else {
                    pollJob?.cancel()
                    cancelHubSleep(wake = _ui.value.hubSleeping)
                    _ui.update {
                        it.copy(
                            prefsReady = true,
                            connection = connection,
                            preview = true,
                            stale = false,
                            banner = null,
                            loading = false,
                            snapshot = DemoSnapshot.create(),
                            winApps = DemoSnapshot.winApps,
                        )
                    }
                }
            }
        }
    }

    fun saveDraft(host: String, portText: String, token: String) {
        val port = portText.toIntOrNull() ?: HubConnection.DEFAULT_PORT
        viewModelScope.launch {
            prefs.save(HubConnection(host = host, port = port, token = token))
        }
    }

    fun setTileLook(look: TileLook) {
        val next = look.copy(
            opacityPercent = look.opacityPercent.coerceIn(TileLook.OPACITY_MIN, TileLook.OPACITY_MAX),
            cornerDp = look.cornerDp.coerceIn(TileLook.CORNER_MIN, TileLook.CORNER_MAX),
        )
        _ui.update { it.copy(tileLook = next) }
        tileLookJob?.cancel()
        tileLookJob = viewModelScope.launch {
            delay(160)
            prefs.saveTileLook(next)
        }
    }

    fun setTypeLook(look: TypeLook) {
        val next = look.copy(
            clockScalePercent = look.clockScalePercent.coerceIn(TypeLook.CLOCK_MIN, TypeLook.CLOCK_MAX),
            statsSize = look.statsSize.coerceIn(TypeLook.STATS_MIN, TypeLook.STATS_MAX),
            tileSize = look.tileSize.coerceIn(TypeLook.TILE_MIN, TypeLook.TILE_MAX),
            chipSize = look.chipSize.coerceIn(TypeLook.CHIP_MIN, TypeLook.CHIP_MAX),
        )
        _ui.update { it.copy(typeLook = next) }
        typeLookJob?.cancel()
        typeLookJob = viewModelScope.launch {
            delay(160)
            prefs.saveTypeLook(next)
        }
    }

    fun setPowerScreen(enabled: Boolean) {
        _ui.update { it.copy(powerScreen = enabled) }
        if (!enabled) cancelHubSleep(wake = _ui.value.hubSleeping)
        else if (disconnectedAt != null) scheduleHubSleep()
        viewModelScope.launch {
            prefs.savePowerScreen(enabled)
        }
    }

    fun setHubSleepDelay(sec: Int) {
        val next = sec.coerceIn(15, 600)
        _ui.update { it.copy(hubSleepDelaySec = next) }
        if (disconnectedAt != null && !_ui.value.hubSleeping) scheduleHubSleep()
        viewModelScope.launch {
            prefs.saveHubSleepDelaySec(next)
        }
    }

    fun setHubReconnect(sec: Int) {
        val next = sec.coerceIn(5, 120)
        _ui.update { it.copy(hubReconnectSec = next) }
        viewModelScope.launch {
            prefs.saveHubReconnectSec(next)
        }
    }

    fun setWeatherEnabled(enabled: Boolean) {
        _ui.update {
            it.copy(
                weatherEnabled = enabled,
                weather = if (enabled) it.weather else null,
                weatherError = if (enabled) it.weatherError else null,
            )
        }
        viewModelScope.launch {
            prefs.saveWeatherEnabled(enabled)
        }
    }

    fun setWeatherCity(city: String) {
        weatherCityJob?.cancel()
        weatherCityJob = viewModelScope.launch {
            delay(400)
            prefs.saveWeatherCity(city)
        }
    }

    fun setWakeWord(enabled: Boolean) {
        _ui.update { it.copy(wakeWordEnabled = enabled, voiceListening = if (enabled) it.voiceListening else false) }
        if (!enabled) listeningJob?.cancel()
        viewModelScope.launch {
            prefs.saveWakeWordEnabled(enabled)
        }
    }

    fun onWakeWord(keyword: String = "岸宝") {
        listeningJob?.cancel()
        val wasSleeping = _ui.value.hubSleeping
        _ui.update { it.copy(voiceListening = true, hubSleeping = false) }
        if (wasSleeping || _ui.value.powerScreen) {
            _screenCommands.tryEmit(ScreenCommand.VoiceWake)
        }
        listeningJob = viewModelScope.launch {
            delay(VOICE_LISTEN_MS)
            _ui.update { it.copy(voiceListening = false) }
            if (disconnectedAt != null && _ui.value.powerScreen) scheduleHubSleep()
        }
    }

    fun onHubReachable() {
        noteHubUp()
        val connection = _ui.value.connection
        if (connection.isConfigured) {
            viewModelScope.launch { pullSnapshot(connection, showLoading = false) }
        }
    }

    fun enterEdit() {
        _ui.update { it.copy(editing = true) }
        _goHome.tryEmit(Unit)
    }

    fun exitEdit() {
        persistLayout(_ui.value.layout)
        _ui.update { it.copy(editing = false, selectedModule = null) }
    }

    fun selectModule(id: DockModule?) {
        _ui.update { current ->
            if (current.selectedModule == id && (id == null || current.editing)) current
            else current.copy(selectedModule = id, editing = true)
        }
    }

    fun toggleModule(id: DockModule) {
        val current = _ui.value.layout.rect(id)
        setModuleVisible(id, !(current?.visible ?: true))
    }

    fun setModuleVisible(id: DockModule, visible: Boolean) {
        val layout = _ui.value.layout
        val current = layout.rect(id)
        val next = if (current == null || current.isPlaceholder()) {
            ModuleRect(
                x = 0f,
                y = 0f,
                w = 0f,
                h = 0f,
                visible = visible,
                chrome = current?.chrome ?: true,
            )
        } else {
            current.copy(visible = visible)
        }
        commitLayout(layout.with(id, next))
    }

    fun setModuleChrome(id: DockModule, chrome: Boolean) {
        val layout = _ui.value.layout
        val current = layout.rect(id)
        val next = if (current == null || current.isPlaceholder()) {
            ModuleRect(
                x = 0f,
                y = 0f,
                w = 0f,
                h = 0f,
                visible = current?.visible ?: true,
                chrome = chrome,
            )
        } else {
            current.copy(chrome = chrome)
        }
        commitLayout(layout.with(id, next))
    }

    fun toggleModuleChrome(
        id: DockModule,
        canvasW: Float,
        canvasH: Float,
        tileW: Float,
        tileH: Float,
    ) {
        val layout = resolvedLayout(canvasW, canvasH, tileW, tileH)
        val current = layout.rect(id) ?: return
        commitLayout(layout.with(id, current.copy(chrome = !current.chrome)))
    }

    fun hideModule(id: DockModule, canvasW: Float, canvasH: Float, tileW: Float, tileH: Float) {
        val layout = resolvedLayout(canvasW, canvasH, tileW, tileH)
        val current = layout.rect(id) ?: return
        commitLayout(layout.with(id, current.copy(visible = false)))
        _ui.update { it.copy(selectedModule = null) }
    }

    fun moveModule(
        id: DockModule,
        dxPx: Float,
        dyPx: Float,
        canvasW: Float,
        canvasH: Float,
        tileW: Float,
        tileH: Float,
    ) {
        if (canvasW <= 0f || canvasH <= 0f) return
        val layout = resolvedLayout(canvasW, canvasH, tileW, tileH)
        val current = layout.rect(id) ?: return
        val next = current.nudge(dxPx / canvasW, dyPx / canvasH).snap(canvasW, canvasH)
        commitLayout(layout.with(id, next), debounce = true)
    }

    fun resizeModule(
        id: DockModule,
        dwPx: Float,
        dhPx: Float,
        canvasW: Float,
        canvasH: Float,
        tileW: Float,
        tileH: Float,
    ) {
        if (canvasW <= 0f || canvasH <= 0f) return
        val layout = resolvedLayout(canvasW, canvasH, tileW, tileH)
        val current = layout.rect(id) ?: return
        val next = current.grow(dwPx / canvasW, dhPx / canvasH).snap(canvasW, canvasH)
        commitLayout(layout.with(id, next), debounce = true)
    }

    fun resetLayout() {
        commitLayout(DockLayout())
        _ui.update { it.copy(selectedModule = null) }
    }

    fun resolvedLayout(canvasW: Float, canvasH: Float, tileW: Float, tileH: Float): DockLayout {
        val defaults = defaultDockLayout(canvasW, canvasH, tileW, tileH)
        return _ui.value.layout.mergedWith(defaults)
    }

    private fun commitLayout(layout: DockLayout, debounce: Boolean = false) {
        _ui.update { it.copy(layout = layout) }
        if (!debounce) {
            persistLayout(layout)
            return
        }
        layoutJob?.cancel()
        layoutJob = viewModelScope.launch {
            delay(180)
            persistLayout(layout)
        }
    }

    private fun persistLayout(layout: DockLayout) {
        viewModelScope.launch {
            prefs.saveLayout(layout)
        }
    }

    fun setBackgroundVideo(uri: Uri?) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val previous = _ui.value.backgroundVideoUri
            if (uri != null) {
                runCatching {
                    resolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                prefs.saveVideoUri(uri.toString())
            } else {
                if (!previous.isNullOrBlank() && previous.startsWith("content:")) {
                    runCatching {
                        resolver.releasePersistableUriPermission(
                            Uri.parse(previous),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
                prefs.saveVideoUri(VIDEO_OFF)
            }
        }
    }

    fun testConnection(host: String, portText: String, token: String) {
        val port = portText.toIntOrNull()
        if (host.isBlank()) {
            _ui.update { it.copy(settingsStatus = "请填写 Hub 地址", settingsOk = false) }
            return
        }
        if (port == null || port !in 1..65535) {
            _ui.update { it.copy(settingsStatus = "端口无效", settingsOk = false) }
            return
        }
        if (token.isBlank()) {
            _ui.update { it.copy(settingsStatus = "请填写 Token", settingsOk = false) }
            return
        }
        val connection = HubConnection(host, port, token)
        viewModelScope.launch {
            _ui.update { it.copy(testing = true, settingsStatus = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { client.health(connection) }
            }
            result.fold(
                onSuccess = { health: Health ->
                    prefs.save(connection)
                    _ui.update {
                        it.copy(
                            testing = false,
                            settingsOk = true,
                            settingsStatus = "已连接 ${health.name}（协议 v${health.protocol}）",
                        )
                    }
                    _goHome.tryEmit(Unit)
                },
                onFailure = { error ->
                    _ui.update {
                        it.copy(
                            testing = false,
                            settingsOk = false,
                            settingsStatus = error.userMessage(),
                        )
                    }
                },
            )
        }
    }

    fun refreshNow() {
        val connection = _ui.value.connection
        if (!connection.isConfigured) return
        viewModelScope.launch { pullSnapshot(connection, showLoading = _ui.value.snapshot == null) }
    }

    fun tapWinApp(app: WinApp) {
        if (_ui.value.preview) {
            _ui.update {
                it.copy(
                    winApps = it.winApps.map { item ->
                        if (item.id == app.id) item.copy(running = !item.running) else item
                    },
                )
            }
            return
        }
        if (!app.online || app.id in _ui.value.busyIds) return
        _ui.update {
            it.copy(
                winApps = it.winApps.map { item ->
                    if (item.id == app.id) item.copy(running = true) else item
                },
            )
        }
        sendCommand(app.id) {
            client.command(_ui.value.connection, app.id, run = true)
        }
    }

    fun sendMedia(action: String) {
        if (action !in MEDIA_ACTIONS) return
        val before = _ui.value.media
        phoneMedia.dispatch(action)
        _ui.update {
            it.copy(
                media = when (action) {
                    "toggle" -> before.copy(playing = !before.playing)
                    else -> before.copy(playing = true)
                },
            )
        }
        viewModelScope.launch {
            delay(400)
            _ui.update { it.copy(media = phoneMedia.snapshot()) }
        }
    }

    fun setPower(device: HubDevice, on: Boolean) {
        if (device.deviceType() == DeviceType.Action) return
        if (_ui.value.preview) {
            patchDevice(device.id) { it.copy(on = on) }
            return
        }
        sendCommand(device.id) { client.command(_ui.value.connection, device.id, on = on) }
    }

    fun setBrightness(device: HubDevice, brightness: Int) {
        val value = brightness.coerceIn(1, 100)
        if (_ui.value.preview) {
            patchDevice(device.id) { it.copy(on = true, brightness = value) }
            return
        }
        sendCommand(device.id) {
            client.command(
                _ui.value.connection,
                device.id,
                on = true,
                brightness = value,
            )
        }
    }

    private fun patchDevice(deviceId: String, transform: (HubDevice) -> HubDevice) {
        _ui.update { state ->
            val snap = state.snapshot ?: return@update state
            state.copy(
                snapshot = snap.copy(
                    devices = snap.devices.map { if (it.id == deviceId) transform(it) else it },
                ),
            )
        }
    }

    private fun sendCommand(deviceId: String, block: () -> HubDevice) {
        if (deviceId in _ui.value.busyIds) return
        val connection = _ui.value.connection
        if (!connection.isConfigured) return
        viewModelScope.launch {
            _ui.update { it.copy(busyIds = it.busyIds + deviceId, banner = null) }
            val result = withContext(Dispatchers.IO) { runCatching(block) }
            result.fold(
                onSuccess = { updated ->
                    _ui.update { state ->
                        val snap = state.snapshot ?: return@update state.copy(busyIds = state.busyIds - deviceId)
                        val devices = snap.devices.map { if (it.id == updated.id) updated else it }
                        val fromSnap = devices
                            .filter { it.deviceType() == DeviceType.Action }
                            .map { it.toWinApp() }
                        val winApps = fromSnap.map { app ->
                            if (app.id == updated.id && updated.deviceType() == DeviceType.Action) {
                                app.copy(running = true)
                            } else {
                                app
                            }
                        }
                        state.copy(
                            busyIds = state.busyIds - deviceId,
                            stale = false,
                            snapshot = snap.copy(devices = devices),
                            winApps = winApps.ifEmpty { state.winApps },
                        )
                    }
                },
                onFailure = { error ->
                    handleFailure(error)
                    _ui.update { it.copy(busyIds = it.busyIds - deviceId) }
                },
            )
        }
    }

    private fun restartPolling(connection: HubConnection) {
        pollJob?.cancel()
        if (!connection.isConfigured) {
            _ui.update { it.copy(snapshot = null, stale = false, banner = null, loading = false) }
            return
        }
        pollJob = viewModelScope.launch {
            pullSnapshot(connection, showLoading = _ui.value.snapshot == null)
            while (isActive) {
                val down = disconnectedAt != null
                val hasLive = _ui.value.snapshot?.pc != null
                val wait = when {
                    down -> _ui.value.hubReconnectSec.coerceIn(5, 120) * 1000L
                    hasLive -> POLL_PC_MS
                    else -> POLL_MS
                }
                delay(wait)
                if (down) {
                    val reachable = withContext(Dispatchers.IO) {
                        runCatching { client.health(connection) }.isSuccess
                    }
                    if (!reachable) continue
                }
                pullSnapshot(connection, showLoading = false)
            }
        }
    }

    private suspend fun pullSnapshot(connection: HubConnection, showLoading: Boolean) {
        if (showLoading) _ui.update { it.copy(loading = true) }
        val result = withContext(Dispatchers.IO) {
            runCatching { client.snapshot(connection) }
        }
        result.fold(
            onSuccess = { snapshot ->
                val banner = when {
                    snapshot.hub.isLoginRequired() ->
                        snapshot.hub.message ?: "请在电脑上扫码登录米家"
                    snapshot.hub.mijia == "error" ->
                        snapshot.hub.message ?: "米家调用失败"
                    else -> null
                }
                val winApps = snapshot.devices
                    .filter { it.deviceType() == DeviceType.Action }
                    .map { it.toWinApp() }
                noteHubUp()
                _ui.update {
                    it.copy(
                        snapshot = snapshot,
                        stale = false,
                        banner = banner,
                        loading = false,
                        winApps = winApps.ifEmpty { if (it.preview) DemoSnapshot.winApps else emptyList() },
                    )
                }
            },
            onFailure = { error ->
                handleFailure(error)
                _ui.update { it.copy(loading = false, stale = it.snapshot != null) }
            },
        )
    }

    private fun handleFailure(error: Throwable) {
        if (error is CancellationException) throw error
        val unauthorized = error is HubException && error.isUnauthorized
        val login = error is HubException && error.isLoginRequired
        _ui.update {
            it.copy(
                banner = error.userMessage(),
                stale = it.snapshot != null && !login,
            )
        }
        if (error is HubNetworkException) {
            noteHubDown()
        } else {
            noteHubUp()
        }
        if (unauthorized) {
            _goSettings.tryEmit(Unit)
        }
    }

    private fun noteHubDown() {
        if (_ui.value.preview || !_ui.value.connection.isConfigured) return
        if (disconnectedAt == null) disconnectedAt = SystemClock.elapsedRealtime()
        scheduleHubSleep()
    }

    private fun noteHubUp() {
        val shouldWake = _ui.value.hubSleeping || disconnectedAt != null
        disconnectedAt = null
        hubSleepJob?.cancel()
        hubSleepJob = null
        if (_ui.value.hubSleeping) {
            _ui.update { it.copy(hubSleeping = false) }
        }
        if (shouldWake && _ui.value.powerScreen) {
            _screenCommands.tryEmit(ScreenCommand.Wake)
        }
    }

    private fun scheduleHubSleep() {
        hubSleepJob?.cancel()
        if (!_ui.value.powerScreen) return
        if (_ui.value.preview || !_ui.value.connection.isConfigured) return
        if (_ui.value.hubSleeping) return
        val started = disconnectedAt ?: return
        val waitMs = _ui.value.hubSleepDelaySec.coerceIn(15, 600) * 1000L
        val remaining = waitMs - (SystemClock.elapsedRealtime() - started)
        hubSleepJob = viewModelScope.launch {
            if (remaining > 0) delay(remaining)
            if (disconnectedAt == null) return@launch
            if (!_ui.value.powerScreen) return@launch
            _ui.update { it.copy(hubSleeping = true) }
            _screenCommands.tryEmit(ScreenCommand.Sleep)
        }
    }

    private fun cancelHubSleep(wake: Boolean) {
        disconnectedAt = null
        hubSleepJob?.cancel()
        hubSleepJob = null
        val wasSleeping = _ui.value.hubSleeping
        if (wasSleeping) _ui.update { it.copy(hubSleeping = false) }
        if (wake && wasSleeping) _screenCommands.tryEmit(ScreenCommand.Wake)
    }

    companion object {
        const val POLL_MS = 20_000L
        const val POLL_PC_MS = 3_000L
        const val PHONE_MEDIA_MS = 1_000L
        const val WEATHER_OK_MS = 20 * 60 * 1000L
        const val WEATHER_RETRY_MS = 90 * 1000L
        const val VIDEO_OFF = "off"
        const val VIDEO_FILE_NAME = "1.mp4"
        const val VOICE_LISTEN_MS = 6_000L
        val MEDIA_ACTIONS = setOf("toggle", "next", "previous")
    }

    private fun resolveVideoUri(stored: String): String? {
        if (stored == VIDEO_OFF) return null
        if (stored.isNotBlank()) return stored
        val file = File(
            getApplication<Application>().getExternalFilesDir(null),
            VIDEO_FILE_NAME,
        )
        return if (file.isFile && file.length() > 0L) Uri.fromFile(file).toString() else null
    }

    private suspend fun refreshWeather(city: String): Boolean {
        return try {
            val info = withContext(Dispatchers.IO) { weatherClient.fetch(city) }
            prefs.saveWeatherCache(info)
            _ui.update { it.copy(weather = info, weatherError = null) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { current ->
                current.copy(
                    weatherError = e.message ?: "天气获取失败",
                    weather = current.weather?.takeIf { cacheMatches(it, city) },
                )
            }
            false
        }
    }

    private fun cacheMatches(info: WeatherInfo, city: String): Boolean {
        val query = WeatherPlaces.normalize(city)
        return WeatherPlaces.normalize(info.query).equals(query, ignoreCase = true) ||
            WeatherPlaces.normalize(info.city).equals(query, ignoreCase = true)
    }
}

private fun Throwable.userMessage(): String = when (this) {
    is HubException -> message
    is HubNetworkException -> message
    else -> message ?: "未知错误"
}
