package com.smartapp.dailyweather.data.mapper
import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/*
 * style.json -> 메모리 DTO 파서
 * - JsonParser 사용
 */

//싱글톤 오브젝트
object JsonToDto {

    // DTO 정의 구간
    // style.json 전체를 메모리에 옮겨 담는 최상위 DTO
    data class StyleJsonDto(
        val enum: EnumsDto,
        val items: List<ItemDto>,
        val temperature: Map<String, Map<String, List<String>>>,
        val purpose: Map<String, Map<String, List<String>>>,
        val style: Map<String, Map<String, List<String>>>,
        val moodColor: Map<String, List<MoodColorDto>>,
        //특수 규칙 (미세먼지/비) 묶음
        val specialWeatherRules: SpecialRulesDto
    )
    // enum 을 담는 DTO, 각 리스트가 json의 배열을 그래도 담음
    data class EnumsDto(
        val category: List<String>,
        val tempRange: List<String>,
        val purpose: List<String>,
        val likeStyle: List<String>,
        val airQuality: List<String>,
        val rainLevel: List<String>,
    )

    data class ItemDto(
        val id: String,
        val kname: String,
        val cate: String
    )

    data class MoodColorDto(
        val cname: String,
        val hex: String,
        val rgb: List<Int>
    )

    data class SpecialRulesDto(
        val airToMask: Map<String, String?>,
        val rainToUmbrella: Map<String, String?>
    )

    // 파싱 진입점 (파싱을 시작하는 함수들)
    // assets에서 파일을 읽어 StyleJsonDto로 반환하는 진입 함수
    fun parseFromAssets(context: Context, assetFile: String = "style.json"): StyleJsonDto {
        val text = context.assets.open(assetFile).bufferedReader().use { it.readText() }
        return parseFromString(text) //문자열 파서를 재사용하기 위해 parseFromString에 위임
    }

    // 이미 문자열을 가진 경우 사용할 파서, 테스트/시뮬레이션에 유용
    fun parseFromString(jsonText: String): StyleJsonDto {
        val root = JsonParser.parseString(jsonText).asJsonObject

        val enums = parseEnums(root.getAsJsonObject("enum"))
        //map으로 리스트 생성
        val items = root.getAsJsonArray("items").map { el ->
            val o = el.asJsonObject
            ItemDto(
                id = o.get("id").asString,
                kname = o.get("kname").asString,
                cate = o.get("cate").asString
            )
        }
       // temperature 섹션을 2단계 맵 구조로 파싱
        val temperature = parseNestedMap(root.getAsJsonObject("temperature"))
        val purpose = parseNestedMap(root.getAsJsonObject("purpose"))
        val style = parseNestedMap(root.getAsJsonObject("style"))

        val moodColorRoot = root.getAsJsonObject("mood_color")
        val moodColor = buildMap<String, List<MoodColorDto>> {
            for ((moodCode, arr) in moodColorRoot.entrySet()) {
                val list = arr.asJsonArray.map { el ->
                    val o = el.asJsonObject
                    MoodColorDto(
                        cname = o.get("cname").asString,
                        hex = o.get("hex").asString,
                        rgb = o.getAsJsonArray("rgb").map { it.asInt }
                    )
                }
                put(moodCode, list)
            }
        }

        val special = root.getAsJsonObject("special_weather_rules")
        val airToMask = special.getAsJsonObject("air_to_mask").entrySet().associate { (k, v) ->
            k to (if (v.isJsonNull) null else v.asString)
        }
        val rainToUmbrella = special.getAsJsonObject("rain_to_umbrella").entrySet().associate { (k, v) ->
            k to (if (v.isJsonNull) null else v.asString)
        }

        //지금까지 만든 모든 파싱 결과를 최상위 DTO로 묶어 반환
        return StyleJsonDto(
            enum = enums,
            items = items,
            temperature = temperature,
            purpose = purpose,
            style = style,
            moodColor = moodColor,
            specialWeatherRules = SpecialRulesDto(
                airToMask = airToMask,
                rainToUmbrella = rainToUmbrella
            )
        )
    }

    // 내부 도움 함수
    //JSON 구조가 복잡해서 반복되는 파싱 로직 분리가 필요

    private fun parseEnums(enumObj: JsonObject): EnumsDto {
        fun arr(key: String) = enumObj.getAsJsonArray(key).map { it.asString }
        return EnumsDto(
            category = arr("Category"),
            tempRange = arr("Temp_range"),
            purpose = arr("Purpose"),
            likeStyle = arr("Like_Style"),
            airQuality = arr("Air_Quality"),
            rainLevel = arr("Rain_Level"),
        )
    }

    /*
     * JsonObject(예: "temperature") → Map<String, Map<String, List<String>>>
     *   1단계 키: "-30~4" 같은 구간 or "office" 같은 코드
     *   2단계 키: "OUTER" / "TOP" / "BOTTOM" / "ACC"
     *   3단계 : 내부 배열을 리스트로 변환
     */
    private fun parseNestedMap(obj: JsonObject): Map<String, Map<String, List<String>>> {
        val out = mutableMapOf<String, Map<String, List<String>>>()
        for ((k, v) in obj.entrySet()) {
            val inner = mutableMapOf<String, List<String>>()
            for ((cate, arr) in v.asJsonObject.entrySet()) {
                inner[cate] = arr.asJsonArray.map { it.asString }
            }
            out[k] = inner
        }
        return out
    }
}
