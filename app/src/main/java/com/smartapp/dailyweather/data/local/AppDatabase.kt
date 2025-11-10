//Room DB
package com.smartapp.dailyweather.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartapp.dailyweather.data.local.dao.*
import com.smartapp.dailyweather.data.local.entity.*


@Database(
    entities = [
        // BaseEntities
        CategoryEntity::class,
        ItemEntity::class,
        TempRangeEntity::class,
        PurposeEntity::class,
        StyleEntity::class,
        AirQualityEntity::class,
        RainLevelEntity::class,
        MoodEntity::class,
        MoodColorEntity::class,

        // RelationEntities
        ItemToTempEntity::class,
        ItemToPurposeEntity::class,
        ItemToStyleEntity::class,
        NecessityRuleAirEntity::class,
        NecessityRuleRainEntity::class,

        //  UserState
        LastUserStateEntity::class,
        CoordinationSetEntity::class,
        CoordinationItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    // ===== Base DAOs =====
    abstract fun categoryDao(): CategoryDao
    abstract fun itemDao(): ItemDao
    abstract fun tempRangeDao(): TempRangeDao
    abstract fun purposeDao(): PurposeDao
    abstract fun styleDao(): StyleDao
    abstract fun airQualityDao(): AirQualityDao
    abstract fun rainLevelDao(): RainLevelDao
    abstract fun MoodDao(): MoodDao
    abstract fun MoodColorDao() : MoodColorDao

    // Relation DAOs
    abstract fun itemToTempDao(): ItemToTempDao
    abstract fun itemToPurposeDao(): ItemToPurposeDao
    abstract fun itemToStyleDao(): ItemToStyleDao
    abstract fun necessityRuleAirDao(): NecessityRuleAirDao
    abstract fun necessityRuleRainDao(): NecessityRuleRainDao

    //  UserState DAOs
    abstract fun lastUserStateDao(): LastUserStateDao
    abstract fun coordinationSetDao(): CoordinationSetDao
    abstract fun coordinationItemDao(): CoordinationItemDao

}
