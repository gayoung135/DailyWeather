package com.smartapp.dailyweather.data.repo

import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.core.TimeProvider
import com.smartapp.dailyweather.core.kstDateTimeString
import com.smartapp.dailyweather.core.parseKstDateTime
import com.smartapp.dailyweather.data.local.AppDatabase
import com.smartapp.dailyweather.data.local.entity.LastUserStateEntity
import com.smartapp.dailyweather.data.model.GridPoint
import java.time.ZonedDateTime
import java.time.Duration

data class LastStateFreshness(
    val state: LastUserStateEntity,
    val hoursDiff: Long,
    val isSameDay: Boolean
)

class LastUserStateRepository(
    private val db: AppDatabase,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val weatherRepository: WeatherRepository = WeatherRepository(timeProvider)
) {

    private val dao = db.lastUserStateDao()

    /**
     * LastUserStateEntity "항상 1행만 유지"
     * 저장 단계에서 skipped=true 면 "사용자 시간 선택 없음" 또는 "질문 스킵" 케이스로 봄
     */
    suspend fun saveWithCurrentTemp(
        grid: GridPoint,
        regionName: String?,
        subRegionName: String?,
        targetTimeKst: ZonedDateTime,
        purposeCode: String?,
        styleCode: String?,
        moodCode: String?,
        skipped: Boolean
    ) {

        // 1) 해당 시각의 기온(℃)
        val tempValue: Int = weatherRepository.getTempAt(grid, targetTimeKst)

        // 2) 온도 버킷 찾기 (TempRangeEntity)
        val tempBucket = db.tempRangeDao().findBucketByTemp(tempValue)
            ?: error("해당 온도($tempValue℃)에 맞는 temp_range 버킷이 없습니다.")

        // 3) 목적/스타일 FK 찾기 (nullable)
        val purposeId = purposeCode?.let { code ->
            db.purposeDao().getByCode(code)?.purposeId
                ?: error("목적 코드($code)를 DB에서 찾을 수 없습니다.")
        }

        val styleId = styleCode?.let { code ->
            db.styleDao().getByCode(code)?.styleId
                ?: error("스타일 코드($code)를 DB에서 찾을 수 없습니다.")
        }

        // 4) 기온 조회에 사용한 targetTimeKst를 그대로 문자열로 저장
        val lastAccessStr = timeProvider.kstDateTimeString(targetTimeKst)

        val entity = LastUserStateEntity(
            id = 0,
            x = grid.x,
            y = grid.y,
            regionName = regionName,
            subRegionName = subRegionName,
            tempId = tempBucket.tempId,
            tempCelsius = tempValue,
            purposeId = purposeId,
            styleId = styleId,
            moodCode = moodCode,
            lastAccessTime = lastAccessStr,
            skipped = skipped
        )

        // 항상 1행 유지
        dao.clearAll()
        dao.insert(entity)
    }

    /**
     * 새로고침 시:
     *  - "시간 선택 안함(skipped=true)" 인 경우에만 now 기준 temp + lastAccessTime 재계산
     *  - "시간 YES(skipped=false)" 이면 그대로 유지 (외출시간 고정)
     */
    suspend fun refreshLatestStateTempIfSkipped(
        latestState: LastUserStateEntity
    ): LastUserStateEntity {

        // 시간 YES (skipped=false)면 업데이트 X, 기존 값 그대로 사용
        if (!latestState.skipped) return latestState

        val grid = GridPoint(latestState.x, latestState.y)

        val baseNow = timeProvider.nowKorea()

        val nowKst = baseNow
            .plusHours(1)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        // now 기준 기온
        val tempValue: Int = weatherRepository.getTempAt(grid, nowKst)

        val tempBucket = db.tempRangeDao().findBucketByTemp(tempValue)
            ?: error("해당 온도($tempValue℃)에 맞는 temp_range 버킷이 없습니다.")

        // nowKst를 문자열로 저장 → 이후 미세먼지/POP/우산 전부 이 시간 기준으로 사용
        val lastAccessStr = timeProvider.kstDateTimeString(nowKst)

        val updatedEntity = latestState.copy(
            tempId = tempBucket.tempId,
            tempCelsius = tempValue,
            lastAccessTime = lastAccessStr,
            // 여전히 "시간 선택 안함" 계열이므로 skipped=true 유지
            skipped = true
        )

        dao.update(updatedEntity)
        return updatedEntity
    }

    suspend fun getLatestState(): LastUserStateEntity? =
        dao.getLatest()

    // ===========================
    // "처음으로" 버튼 전용 API
    // ===========================

    /** last_user_state 테이블만 전체 삭제 */
    suspend fun clearLastUserState() {
        dao.clearAll()
    }

    /** coordination_set + coordination_item 전체 삭제 */
    suspend fun clearCoordinationTables() {
        db.coordinationSetDao().clearAll()
        db.coordinationItemDao().clearAll()
    }


    /**
     * 마지막 상태와 현재 시간 차이를 계산해서
     * - 몇 시간 지났는지(hoursDiff)
     * - 같은 달력 날짜인지(isSameDay)
     *
     * 24시간 기준 로직에서 hoursDiff >= 24 이면 "다른 날로 취급" 하는 식으로 사용.
     */
    suspend fun getLatestStateFreshness(): LastStateFreshness? {
        val state = dao.getLatest() ?: return null

        val last = timeProvider.parseKstDateTime(state.lastAccessTime)
        val now = timeProvider.nowKorea()

        val hoursDiff = kotlin.math.abs(
            Duration.between(last, now).toHours()
        )

        val isSameDay = last.toLocalDate() == now.toLocalDate()

        return LastStateFreshness(
            state = state,
            hoursDiff = hoursDiff,
            isSameDay = isSameDay
        )
    }
}
