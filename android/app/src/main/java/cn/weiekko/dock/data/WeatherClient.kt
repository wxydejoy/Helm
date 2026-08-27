package cn.weiekko.dock.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class WeatherException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class WeatherEndpoints(
    val forecast: String = "https://api.open-meteo.com/v1/forecast",
    val geocode: String = "https://geocoding-api.open-meteo.com/v1/search",
    val wttr: String = "https://wttr.in",
    val itboy: String = "http://t.weather.itboy.net/api/weather/city",
)

class WeatherClient(
    private val http: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
    private val endpoints: WeatherEndpoints = WeatherEndpoints(),
) {
    fun fetch(query: String): WeatherInfo {
        val city = query.trim()
        if (city.isEmpty()) throw WeatherException("城市为空")
        val place = resolvePlace(city)
        val errors = mutableListOf<String>()
        if (place != null) {
            runCatching { fetchOpenMeteo(place, city) }
                .onSuccess { return it }
                .onFailure { errors += it.message ?: "open-meteo" }
        }
        runCatching { fetchWttr(city) }
            .onSuccess { return it }
            .onFailure { errors += it.message ?: "wttr" }
        val code = place?.itboyCode
        if (code != null) {
            runCatching { fetchItboy(place, city, code) }
                .onSuccess { return it }
                .onFailure { errors += it.message ?: "itboy" }
        }
        throw WeatherException(errors.firstOrNull()?.ifBlank { null } ?: "天气获取失败")
    }

    private fun resolvePlace(query: String): WeatherPlace? {
        WeatherPlaces.lookup(query)?.let { return it }
        return runCatching { geocode(query) }.getOrNull()
    }

    private fun geocode(query: String): WeatherPlace {
        val url = endpoints.geocode.toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("count", "1")
            .addQueryParameter("language", "zh")
            .addQueryParameter("format", "json")
            .build()
        val body = get(url.toString())
        val parsed = json.decodeFromString(GeoResponse.serializer(), body)
        val item = parsed.results.firstOrNull()
            ?: throw WeatherException("找不到城市：$query")
        val lat = item.latitude ?: throw WeatherException("地理编码没有纬度")
        val lon = item.longitude ?: throw WeatherException("地理编码没有经度")
        val name = item.name?.takeIf { it.isNotBlank() }
            ?: item.admin1?.takeIf { it.isNotBlank() }
            ?: query
        return WeatherPlace(name = name, latitude = lat, longitude = lon)
    }

    private fun fetchOpenMeteo(place: WeatherPlace, query: String): WeatherInfo {
        val url = endpoints.forecast.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", place.latitude.toString())
            .addQueryParameter("longitude", place.longitude.toString())
            .addQueryParameter("current", "temperature_2m,weather_code")
            .addQueryParameter("timezone", "auto")
            .build()
        val body = get(url.toString())
        val parsed = json.decodeFromString(ForecastResponse.serializer(), body)
        val temp = parsed.current?.temperature2m ?: throw WeatherException("Open-Meteo 没有温度")
        val code = parsed.current.weatherCode ?: 3
        return WeatherInfo(
            city = place.name,
            celsius = temp,
            condition = WeatherPlaces.wmoCondition(code),
            query = query,
        )
    }

    private fun fetchWttr(query: String): WeatherInfo {
        val url = endpoints.wttr.toHttpUrl().newBuilder()
            .addPathSegment(query)
            .addQueryParameter("lang", "zh")
            .addQueryParameter("format", "j1")
            .build()
        val body = get(url.toString())
        val root = json.parseToJsonElement(body).jsonObject
        val current = root.arrObj("current_condition")
            ?: throw WeatherException("wttr 没有当前天气")
        val temp = current.str("temp_C")?.toDoubleOrNull()
            ?: throw WeatherException("wttr 没有温度")
        val condition = current.langValue("lang_zh")
            ?: current.langValue("weatherDesc")
            ?: "—"
        val city = root.arrObj("nearest_area")?.langValue("areaName")
            ?: query
        return WeatherInfo(
            city = city,
            celsius = temp,
            condition = condition,
            query = query,
        )
    }

    private fun fetchItboy(place: WeatherPlace, query: String, code: String): WeatherInfo {
        val url = endpoints.itboy.toHttpUrl().newBuilder()
            .addPathSegment(code)
            .build()
        val body = get(url.toString())
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: throw WeatherException("天气接口没有数据")
        val temp = data.str("wendu")?.toDoubleOrNull()
            ?: throw WeatherException("天气接口没有温度")
        val condition = data["forecast"]?.jsonArray?.firstOrNull()?.jsonObject?.str("type")
            ?: "—"
        val city = root["cityInfo"]?.jsonObject?.str("city")
            ?: place.name
        return WeatherInfo(
            city = WeatherPlaces.normalize(city).ifBlank { place.name },
            celsius = temp,
            condition = condition,
            query = query,
        )
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).get().build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw WeatherException("天气网络失败：${e.message ?: "未知错误"}", e)
        }
        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw WeatherException("天气接口 HTTP ${resp.code}")
            }
            if (raw.isBlank()) throw WeatherException("天气接口返回为空")
            return raw
        }
    }

    private fun JsonObject.str(key: String): String? {
        val el = this[key] ?: return null
        return runCatching { el.jsonPrimitive.contentOrNull }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.arrObj(key: String): JsonObject? =
        this[key]?.jsonArray?.firstOrNull()?.jsonObject

    private fun JsonObject.langValue(key: String): String? {
        str(key)?.let { return it }
        return this[key]?.jsonArray?.firstOrNull()?.jsonObject?.str("value")
    }

    @Serializable
    private data class GeoResponse(
        val results: List<GeoItem> = emptyList(),
    )

    @Serializable
    private data class GeoItem(
        val name: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val admin1: String? = null,
    )

    @Serializable
    private data class ForecastResponse(
        val current: Current? = null,
    ) {
        @Serializable
        data class Current(
            @SerialName("temperature_2m") val temperature2m: Double? = null,
            @SerialName("weather_code") val weatherCode: Int? = null,
        )
    }

    companion object {
        const val USER_AGENT = "Shoreting/0.3 (cn.weiekko.dock; weather)"

        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .build(),
                )
            }
            .build()
    }
}
