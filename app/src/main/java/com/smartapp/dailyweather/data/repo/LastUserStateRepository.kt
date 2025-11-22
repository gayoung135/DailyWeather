package com.smartapp.dailyweather.data.repo

import com.smartapp.dailyweather.core.SystemTimeProvider
import com.smartapp.dailyweather.core.TimeProvider
import com.smartapp.dailyweather.core.kstDateTimeString
import com.smartapp.dailyweather.data.local.AppDatabase
import com.smartapp.dailyweather.data.local.entity.LastUserStateEntity
import com.smartapp.dailyweather.data.model.GridPoint
import java.time.ZonedDateTime

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
     * 새로고침 시: "시간 선택 안함(skipped=true)" → now 기준 temp 재계산 + update
     *  - 스킵/시간 NO : skipped = true → 여기서 UPDATE
     *  - 시간 YES     : skipped = false → 그대로 유지
     */
    suspend fun refreshLatestStateTempIfSkipped(
        latestState: LastUserStateEntity
    ): LastUserStateEntity {

        // 시간 YES (skipped=false)면 업데이트 X, 기존 값 그대로 사용
        if (!latestState.skipped) return latestState

        val grid = GridPoint(latestState.x, latestState.y)
        val nowKst: ZonedDateTime = timeProvider.nowKorea()

        val tempValue: Int = weatherRepository.getTempAt(grid, nowKst)

        val tempBucket = db.tempRangeDao().findBucketByTemp(tempValue)
            ?: error("해당 온도($tempValue℃)에 맞는 temp_range 버킷이 없습니다.")

        val lastAccessStr = timeProvider.kstDateTimeString(nowKst)

        val updatedEntity = latestState.copy(
            tempId = tempBucket.tempId,
            tempCelsius = tempValue,
            lastAccessTime = lastAccessStr,
            skipped = true   // 계속 "스킵/시간NO 계열"로 취급
        )

        dao.update(updatedEntity)
        return updatedEntity
    }

    suspend fun getLatestState(): LastUserStateEntity? =
        dao.getLatest()
}
