package cn.weiekko.dock.data

import android.content.Context
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

    suspend fun save(connection: HubConnection) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HOST] = connection.host.trim()
            prefs[KEY_PORT] = connection.port
            prefs[KEY_TOKEN] = connection.token.trim()
        }
    }

    companion object {
        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_PORT = intPreferencesKey("port")
        private val KEY_TOKEN = stringPreferencesKey("token")
    }
}
