package com.smartapp.dailyweather.data.local.entity

import androidx.room.*

/** (온도 + 카테고리) → 아이템 */
@Entity(
    tableName = "item_to_temp",
    primaryKeys = ["temp_id", "category_id", "item_id"],
    foreignKeys = [
        ForeignKey(entity = TempRangeEntity::class, parentColumns = ["temp_id"], childColumns = ["temp_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["category_id"]), Index(value = ["item_id"])]
)
data class ItemToTempEntity(
    @ColumnInfo(name = "temp_id") val tempId: Int,
    @ColumnInfo(name = "category_id") val categoryId: Int,
    @ColumnInfo(name = "item_id") val itemId: String
)

/** (목적 + 카테고리) → 아이템 */
@Entity(
    tableName = "item_to_purpose",
    primaryKeys = ["purpose_id", "category_id", "item_id"],
    foreignKeys = [
        ForeignKey(entity = PurposeEntity::class, parentColumns = ["purpose_id"], childColumns = ["purpose_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["category_id"]), Index(value = ["item_id"])]
)
data class ItemToPurposeEntity(
    @ColumnInfo(name = "purpose_id") val purposeId: Int,
    @ColumnInfo(name = "category_id") val categoryId: Int,
    @ColumnInfo(name = "item_id") val itemId: String
)

/** (스타일 + 카테고리) → 아이템 */
@Entity(
    tableName = "item_to_style",
    primaryKeys = ["style_id", "category_id", "item_id"],
    foreignKeys = [
        ForeignKey(entity = StyleEntity::class, parentColumns = ["style_id"], childColumns = ["style_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["category_id"]), Index(value = ["item_id"])]
)
data class ItemToStyleEntity(
    @ColumnInfo(name = "style_id") val styleId: Int,
    @ColumnInfo(name = "category_id") val categoryId: Int,
    @ColumnInfo(name = "item_id") val itemId: String
)

/** (대기질 → 마스크류) 규칙 */
@Entity(
    tableName = "necessity_rule_air",
    primaryKeys = ["air_quality_id", "item_id"],
    foreignKeys = [
        ForeignKey(entity = AirQualityEntity::class, parentColumns = ["air_quality_id"], childColumns = ["air_quality_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["item_id"])]
)
data class NecessityRuleAirEntity(
    @ColumnInfo(name = "air_quality_id") val airQualityId: Int,
    @ColumnInfo(name = "item_id") val itemId: String // basic_mask / kf94_mask
)

/** (강수 → 우산류) 규칙 */
@Entity(
    tableName = "necessity_rule_rain",
    primaryKeys = ["rain_level_id", "item_id"],
    foreignKeys = [
        ForeignKey(entity = RainLevelEntity::class, parentColumns = ["rain_level_id"], childColumns = ["rain_level_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["item_id"])]
)
data class NecessityRuleRainEntity(
    @ColumnInfo(name = "rain_level_id") val rainLevelId: Int,
    @ColumnInfo(name = "item_id") val itemId: String // hand_umbrella / long_umbrella
)
