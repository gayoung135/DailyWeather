package com.smartapp.dailyweather.data.local.dao

import androidx.room.*
import com.smartapp.dailyweather.data.local.entity.*

@Dao
interface CategoryDao {
    @Upsert suspend fun upsertAll(list: List<CategoryEntity>)
    @Query("SELECT * FROM category WHERE category_code = :code LIMIT 1")
    suspend fun getByCode(code: String): CategoryEntity?
    @Query("SELECT * FROM category")
    suspend fun getAll(): List<CategoryEntity>
}

@Dao
interface ItemDao {
    @Upsert suspend fun upsertAll(list: List<ItemEntity>)
    @Query("SELECT * FROM item WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ItemEntity?
    @Query("SELECT * FROM item WHERE category_id = :categoryId")
    suspend fun getByCategory(categoryId: Int): List<ItemEntity>
}

@Dao
interface TempRangeDao {
    @Upsert suspend fun upsertAll(list: List<TempRangeEntity>)
    @Query("SELECT * FROM temp_range")
    suspend fun getAll(): List<TempRangeEntity>
    @Query("SELECT * FROM temp_range WHERE :t BETWEEN min_temp AND max_temp LIMIT 1")
    suspend fun findBucketByTemp(t: Int): TempRangeEntity?
}

@Dao
interface PurposeDao {
    @Upsert suspend fun upsertAll(list: List<PurposeEntity>)
    @Query("SELECT * FROM purpose WHERE purpose_code = :code LIMIT 1")
    suspend fun getByCode(code: String): PurposeEntity?
}

@Dao
interface StyleDao {
    @Upsert suspend fun upsertAll(list: List<StyleEntity>)
    @Query("SELECT * FROM style WHERE style_code = :code LIMIT 1")
    suspend fun getByCode(code: String): StyleEntity?
}

@Dao
interface AirQualityDao {
    @Upsert suspend fun upsertAll(list: List<AirQualityEntity>)
    @Query("SELECT * FROM air_quality WHERE air_quality_code = :code LIMIT 1")
    suspend fun getByCode(code: String): AirQualityEntity?
}

@Dao
interface RainLevelDao {
    @Upsert suspend fun upsertAll(list: List<RainLevelEntity>)
    @Query("SELECT * FROM rain_level WHERE rain_level_code = :code LIMIT 1")
    suspend fun getByCode(code: String): RainLevelEntity?
}
/* mood / mood_color */
@Dao
interface MoodDao {
    @Upsert
    suspend fun upsertAll(list: List<MoodColorEntity>)
    @Query("SELECT * FROM mood WHERE mood_code = :code LIMIT 1")
    suspend fun getByCode(code: String): MoodEntity?
}

@Dao
interface MoodColorDao {
    @Upsert suspend fun upsertAll(list: List<MoodColorEntity>)
    @Query("SELECT * FROM mood_color WHERE mood_id = :moodId ORDER BY cname")
    suspend fun getByMood(moodId: Int): List<MoodColorEntity>
}