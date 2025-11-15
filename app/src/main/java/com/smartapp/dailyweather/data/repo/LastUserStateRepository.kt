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

    /**
     * 1) 단기예보 API로 targetTime 의 기온(TMP) 조회
     * 2) TempRange 테이블에서 temp_id 버킷 찾기
     * 3) LastUserStateEntity 1건 저장
     */
    suspend fun saveWithCurrentTemp(
        grid: GridPoint,
        targetTimeKst: ZonedDateTime,
        purposeCode: String?,
        styleCode: String?,
        skipped: Boolean
    ): Long {

        // 1) 지금/외출 시각의 기온(℃)
        val tempCelsius: Int = weatherRepository.getTempAt(grid, targetTimeKst)

        // 2) 온도 버킷 찾기 (TempRangeEntity)
        val tempBucket = db.tempRangeDao().findBucketByTemp(tempCelsius)
            ?: error("해당 온도($tempCelsius℃)에 맞는 temp_range 버킷이 없습니다.")

        // 3) 목적/스타일 FK 찾기 (nullable)
        val purposeId = purposeCode?.let { code ->
            db.purposeDao().getByCode(code)?.purposeId
                ?: error("목적 코드($code)를 DB에서 찾을 수 없습니다.")
        }

        val styleId = styleCode?.let { code ->
            db.styleDao().getByCode(code)?.styleId
                ?: error("스타일 코드($code)를 DB에서 찾을 수 없습니다.")
        }

        // 4) 현재 접속 시간 문자열
        val nowStr = timeProvider.kstDateTimeString()

        val entity = LastUserStateEntity(
            x = grid.x,
            y = grid.y,
            tempId = tempBucket.tempId,
            purposeId = purposeId,
            styleId = styleId,
            skipQuestion = skipped,
            lastAccessTime = nowStr
        )

        return db.lastUserStateDao().insert(entity)
    }
}