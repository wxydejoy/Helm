package cn.weiekko.dock.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WeatherClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WeatherClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val root = server.url("/").toString().trimEnd('/')
        client = WeatherClient(
            endpoints = WeatherEndpoints(
                forecast = "$root/forecast",
                geocode = "$root/search",
                wttr = "$root/wttr",
                itboy = "$root/city",
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun lookupShanghaiAndCoordinates() {
        val shanghai = WeatherPlaces.lookup("上海市")
        assertEquals("上海", shanghai?.name)
        assertEquals("101020100", shanghai?.itboyCode)
        val coords = WeatherPlaces.lookup("31.23,121.47")
        assertEquals(31.23, coords?.latitude ?: 0.0, 0.001)
        assertNull(WeatherPlaces.lookup("南"))
        assertEquals("南京", WeatherPlaces.lookup("南京市鼓楼")?.name)
        assertEquals("晴", WeatherPlaces.wmoCondition(0))
        assertEquals("多云", WeatherPlaces.wmoCondition(3))
        assertEquals("雷阵雨", WeatherPlaces.wmoCondition(95))
    }

    @Test
    fun fetchesOpenMeteoForKnownCity() {
        server.enqueue(
            MockResponse().setBody(
                """{"current":{"temperature_2m":26.4,"weather_code":3}}""",
            ),
        )
        val info = client.fetch("上海")
        assertEquals("上海", info.city)
        assertEquals(26.4, info.celsius, 0.01)
        assertEquals("多云", info.condition)
        val request = server.takeRequest()
        assertTrue(request.path.orEmpty().startsWith("/forecast"))
        assertTrue(request.path.orEmpty().contains("latitude=31.23"))
    }

    @Test
    fun fallsBackToWttrWhenForecastFails() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("down"))
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "current_condition":[{"temp_C":"18","lang_zh":[{"value":"小雨"}]}],
                  "nearest_area":[{"areaName":[{"value":"Hangzhou"}]}]
                }
                """.trimIndent(),
            ),
        )
        val info = client.fetch("杭州")
        assertEquals("Hangzhou", info.city)
        assertEquals(18.0, info.celsius, 0.01)
        assertEquals("小雨", info.condition)
        server.takeRequest()
        assertTrue(server.takeRequest().path.orEmpty().startsWith("/wttr/"))
    }

    @Test
    fun fallsBackToItboyWhenOthersFail() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("no"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("no"))
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "cityInfo":{"city":"深圳市"},
                  "data":{"wendu":"29","forecast":[{"type":"晴"}]}
                }
                """.trimIndent(),
            ),
        )
        val info = client.fetch("深圳")
        assertEquals("深圳", info.city)
        assertEquals(29.0, info.celsius, 0.01)
        assertEquals("晴", info.condition)
        server.takeRequest()
        server.takeRequest()
        assertEquals("/city/101280601", server.takeRequest().path)
    }

    @Test
    fun clockLineFormatsCityTempCondition() {
        val line = WeatherInfo("上海", 26.4, "多云").clockLine()
        assertEquals("上海  26°  多云", line)
    }
}
