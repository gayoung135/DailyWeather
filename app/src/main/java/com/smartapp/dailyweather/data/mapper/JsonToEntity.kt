package com.smartapp.dailyweather.data.mapper

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.smartapp.dailyweather.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.smartapp.dailyweather.data.local.dao.*
import com.smartapp.dailyweather.data.local.entity.*

/**
 * style.json -> Room DB 시더
 * Gson JsonParser 사용
 * FK를 위해 코드→ID 맵 생성 후 관계 테이블 생성
 * clearBefore=true면 전체 테이블 비우고 재시드
 */
object JsonToEntity {

    //suspend : 코루틴에서 호출
    suspend fun seedFromAssets(
        context: Context,
        db: AppDatabase,
        assetFile: String = "style.json",
        clearBefore: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val jsonText = context.assets.open(assetFile).bufferedReader().use { it.readText() }
        val root = JsonParser.parseString(jsonText).asJsonObject

        // 이 블록 안의 모든 DB 작업을 하나의 트랜잭션으로 묶음
        db.withTransaction {
            if (clearBefore) {
                db.clearAllTables()
            }

            // 기본 코드 테이블 (카테고리/아이템/온도/목적/스타일/공기질/비/무드/무드색) 삽입
            val codeMaps = insertBaseTables(db, root)

            // 관계(추천 규칙) 삽입: 온도/목적/스타일
            // 관계 삽입 시 FK용으로 쓸 코드 -> ID 맵을 받아옴
           // 세 가지 관계 테이블(ItemToTemp, ItemToPurpose, ItemToStyle)을 채움.
            insertRelationsByTemperature(db, root, codeMaps)
            insertRelationsByPurpose(db, root, codeMaps)
            insertRelationsByStyle(db, root, codeMaps)

            //  특수 규칙 (미세먼지→마스크, 강수→우산)
            insertSpecialRules(db, root, codeMaps)
        }
    }

    // 내부 도움 함수들

    private data class CodeMaps(
        val categoryCodeToId: Map<String, Int>,
        val itemIdToId: Map<String, String>, // item.id 자체가 PK(문자열)
        val tempLabelToId: Map<String, Int>, // "min~max" 라벨 -> temp_id
        val purposeCodeToId: Map<String, Int>,
        val styleCodeToId: Map<String, Int>,
        val airCodeToId: Map<String, Int>,
        val rainCodeToId: Map<String, Int>,
        val moodCodeToId: Map<String, Int>
    )

    //확장함수 : 문자열을 trim 후 대문자로, JSON의 카테고리 표기를 정규화 할 때 사용
    private fun String.trimUpper(): String = trim().uppercase()

    private suspend fun insertBaseTables(db: AppDatabase, root: JsonObject): CodeMaps {
        val enums = root.getAsJsonObject("enum")

        // 1) Category
        val categoryCodes = enums.getAsJsonArray("Category").map { it.asString.trimUpper() }
        db.categoryDao().upsertAll(categoryCodes.map { CategoryEntity(categoryCode = it) })
        val categories = db.categoryDao().getAll()
        val categoryCodeToId = categories.associate { it.categoryCode to it.id }

        // 2) Items
        val itemsJson = root.getAsJsonArray("items")
        val itemEntities = itemsJson.map { je ->
            val o = je.asJsonObject
            val id = o.get("id").asString
            val kname = o.get("kname").asString
            val cateCode = o.get("cate").asString.trimUpper()
            val categoryId = categoryCodeToId[cateCode]
                ?: error("items에 존재하지 않는 카테고리 코드 발견: $cateCode")
            ItemEntity(id = id, kname = kname, categoryId = categoryId)
        }
        db.itemDao().upsertAll(itemEntities)
        val itemIdToId = itemEntities.associate { it.id to it.id }

        // 3) Temp ranges
        val tempLabels = enums.getAsJsonArray("Temp_range").map { it.asString }
        val tempEntities = tempLabels.map { label ->
            val (minT, maxT) = parseRange(label)
            TempRangeEntity(minTemp = minT, maxTemp = maxT)
        }
        db.tempRangeDao().upsertAll(tempEntities)
        val persistedTemps = db.tempRangeDao().getAll()
        val tempLabelToId = persistedTemps.associate { "${it.minTemp}~${it.maxTemp}" to it.tempId }

        // 4) Purpose
        val purposeCodes = enums.getAsJsonArray("Purpose").map { it.asString }
        db.purposeDao().upsertAll(purposeCodes.map { PurposeEntity(purposeCode = it) })
        val purposeCodeToId = purposeCodes.associateWith { code ->
            db.purposeDao().getByCode(code)?.purposeId
                ?: error("목적 코드가 디비에 저장x: $code")
        }

        // 5) Style
        val styleCodes = enums.getAsJsonArray("Like_Style").map { it.asString }
        db.styleDao().upsertAll(styleCodes.map { StyleEntity(styleCode = it) })
        val styleCodeToId = styleCodes.associateWith { code ->
            db.styleDao().getByCode(code)?.styleId
                ?: error("스타일 코드가 디비에 저장x: $code")
        }

        // 6) Air Quality
        val airCodes = enums.getAsJsonArray("Air_Quality").map { it.asString }
        db.airQualityDao().upsertAll(airCodes.map { AirQualityEntity(airQualityCode = it) })
        val airCodeToId = airCodes.associateWith { code ->
            db.airQualityDao().getByCode(code)?.airQualityId
                ?: error("미세먼지가 디비에 저장x: $code")
        }

        // 7) Rain Level
        val rainCodes = enums.getAsJsonArray("Rain_Level").map { it.asString }
        db.rainLevelDao().upsertAll(rainCodes.map { RainLevelEntity(rainLevelCode = it) })
        val rainCodeToId = rainCodes.associateWith { code ->
            db.rainLevelDao().getByCode(code)?.rainLevelId
                ?: error("강수 상태가 디비에 저장x: $code")
        }

        // 8) Mood + MoodColor
        // mood는 JSON의 mood_color 키 집합(good/normal/bad)을 그대로 사용
        val moodRoot = root.getAsJsonObject("mood_color")
        val moodCodes = moodRoot.entrySet().map { it.key }
        db.MoodDao().upsertAll(moodCodes.map { MoodEntity(moodCode = it) })
        val moodCodeToId = moodCodes.associateWith { code ->
            db.MoodDao().getByCode(code)?.moodId
                ?: error("기분 상태가 디비에 저장x: $code")
        }
        // mood_color 행들 삽입
        val moodColorEntities = buildList {
            for ((moodCode, arr) in moodRoot.entrySet()) {
                val moodId = moodCodeToId[moodCode] ?: error("Mood id not found for $moodCode")
                arr.asJsonArray.forEach { el ->
                    val o = el.asJsonObject
                    val cname = o.get("cname").asString
                    val hex = o.get("hex").asString
                    add(MoodColorEntity(moodId = moodId, cname = cname, hex = hex))
                }
            }
        }
        if (moodColorEntities.isNotEmpty()) {
            db.MoodColorDao().upsertAll(moodColorEntities)
        }

        return CodeMaps(
            categoryCodeToId = categoryCodeToId,
            itemIdToId = itemIdToId,
            tempLabelToId = tempLabelToId,
            purposeCodeToId = purposeCodeToId,
            styleCodeToId = styleCodeToId,
            airCodeToId = airCodeToId,
            rainCodeToId = rainCodeToId,
            moodCodeToId = moodCodeToId
        )
    }

    private fun parseRange(label: String): Pair<Int, Int> {
        // "-30~4" 형태
        val parts = label.split("~")
        require(parts.size == 2) { "온도 범위 형식이 잘못됨: $label" }
        val minT = parts[0].trim().toInt()
        val maxT = parts[1].trim().toInt()
        return minT to maxT
    }

    private suspend fun insertRelationsByTemperature(
        db: AppDatabase,
        root: JsonObject,
        maps: CodeMaps
    ) {
        val tempRoot = root.getAsJsonObject("temperature") ?: return
        val itemToTemp = mutableListOf<ItemToTempEntity>()

        for ((rangeLabel, cateObj) in tempRoot.entrySet()) {
            val tempId = maps.tempLabelToId[rangeToKey(rangeLabel)]
                ?: error("온도 범위를 찾지 못함: $rangeLabel")

            // cateObj: {"OUTER":[id...], "TOP":[...], ...}
            for ((cateCodeRaw, arr) in cateObj.asJsonObject.entrySet()) {
                val cateCode = cateCodeRaw.trimUpper()
                val categoryId = maps.categoryCodeToId[cateCode]
                    ?: error("items에 존재하지 않는 카테고리 코드: $cateCode")
                arr.asJsonArray.forEach { idEl ->
                    val itemId = maps.itemIdToId[idEl.asString]
                        ?: error("온도에 존재하지 않는 아이템 ID: ${idEl.asString}")
                    itemToTemp += ItemToTempEntity(
                        tempId = tempId,
                        categoryId = categoryId,
                        itemId = itemId
                    )
                }
            }
        }
        if (itemToTemp.isNotEmpty()) {
            db.itemToTempDao().upsertAll(itemToTemp)
        }
    }

    private fun rangeToKey(label: String): String {
        // 입력 라벨이 " -30~4 " 등일 수 있으니 정규화
        val (a, b) = parseRange(label)
        return "$a~$b"
    }

    private suspend fun insertRelationsByPurpose(
        db: AppDatabase,
        root: JsonObject,
        maps: CodeMaps
    ) {
        val purposeRoot = root.getAsJsonObject("purpose") ?: return
        val itemToPurpose = mutableListOf<ItemToPurposeEntity>()

        for ((purposeCode, cateObj) in purposeRoot.entrySet()) {
            val purposeId = maps.purposeCodeToId[purposeCode]
                ?: error("목적 코드가 존재X: $purposeCode")

            for ((cateCodeRaw, arr) in cateObj.asJsonObject.entrySet()) {
                val cateCode = cateCodeRaw.trimUpper()
                val categoryId = maps.categoryCodeToId[cateCode]
                    ?: error("purpose에 잘못됨 카테고리 코드 포함: $cateCode")
                arr.asJsonArray.forEach { idEl ->
                    val itemId = maps.itemIdToId[idEl.asString]
                        ?: error("purpose에 존재하지 않는 아이템id: ${idEl.asString}")
                    itemToPurpose += ItemToPurposeEntity(
                        purposeId = purposeId,
                        categoryId = categoryId,
                        itemId = itemId
                    )
                }
            }
        }
        if (itemToPurpose.isNotEmpty()) {
            db.itemToPurposeDao().upsertAll(itemToPurpose)
        }
    }

    private suspend fun insertRelationsByStyle(
        db: AppDatabase,
        root: JsonObject,
        maps: CodeMaps
    ) {
        val styleRoot = root.getAsJsonObject("style") ?: return
        val itemToStyle = mutableListOf<ItemToStyleEntity>()

        for ((styleCode, cateObj) in styleRoot.entrySet()) {
            val styleId = maps.styleCodeToId[styleCode]
                ?: error("존재하지 않는 스타일코드: $styleCode")

            for ((cateCodeRaw, arr) in cateObj.asJsonObject.entrySet()) {
                val cateCode = cateCodeRaw.trimUpper()
                val categoryId = maps.categoryCodeToId[cateCode]
                    ?: error("스타일에 잘못된 카테고리 코드가 포함: $cateCode")
                arr.asJsonArray.forEach { idEl ->
                    val itemId = maps.itemIdToId[idEl.asString]
                        ?: error("스타일에 존재하지 않는 아이템 id: ${idEl.asString}")
                    itemToStyle += ItemToStyleEntity(
                        styleId = styleId,
                        categoryId = categoryId,
                        itemId = itemId
                    )
                }
            }
        }
        if (itemToStyle.isNotEmpty()) {
            db.itemToStyleDao().upsertAll(itemToStyle)
        }
    }

    private suspend fun insertSpecialRules(
        db: AppDatabase,
        root: JsonObject,
        maps: CodeMaps
    ) {
        val special = root.getAsJsonObject("special_weather_rules") ?: return

        // air_to_mask
        val air = special.getAsJsonObject("air_to_mask")
        val airEntities = mutableListOf<NecessityRuleAirEntity>()
        for ((airCode, itemOrNull) in air.entrySet()) {
            if (itemOrNull.isJsonNull) continue
            val airId = maps.airCodeToId[airCode] ?: error("air_quality 코드가 db에 저장x: $airCode")
            val itemId = maps.itemIdToId[itemOrNull.asString]
                ?: error("air_to_mask에서 존재하지 않는 아이템id: ${itemOrNull.asString}")
            airEntities += NecessityRuleAirEntity(airQualityId = airId, itemId = itemId)
        }
        if (airEntities.isNotEmpty()) {
            db.necessityRuleAirDao().upsertAll(airEntities)
        }

        // rain_to_umbrella
        val rain = special.getAsJsonObject("rain_to_umbrella")
        val rainEntities = mutableListOf<NecessityRuleRainEntity>()
        for ((rainCode, itemOrNull) in rain.entrySet()) {
            if (itemOrNull.isJsonNull) continue
            val rainId = maps.rainCodeToId[rainCode] ?: error("존재하지 않는 강수 코드: $rainCode")
            val itemId = maps.itemIdToId[itemOrNull.asString]
                ?: error("rain_to_um..에서 존재하지 않는 아이템 id: ${itemOrNull.asString}")
            rainEntities += NecessityRuleRainEntity(rainLevelId = rainId, itemId = itemId)
        }
        if (rainEntities.isNotEmpty()) {
            db.necessityRuleRainDao().upsertAll(rainEntities)
        }
    }
}
