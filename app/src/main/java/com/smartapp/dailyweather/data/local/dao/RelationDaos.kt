package com.smartapp.dailyweather.data.local.dao

import androidx.room.*
import com.smartapp.dailyweather.data.local.entity.*

@Dao
interface ItemToTempDao {
    @Upsert suspend fun upsertAll(list: List<ItemToTempEntity>)
    @Query("""
        SELECT item_id FROM item_to_temp 
        WHERE temp_id = :tempId AND category_id = :categoryId
    """)
    suspend fun findItems(tempId: Int, categoryId: Int): List<String>
}

@Dao
interface ItemToPurposeDao {
    @Upsert suspend fun upsertAll(list: List<ItemToPurposeEntity>)
    @Query("""
        SELECT item_id FROM item_to_purpose
        WHERE purpose_id = :purposeId AND category_id = :categoryId
    """)
    suspend fun findItems(purposeId: Int, categoryId: Int): List<String>
}

@Dao
interface ItemToStyleDao {
    @Upsert suspend fun upsertAll(list: List<ItemToStyleEntity>)
    @Query("""
        SELECT item_id FROM item_to_style
        WHERE style_id = :styleId AND category_id = :categoryId
    """)
    suspend fun findItems(styleId: Int, categoryId: Int): List<String>
}

@Dao
interface NecessityRuleAirDao {
    @Upsert suspend fun upsertAll(list: List<NecessityRuleAirEntity>)
    @Query("SELECT item_id FROM necessity_rule_air WHERE air_quality_id = :airId")
    suspend fun findMaskByAir(airId: Int): List<String>  // 0~2개 예상
}

@Dao
interface NecessityRuleRainDao {
    @Upsert suspend fun upsertAll(list: List<NecessityRuleRainEntity>)
    @Query("SELECT item_id FROM necessity_rule_rain WHERE rain_level_id = :rainId")
    suspend fun findUmbrellaByRain(rainId: Int): List<String> // 0~2개 예상
}
