package com.smartapp.dailyweather.data.repo

import android.util.Log
import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.core.TimeProvider
import com.smartapp.dailyweather.core.kstDateTimeString
import com.smartapp.dailyweather.core.parseKstDateTime
import com.smartapp.dailyweather.data.local.AppDatabase
import com.smartapp.dailyweather.data.local.entity.CoordinationItemEntity
import com.smartapp.dailyweather.data.local.entity.CoordinationSetEntity
import com.smartapp.dailyweather.data.local.entity.LastUserStateEntity
import com.smartapp.dailyweather.data.model.GridPoint

object CategoryIds {
    const val OUTER = 1
    const val TOP = 2
    const val BOTTOM = 3
    const val ACC = 4
    const val NECESSITY = 5
}

/**
 * temp_id / purpose_id / style_id + category_id
 *  → item_id 문자열로 코디 2세트 생성 + DB 저장
 */
class RecommendationRepository(
    private val db: AppDatabase,
    private val lastUserStateRepository: LastUserStateRepository,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val weatherRepository: WeatherRepository = WeatherRepository(timeProvider)
) {
    private val itemToTempDao = db.itemToTempDao()
    private val itemToPurposeDao = db.itemToPurposeDao()
    private val itemToStyleDao = db.itemToStyleDao()

    private val airQualityDao = db.airQualityDao()
    private val necessityRuleAirDao = db.necessityRuleAirDao()
    private val rainLevelDao = db.rainLevelDao()
    private val necessityRuleRainDao = db.necessityRuleRainDao()
    private val moodDao = db.MoodDao()
    private val itemDao = db.itemDao()

    private val coordinationSetDao = db.coordinationSetDao()
    private val coordinationItemDao = db.coordinationItemDao()

    /**
     * 최근 LastUserState를 기준으로 추천 2세트를 생성하고,
     * coordination_set / coordination_item 테이블에 저장한다.
     *
     * @param airQualityCode24h
     *  - PM2.5 24시간 평균 기준 코드 (good / normal / bad / hazard)
     *  - null 이면 대기질 기반 마스크 추천은 생략
     *
     * @param moodCode 사용자가 선택한 mood_code (good/normal/bad)
     */
    suspend fun createRecommendationForLatestState(
        airQualityCode24h: String?,
        moodCode: String?
    ): RecommendationResult {

        val state: LastUserStateEntity = lastUserStateRepository.getLatestState()
            ?: error("최근 LastUserState 데이터가 없습니다.")

        // mood_code → mood_id
        val moodId: Int? = getMoodIdOrNull(moodCode)

        val targetTimeKst = timeProvider.parseKstDateTime(state.lastAccessTime)
        Log.d("RecommendRepo", "추천 기준 시간(KST, TMP/POP 공통) = $targetTimeKst")

        val grid = GridPoint(state.x, state.y)

        val tempId = state.tempId
        val purposeId = state.purposeId
        val styleId = state.styleId

        /* ① 대기질 → 마스크 NECESSITY (PM2.5 24시간 평균 기준 코드 사용) */
        val airNecessityItemIds =
            if (airQualityCode24h != null) {
                getNecessityItemIdsByAir(airQualityCode24h)
            } else {
                Log.w("RecommendRepo", "airQualityCode24h=null → 마스크 NECESSITY 생략")
                emptyList()
            }
        Log.d("RecommendRepo", "air necessity items = $airNecessityItemIds")

        /* ② POP(강수확률) → 우산 NECESSITY */
        Log.d("RecommendRepo", "POP 조회 대상 시간(KST) = $targetTimeKst")
        val popPercent: Int? = try {
            weatherRepository.getPopAt(grid, targetTimeKst)
        } catch (e: Exception) {
            Log.e("RecommendRepo", "POP 조회 실패: ${e.message}", e)
            null
        }
        Log.d("RecommendRepo", "POP raw = $popPercent")

        val rainCode = mapPopToRainCode(popPercent)
        Log.d(
            "RecommendRepo",
            "POP 기반 rainCode = $rainCode, rawPop=${popPercent ?: -1}%"
        )

        val rainNecessityItemIds =
            if (rainCode == "none") emptyList()
            else getNecessityItemIdsByRain(rainCode)

        Log.d("RecommendRepo", "rain necessity items = $rainNecessityItemIds")

        /* ③ 마스크 + 우산 NECESSITY 합치기 */
        val necessityItemIds =
            (airNecessityItemIds + rainNecessityItemIds).distinct()

        Log.d("RecommendRepo", "최종 NECESSITY items = $necessityItemIds")

        /* ④ 코디 1세트 */
        val firstSet = pickCoordinationSet(
            tempId = tempId,
            purposeId = purposeId,
            styleId = styleId,
            excludeItems = emptySet(),
            necessityItemIds = necessityItemIds
        )

        /* ⑤ 코디 2세트 (1세트에서 사용된 의류 제외) */
        val usedClothIds = firstSet.items
            .filter { it.categoryId != CategoryIds.NECESSITY }
            .map { it.itemId }
            .toSet()

        val secondSet = pickCoordinationSet(
            tempId = tempId,
            purposeId = purposeId,
            styleId = styleId,
            excludeItems = usedClothIds,
            necessityItemIds = necessityItemIds
        )

        val result = RecommendationResult(
            firstSet = firstSet,
            secondSet = secondSet
        )

        // moodId 포함해서 DB에 저장
        saveCoordinationSetsToDb(
            lastUserStateId = state.id,
            recommendation = result,
            moodId = moodId
        )

        return result
    }

    /**
     * POP(강수확률 %) → rain_level_code (none / low / medium / high)
     */
    private fun mapPopToRainCode(popPercent: Int?): String {
        if (popPercent == null) {
            Log.w("RecommendRepo", "POP=null → rain_level_code=none")
            return "none"
        }

        val code = when {
            popPercent < 20 -> "none"
            popPercent < 40 -> "low"
            popPercent < 70 -> "medium"
            else -> "high"
        }

        Log.d("RecommendRepo", "POP=$popPercent% → rain_level_code=$code")
        return code
    }

    private suspend fun getNecessityItemIdsByRain(rainCode: String): List<String> {
        val rainEntity = rainLevelDao.getByCode(rainCode)
        if (rainEntity == null) {
            Log.w("RecommendRepo", "rainLevelCode=$rainCode 를 DB에서 찾을 수 없습니다.")
            return emptyList()
        }

        val list = necessityRuleRainDao.findUmbrellaByRain(rainEntity.rainLevelId)
        Log.d("RecommendRepo", "rainCode=$rainCode → umbrella items=$list")
        return list
    }

    private suspend fun getNecessityItemIdsByAir(airCode: String): List<String> {
        val airEntity = airQualityDao.getByCode(airCode)
        if (airEntity == null) {
            Log.w("RecommendRepo", "airQualityCode=$airCode 를 DB에서 찾을 수 없습니다.")
            return emptyList()
        }

        val list = necessityRuleAirDao.findMaskByAir(airEntity.airQualityId)
        Log.d("RecommendRepo", "airCode=$airCode → mask items=$list")
        return list
    }

    private suspend fun pickCoordinationSet(
        tempId: Int,
        purposeId: Int?,
        styleId: Int?,
        excludeItems: Set<String>,
        necessityItemIds: List<String>
    ): CoordinationSetDomain {

        // TOP
        val topId = pickOneItemForCategory(
            categoryId = CategoryIds.TOP,
            tempId = tempId,
            purposeId = purposeId,
            styleId = styleId,
            excludeItems = excludeItems
        )

        // BOTTOM (상의와 겹치지 않게 제외 목록 추가)
        val bottomExclude = excludeItems + setOfNotNull(topId)
        val bottomId = pickOneItemForCategory(
            categoryId = CategoryIds.BOTTOM,
            tempId = tempId,
            purposeId = purposeId,
            styleId = styleId,
            excludeItems = bottomExclude
        )

        // OUTER (없어도 됨)
        val outerExclude = bottomExclude + setOfNotNull(bottomId)
        val outerId = pickOneItemForCategory(
            categoryId = CategoryIds.OUTER,
            tempId = tempId,
            purposeId = purposeId,
            styleId = styleId,
            excludeItems = outerExclude,
            allowEmpty = true
        )

        // ACC(악세사리, 없어도 됨)
        val accExclude = outerExclude + setOfNotNull(outerId)
        val accId = pickOneItemForCategory(
            categoryId = CategoryIds.ACC,
            tempId = tempId,
            purposeId = purposeId,
            styleId = styleId,
            excludeItems = accExclude,
            allowEmpty = true
        )

        // 의류 + 악세사리 (NECESSITY 제외)
        val clothIds = listOfNotNull(topId, bottomId, outerId, accId)

        // 여기서 necessity(마스크+우산 등) item 추가
        val allItemIds = clothIds + necessityItemIds

        val domainItems = allItemIds.map { itemId ->
            val entity = itemDao.getById(itemId)

            CoordinationItemDomain(
                itemId = itemId,
                displayName = entity?.kname ?: itemId,
                categoryId = entity?.categoryId ?: CategoryIds.NECESSITY
            )
        }

        // 디버깅용 로그
        Log.d("RecommendRepo", "pickCoordinationSet 결과 → cloth=$clothIds, necessity=$necessityItemIds")

        return CoordinationSetDomain(items = domainItems)
    }

    private suspend fun pickOneItemForCategory(
        categoryId: Int,
        tempId: Int,
        purposeId: Int?,
        styleId: Int?,
        excludeItems: Set<String>,
        allowEmpty: Boolean = false
    ): String? {

        // 0. 온도 기준 전체 후보
        val tempItems = itemToTempDao.findItems(tempId, categoryId)
            .filterNot { it in excludeItems }
            .toSet()

        if (tempItems.isEmpty()) {
            return if (allowEmpty) null else null
        }

        var candidates: Set<String> = emptySet()

        // 1) 온도 ∩ 목적 ∩ 스타일
        if (purposeId != null && styleId != null) {
            val purposeItems = itemToPurposeDao.findItems(purposeId, categoryId)
                .filterNot { it in excludeItems }
                .toSet()

            val styleItems = itemToStyleDao.findItems(styleId, categoryId)
                .filterNot { it in excludeItems }
                .toSet()

            candidates = tempItems intersect purposeItems intersect styleItems
        }

        // 2) 온도 ∩ 목적 (1번 결과가 비었고, purposeId가 있을 때)
        if (candidates.isEmpty() && purposeId != null) {
            val purposeItems = itemToPurposeDao.findItems(purposeId, categoryId)
                .filterNot { it in excludeItems }
                .toSet()

            candidates = tempItems intersect purposeItems
        }

        // 3) 온도만
        if (candidates.isEmpty()) {
            candidates = tempItems
        }

        if (candidates.isEmpty()) {
            return if (allowEmpty) null else null
        }

        Log.d("RecommendRepo", "최종 candidates = $candidates")

        return candidates.random()
    }

    suspend fun getMoodIdOrNull(moodCode: String?): Int? {
        if (moodCode == null) return null
        return moodDao.getByCode(moodCode)?.moodId
    }

    private fun <T> setOfNotNull(value: T?): Set<T> =
        if (value == null) emptySet() else setOf(value)

    /**
     * DB에 저장된 coordination_set / coordination_item 을 기반으로
     * RecommendationResult + moodId 도메인 모델로 재구성
     */
    suspend fun loadRecommendationFromDbForLatestState(): RecommendationWithMood {
        val state = lastUserStateRepository.getLatestState()
            ?: error("최근 LastUserState 데이터가 없습니다.")

        val setEntities = coordinationSetDao.findSets(state.id)
            .sortedBy { it.orderNo }

        if (setEntities.isEmpty()) {
            error("저장된 추천 코디 세트가 없습니다.")
        }

        suspend fun mapSet(entity: CoordinationSetEntity): CoordinationSetDomain {
            val itemEntities = coordinationItemDao.findItemsOfSet(entity.setId)

            val domainItems = itemEntities.map { ci ->
                val itemEntity = itemDao.getById(ci.itemId)

                CoordinationItemDomain(
                    itemId = ci.itemId,
                    displayName = itemEntity?.kname ?: ci.itemId,
                    categoryId = itemEntity?.categoryId ?: ci.categoryId
                )
            }

            return CoordinationSetDomain(items = domainItems)
        }

        val firstSetEntity = setEntities.firstOrNull { it.orderNo == 1 }
            ?: error("order_no=1 코디 세트가 없습니다.")
        val secondSetEntity = setEntities.firstOrNull { it.orderNo == 2 }
            ?: error("order_no=2 코디 세트가 없습니다.")

        val firstSetDomain = mapSet(firstSetEntity)
        val secondSetDomain = mapSet(secondSetEntity)

        return RecommendationWithMood(
            firstSet = firstSetDomain,
            secondSet = secondSetDomain,
            moodId = firstSetEntity.moodId   // 두 세트 모두 같은 mood를 공유한다고 가정
        )
    }

    private suspend fun saveCoordinationSetsToDb(
        lastUserStateId: Int,
        recommendation: RecommendationResult,
        moodId: Int?
    ) {
        // 1) 기존 세트 삭제
        coordinationSetDao.deleteByLastUserState(lastUserStateId)

        // 2) 시간 포맷 — TimeProvider 활용
        val nowStr = timeProvider.kstDateTimeString()

        suspend fun insertOneSet(orderNo: Int, setDomain: CoordinationSetDomain) {
            val setEntity = CoordinationSetEntity(
                lastUserStateId = lastUserStateId,
                orderNo = orderNo,
                createdAt = nowStr,
                moodId = moodId
            )

            val setIdLong = coordinationSetDao.insert(setEntity)
            val setId = setIdLong.toInt()

            val itemEntities = setDomain.items.map { item ->
                CoordinationItemEntity(
                    setId = setId,
                    categoryId = item.categoryId,
                    itemId = item.itemId
                )
            }

            coordinationItemDao.insertAll(itemEntities)
        }

        insertOneSet(orderNo = 1, setDomain = recommendation.firstSet)
        insertOneSet(orderNo = 2, setDomain = recommendation.secondSet)
    }
}

/* ------------ 도메인 / 결과용 모델 ------------ */

data class CoordinationItemDomain(
    val itemId: String,
    val displayName: String,
    val categoryId: Int
)

data class CoordinationSetDomain(
    val items: List<CoordinationItemDomain>
)

data class RecommendationResult(
    val firstSet: CoordinationSetDomain,
    val secondSet: CoordinationSetDomain
)

data class RecommendationWithMood(
    val firstSet: CoordinationSetDomain,
    val secondSet: CoordinationSetDomain,
    val moodId: Int?
)