package cn.weiekko.dock.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HubClient(
    private val http: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
) {
    fun health(connection: HubConnection): Health {
        val request = Request.Builder()
            .url("${connection.baseUrl()}/health")
            .get()
            .build()
        val body = execute(request, readTimeoutMs = SNAPSHOT_TIMEOUT_MS, expectAuthError = false)
        val health = decode<Health>(body)
        if (health.service != "dock-hub") {
            throw HubException("bad_request", "这不是 Dock Hub（service=${health.service}）")
        }
        if (health.protocol < 1) {
            throw HubException("bad_request", "Hub 协议版本过低：${health.protocol}")
        }
        return health
    }

    fun snapshot(connection: HubConnection): Snapshot {
        val request = authorized(connection, "/v1/snapshot").get().build()
        val body = execute(request, readTimeoutMs = SNAPSHOT_TIMEOUT_MS)
        return decode(body)
    }

    fun command(
        connection: HubConnection,
        deviceId: String,
        on: Boolean? = null,
        brightness: Int? = null,
        run: Boolean? = null,
    ): HubDevice {
        if (on == null && brightness == null && run == null) {
            throw HubException("bad_request", "命令不能为空")
        }
        val payload = buildJsonObject {
            if (on != null) put("on", JsonPrimitive(on))
            if (brightness != null) put("brightness", JsonPrimitive(brightness))
            if (run != null) put("run", JsonPrimitive(run))
        }
        val request = authorized(connection, "/v1/devices/$deviceId/command")
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON))
            .build()
        val body = execute(request, readTimeoutMs = COMMAND_TIMEOUT_MS)
        return decode(body)
    }

    fun mediaCommand(connection: HubConnection, action: String): MediaInfo {
        val payload = buildJsonObject {
            put("media", JsonPrimitive(action))
        }
        val request = authorized(connection, "/v1/devices/media/command")
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON))
            .build()
        val body = execute(request, readTimeoutMs = COMMAND_TIMEOUT_MS)
        return decode(body)
    }

    private fun authorized(connection: HubConnection, path: String): Request.Builder {
        return Request.Builder()
            .url("${connection.baseUrl()}$path")
            .header("Authorization", "Bearer ${connection.token}")
            .header("Accept", "application/json")
    }

    private fun execute(
        request: Request,
        readTimeoutMs: Long,
        expectAuthError: Boolean = true,
    ): String {
        val callClient = http.newBuilder()
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(readTimeoutMs + 1_000, TimeUnit.MILLISECONDS)
            .build()
        val response = try {
            callClient.newCall(request).execute()
        } catch (e: IOException) {
            throw HubNetworkException("连不上 Hub：${e.message ?: "网络错误"}", e)
        }
        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.isSuccessful) return raw
            throw parseError(raw, resp.code, expectAuthError)
        }
    }

    private fun parseError(raw: String, httpStatus: Int, expectAuthError: Boolean): HubException {
        val parsed = runCatching { json.decodeFromString<ErrorBody>(raw) }.getOrNull()
        val code = parsed?.error?.code
            ?: when (httpStatus) {
                401 -> "unauthorized"
                404 -> "not_found"
                409 -> "offline"
                503 -> "login_required"
                502 -> "mijia_error"
                else -> "mijia_error"
            }
        val message = parsed?.error?.message ?: defaultMessage(code, httpStatus)
        if (!expectAuthError && httpStatus == 401) {
            return HubException(code, message, httpStatus)
        }
        return HubException(code, message, httpStatus)
    }

    private inline fun <reified T> decode(raw: String): T {
        return try {
            json.decodeFromString<T>(raw)
        } catch (e: Exception) {
            throw HubException("bad_request", "Hub 返回无法解析：${e.message}")
        }
    }

    companion object {
        const val SNAPSHOT_TIMEOUT_MS = 5_000L
        const val COMMAND_TIMEOUT_MS = 10_000L
        private val JSON = "application/json; charset=utf-8".toMediaType()

        @OptIn(ExperimentalSerializationApi::class)
        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = false
            namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        fun defaultMessage(code: String, httpStatus: Int): String = when (code) {
            "unauthorized" -> "Token 不正确"
            "not_found" -> "设备不存在"
            "offline" -> "设备离线"
            "login_required" -> "请在电脑上扫码登录米家"
            "action_error" -> "无法启动程序"
            "media_error" -> "无法控制播放"
            "unsupported" -> "该设备不支持此操作"
            "bad_request" -> "请求无效"
            else -> "Hub 错误 HTTP $httpStatus"
        }
    }
}
