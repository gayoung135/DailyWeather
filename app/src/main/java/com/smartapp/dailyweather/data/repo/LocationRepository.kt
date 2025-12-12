package com.smartapp.dailyweather.data.repo

import android.util.Log
import com.smartapp.dailyweather.data.model.GridPoint
import com.smartapp.dailyweather.data.model.Region
import com.smartapp.dailyweather.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

class LocationRepository {

    /**
     * 위경도 → KMA 격자 변환 (텍스트 응답 파싱)
     * 응답 예:
     * #START7777
     * #       LON,         LAT,   X,   Y
     *  127.500000,   36.500000,  69, 104
     */
    suspend fun convertLatLngToGrid(longitude: Double, latitude: Double): GridPoint =
        withContext(Dispatchers.IO) {
            val body = RetrofitClient.kmaConvertApi.convertLonLatToGrid(
                lon = longitude,
                lat = latitude
            )

            // 1) 우선 서버가 준 charset 시도 → 실패/없으면 UTF-8 → 실패 기미(깨짐)면 EUC-KR 재시도
            val raw = runCatching {
                val source = body.source()
                val contentType = body.contentType()
                val cs = contentType?.charset() ?: Charsets.UTF_8
                source.request(Long.MAX_VALUE)
                source.buffer.clone().readString(cs)
            }.getOrElse {
                // 최후의 보루: 바이트 직접 읽고 EUC-KR로 변환
                val bytes = body.bytes()
                String(bytes, charset("EUC-KR"))
            }

            Log.e("KMA", "<<< KMA raw >>>\n${raw.take(800)}\n<<< /KMA raw >>>")
            return@withContext parseGridFromText(raw)
        }

    private fun parseGridFromText(text: String): GridPoint {
        // 숫자 줄만 잡아내는 멀티라인 정규식 (공백/탭/CRLF 견고)
        val rowRegex = Regex(
            pattern = """^\s*([-+]?\d+(?:\.\d+)?)\s*,\s*([-+]?\d+(?:\.\d+)?)\s*,\s*(\d+)\s*,\s*(\d+)\s*$""",
            options = setOf(RegexOption.MULTILINE)
        )

        // 먼저 데이터 라인 탐색
        rowRegex.find(text)?.let { m ->
            val x = m.groupValues[3].toInt()
            val y = m.groupValues[4].toInt()
            return GridPoint(x, y)
        }

        // 못 찾으면, 안내문(한글) 추출해서 에러로 올려 사용자에게 보여주기
        val cleaned = text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString(" ")

        Log.e("KMA", "cleaned=$cleaned")

        throw IllegalStateException("격자 변환 응답 파싱 실패: $cleaned")
    }

    /**
     * Region enum 기반으로
     *  - (lat, lon)에 가장 가까운 (시/도, 시/군/구)를 추정
     *  - threshold보다 너무 멀면 null 반환
     *
     * @return Pair<regionName(시/도), subRegionName(시/군/구)> 또는 null
     */
    fun guessRegionFromLatLng(
        lat: Double,
        lon: Double
    ): Pair<String, String>? {
        var bestRegion: String? = null
        var bestSub: String? = null
        var bestDist = Double.MAX_VALUE

        for (region in Region.entries) {
            if (region == Region.SELECT) continue

            for (sub in region.subRegions) {
                val dist = (lat - sub.latitude).pow(2) + (lon - sub.longitude).pow(2)
                if (dist < bestDist) {
                    bestDist = dist
                    bestRegion = region.displayName
                    bestSub = sub.name
                }
            }
        }

        // threshold: 대략 위도/경도 0.5도 이내 (루트(0.25) ≈ 0.5)
        return if (bestRegion != null && bestSub != null && bestDist < 0.25) {
            bestRegion!! to bestSub!!
        } else {
            null
        }
    }

}