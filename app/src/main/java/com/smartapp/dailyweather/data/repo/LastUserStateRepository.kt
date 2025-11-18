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
     * 현재(또는 사용자가 선택한 targetTimeKst)의 기온을 조회하여
     * LastUserStateEntity 1건을 저장한다.
     *
     * @param regionName    사용자가 선택한 시/도 이름 (예: "부산광역시"), 없으면 null
     * @param subRegionName 사용자가 선택한 시/군/구 이름 (예: "연제구"), 없으면 null
     * @param moodCode 사용자가 선택한 기분 코드 (good/normal/bad), 없으면 null
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
            x = grid.x,
            y = grid.y,
            regionName = regionName,
            subRegionName = subRegionName,
            tempId = tempBucket.tempId,   // temp_range.id (버킷 id)를 tempId로 저장
            tempCelsius = tempValue,
            purposeId = purposeId,
            styleId = styleId,
            moodCode = moodCode,
            lastAccessTime = lastAccessStr,
            skipped = skipped
        )

        dao.insert(entity)
    }

    suspend fun getLatestState(): LastUserStateEntity? =
        dao.getLatest()
}