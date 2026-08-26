package cn.weiekko.dock.data

import java.util.Locale

data class WeatherPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val itboyCode: String? = null,
)

object WeatherPlaces {
    private val COORD = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*[,，]\s*(-?\d+(?:\.\d+)?)\s*$""")

    val cities: List<WeatherPlace> = listOf(
        WeatherPlace("北京", 39.90, 116.41, "101010100"),
        WeatherPlace("上海", 31.23, 121.47, "101020100"),
        WeatherPlace("天津", 39.13, 117.20, "101030100"),
        WeatherPlace("重庆", 29.56, 106.55, "101040100"),
        WeatherPlace("哈尔滨", 45.75, 126.63, "101050101"),
        WeatherPlace("长春", 43.88, 125.32, "101060101"),
        WeatherPlace("沈阳", 41.80, 123.43, "101070101"),
        WeatherPlace("大连", 38.91, 121.61, "101070201"),
        WeatherPlace("呼和浩特", 40.84, 111.75, "101080101"),
        WeatherPlace("石家庄", 38.04, 114.51, "101090101"),
        WeatherPlace("太原", 37.87, 112.55, "101100101"),
        WeatherPlace("西安", 34.26, 108.94, "101110101"),
        WeatherPlace("济南", 36.65, 117.12, "101120101"),
        WeatherPlace("青岛", 36.07, 120.38, "101120201"),
        WeatherPlace("乌鲁木齐", 43.83, 87.62, "101130101"),
        WeatherPlace("拉萨", 29.65, 91.17, "101140101"),
        WeatherPlace("西宁", 36.62, 101.78, "101150101"),
        WeatherPlace("兰州", 36.06, 103.83, "101160101"),
        WeatherPlace("银川", 38.49, 106.23, "101170101"),
        WeatherPlace("郑州", 34.75, 113.65, "101180101"),
        WeatherPlace("南京", 32.06, 118.80, "101190101"),
        WeatherPlace("无锡", 31.49, 120.31, "101190201"),
        WeatherPlace("苏州", 31.30, 120.62, "101190401"),
        WeatherPlace("武汉", 30.59, 114.31, "101200101"),
        WeatherPlace("杭州", 30.27, 120.16, "101210101"),
        WeatherPlace("宁波", 29.87, 121.55, "101210401"),
        WeatherPlace("温州", 28.00, 120.70, "101210701"),
        WeatherPlace("合肥", 31.82, 117.23, "101220101"),
        WeatherPlace("福州", 26.07, 119.30, "101230101"),
        WeatherPlace("厦门", 24.48, 118.09, "101230201"),
        WeatherPlace("南昌", 28.68, 115.86, "101240101"),
        WeatherPlace("长沙", 28.23, 112.94, "101250101"),
        WeatherPlace("贵阳", 26.65, 106.63, "101260101"),
        WeatherPlace("成都", 30.67, 104.07, "101270101"),
        WeatherPlace("广州", 23.13, 113.26, "101280101"),
        WeatherPlace("深圳", 22.54, 114.06, "101280601"),
        WeatherPlace("珠海", 22.27, 113.58, "101280701"),
        WeatherPlace("佛山", 23.02, 113.12, "101280800"),
        WeatherPlace("惠州", 23.11, 114.42, "101280301"),
        WeatherPlace("东莞", 23.04, 113.75, "101281601"),
        WeatherPlace("中山", 22.52, 113.39, "101281701"),
        WeatherPlace("昆明", 25.04, 102.71, "101290101"),
        WeatherPlace("南宁", 22.82, 108.37, "101300101"),
        WeatherPlace("海口", 20.04, 110.32, "101310101"),
        WeatherPlace("香港", 22.32, 114.17),
        WeatherPlace("澳门", 22.20, 113.55),
        WeatherPlace("台北", 25.03, 121.57),
    )

    private val byName: Map<String, WeatherPlace> = cities.associateBy { it.name }

    fun normalize(raw: String): String {
        return raw.trim()
            .replace("　", "")
            .removeSuffix("特别行政区")
            .removeSuffix("地区")
            .removeSuffix("自治州")
            .removeSuffix("市")
            .removeSuffix("省")
            .trim()
    }

    fun parseCoordinates(raw: String): WeatherPlace? {
        val match = COORD.matchEntire(raw) ?: return null
        val lat = match.groupValues[1].toDouble()
        val lon = match.groupValues[2].toDouble()
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return WeatherPlace(
            name = String.format(Locale.US, "%.2f, %.2f", lat, lon),
            latitude = lat,
            longitude = lon,
        )
    }

    fun lookup(raw: String): WeatherPlace? {
        parseCoordinates(raw)?.let { return it }
        val query = normalize(raw)
        if (query.isEmpty()) return null
        byName[query]?.let { return it }
        byName[raw.trim()]?.let { return it }
        if (query.length < 2) return null
        return cities
            .filter { place -> query.startsWith(place.name) || place.name.startsWith(query) }
            .maxByOrNull { it.name.length }
    }

    fun wmoCondition(code: Int): String = when (code) {
        0 -> "晴"
        1, 2 -> "少云"
        3 -> "多云"
        45, 48 -> "雾"
        51, 53, 55, 56, 57 -> "毛毛雨"
        61, 63, 65, 66, 67 -> "雨"
        71, 73, 75, 77 -> "雪"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95 -> "雷阵雨"
        96, 99 -> "雷暴冰雹"
        else -> "阴"
    }
}
