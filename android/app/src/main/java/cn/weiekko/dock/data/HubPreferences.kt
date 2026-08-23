package cn.weiekko.dock.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dock_hub")

data class HubConnection(
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val token: String = "",
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && token.isNotBlank() && port in 1..65535

    fun baseUrl(): String {
        val cleaned = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        return "http://$cleaned:$port"
    }

    companion object {
        const val DEFAULT_PORT = 17890
    }
}

class HubPreferences(private val context: Context) {
    val connection: Flow<HubConnection> = context.dataStore.data.map { prefs ->
        HubConnection(
            host = prefs[KEY_HOST].orEmpty(),
            port = prefs[KEY_PORT] ?: HubConnection.DEFAULT_PORT,
            token = prefs[KEY_TOKEN].orEmpty(),
        )
    }

    val videoUri: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_VIDEO_URI].orEmpty()
    }

    val tileLook: Flow<TileLook> = context.dataStore.data.map { prefs ->
        TileLook(
            style = TileStyle.fromId(prefs[KEY_TILE_STYLE]),
            opacityPercent = prefs[KEY_TILE_OPACITY] ?: TileLook().opacityPercent,
            cornerDp = prefs[KEY_TILE_CORNER] ?: TileLook().cornerDp,
        )
    }

    val typeLook: Flow<TypeLook> = context.dataStore.data.map { prefs ->
        TypeLook(
            font = DockFont.fromId(prefs[KEY_TYPE_FONT]),
            clockScalePercent = prefs[KEY_TYPE_CLOCK] ?: TypeLook().clockScalePercent,
            statsSize = prefs[KEY_TYPE_STATS] ?: TypeLook().statsSize,
            tileSize = prefs[KEY_TYPE_TILE] ?: TypeLook().tileSize,
            chipSize = prefs[KEY_TYPE_CHIP] ?: TypeLook().chipSize,
        )
    }

    val powerScreen: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_POWER_SCREEN] ?: true
    }

    suspend fun save(connection: HubConnection) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HOST] = connection.host.trim()
            prefs[KEY_PORT] = connection.port
            prefs[KEY_TOKEN] = connection.token.trim()
        }
    }

    suspend fun saveVideoUri(uri: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VIDEO_URI] = uri
        }
    }

    suspend fun saveTileLook(look: TileLook) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TILE_STYLE] = look.style.id
            prefs[KEY_TILE_OPACITY] = look.opacityPercent.coerceIn(TileLook.OPACITY_MIN, TileLook.OPACITY_MAX)
            prefs[KEY_TILE_CORNER] = look.cornerDp.coerceIn(TileLook.CORNER_MIN, TileLook.CORNER_MAX)
        }
    }

    suspend fun saveTypeLook(look: TypeLook) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TYPE_FONT] = look.font.id
            prefs[KEY_TYPE_CLOCK] = look.clockScalePercent.coerceIn(TypeLook.CLOCK_MIN, TypeLook.CLOCK_MAX)
            prefs[KEY_TYPE_STATS] = look.statsSize.coerceIn(TypeLook.STATS_MIN, TypeLook.STATS_MAX)
            prefs[KEY_TYPE_TILE] = look.tileSize.coerceIn(TypeLook.TILE_MIN, TypeLook.TILE_MAX)
            prefs[KEY_TYPE_CHIP] = look.chipSize.coerceIn(TypeLook.CHIP_MIN, TypeLook.CHIP_MAX)
        }
    }

    suspend fun savePowerScreen(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_POWER_SCREEN] = enabled
        }
    }

    companion object {
        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_PORT = intPreferencesKey("port")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_VIDEO_URI = stringPreferencesKey("background_video_uri")
        private val KEY_TILE_STYLE = stringPreferencesKey("tile_style")
        private val KEY_TILE_OPACITY = intPreferencesKey("tile_opacity")
        private val KEY_TILE_CORNER = intPreferencesKey("tile_corner")
        private val KEY_TYPE_FONT = stringPreferencesKey("type_font")
        private val KEY_TYPE_CLOCK = intPreferencesKey("type_clock")
        private val KEY_TYPE_STATS = intPreferencesKey("type_stats")
        private val KEY_TYPE_TILE = intPreferencesKey("type_tile")
        private val KEY_TYPE_CHIP = intPreferencesKey("type_chip")
        private val KEY_POWER_SCREEN = booleanPreferencesKey("power_screen")
    }
}
