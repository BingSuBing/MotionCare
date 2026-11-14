package com.carfortable

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// 휴식처 (휴게소·졸음쉼터)
data class RestStop(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val distanceM: Int     // 현재 위치에서 직선거리
)

enum class RestSource { KAKAO, CACHE, NONE }

data class RestSearchResult(
    val stops: List<RestStop>,
    val source: RestSource,
    val error: String? = null
)

// 1순위: 카카오 로컬 키워드 검색 (현재 위치 20km 안)
// 실패하면: 마지막으로 성공한 검색 결과(이 기기에 저장)를 거리만 다시 계산해서 보여줘요
object RestStopFinder {

    private const val TAG = "MotionCare"
    private const val PREFS = "rest_stop_cache"
    private const val KEY_CACHE = "stops"
    private const val RADIUS_M = 20_000      // 카카오 최대 반경
    private const val MAX_RESULTS = 6
    private val KEYWORDS = listOf("휴게소", "졸음쉼터")

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun search(context: Context, lat: Double, lng: Double, onResult: (RestSearchResult) -> Unit) {
        val app = context.applicationContext
        io.execute {
            val result = try {
                val stops = searchKakao(lat, lng)
                if (stops.isNotEmpty()) saveCache(app, stops)
                RestSearchResult(stops, RestSource.KAKAO)
            } catch (t: Throwable) {
                Log.w(TAG, "카카오 휴식처 검색 실패", t)
                val cached = loadCache(app)
                    .map { it.copy(distanceM = distanceM(lat, lng, it.lat, it.lng)) }
                    .sortedBy { it.distanceM }
                    .take(MAX_RESULTS)
                if (cached.isNotEmpty()) {
                    RestSearchResult(cached, RestSource.CACHE, t.message)
                } else {
                    RestSearchResult(emptyList(), RestSource.NONE, t.message)
                }
            }
            main.post { onResult(result) }
        }
    }

    private fun searchKakao(lat: Double, lng: Double): List<RestStop> {
        val key = BuildConfig.KAKAO_REST_KEY
        require(key.isNotBlank()) { "카카오 REST API 키가 없어요 (local.properties의 kakaoRestKey)" }

        val found = linkedMapOf<String, RestStop>()
        for (keyword in KEYWORDS) {
            val url = "https://dapi.kakao.com/v2/local/search/keyword.json" +
                    "?query=" + URLEncoder.encode(keyword, "UTF-8") +
                    "&x=$lng&y=$lat&radius=$RADIUS_M&sort=distance&size=15"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "KakaoAK $key")
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            try {
                val code = conn.responseCode
                if (code != 200) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("카카오 응답 $code $err")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val docs = JSONObject(body).getJSONArray("documents")
                for (i in 0 until docs.length()) {
                    val d = docs.getJSONObject(i)
                    val name = d.optString("place_name")
                    val category = d.optString("category_name")
                    // 휴게소·졸음쉼터만 (휴게소 안 식당·주유소 같은 건 빼요)
                    val isRest = name.endsWith("휴게소") || name.contains("졸음쉼터") ||
                            category.endsWith("휴게소") || category.endsWith("졸음쉼터")
                    if (!isRest) continue
                    // 택시·화물차 전용 쉼터는 빼요 (일반 탑승자가 못 씀)
                    if (name.contains("택시") || name.contains("화물")) continue
                    val stopLat = d.optString("y").toDoubleOrNull() ?: continue
                    val stopLng = d.optString("x").toDoubleOrNull() ?: continue
                    val id = d.optString("id")
                    found[id] = RestStop(
                        id = id,
                        name = name,
                        address = d.optString("road_address_name").ifBlank { d.optString("address_name") },
                        lat = stopLat,
                        lng = stopLng,
                        distanceM = d.optString("distance").toIntOrNull()
                            ?: distanceM(lat, lng, stopLat, stopLng)
                    )
                }
            } finally {
                conn.disconnect()
            }
        }
        return found.values.sortedBy { it.distanceM }.take(MAX_RESULTS)
    }

    private fun saveCache(context: Context, stops: List<RestStop>) {
        val arr = JSONArray()
        stops.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("address", s.address)
                put("lat", s.lat); put("lng", s.lng)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CACHE, arr.toString()).apply()
    }

    private fun loadCache(context: Context): List<RestStop> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CACHE, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RestStop(o.getString("id"), o.getString("name"), o.optString("address"),
                    o.getDouble("lat"), o.getDouble("lng"), 0)
            }
        }.getOrDefault(emptyList())
    }

    // 두 좌표 사이 직선거리 (m)
    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return (r * 2 * atan2(sqrt(a), sqrt(1 - a))).roundToInt()
    }
}