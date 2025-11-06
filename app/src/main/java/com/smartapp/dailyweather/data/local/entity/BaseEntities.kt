package com.smartapp.dailyweather.data.local.entity

import androidx.room.*

/* 카테고리 테이블 */
@Entity(
    tableName = "category",
    indices = [Index(value = ["category_code"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "category_code") val categoryCode: String // OUTER, TOP, BOTTOM, ACC, NECESSITY
)

/* 아이템 테이블 */
@Entity(
    tableName = "item",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["category_id"]), Index(value = ["id"], unique = true)]
)
data class ItemEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,         // ex) "o_long_puffer"
    @ColumnInfo(name = "kname") val kname: String,               // 표시명(한글)
    @ColumnInfo(name = "category_id") val categoryId: Int        // FK -> category.id
)

/** 온도 범위 테이블 */
@Entity(
    tableName = "temp_range",
    indices = [
        Index(value = ["min_temp", "max_temp"]) // 범위 검색 최적화
    ]
)
data class TempRangeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "temp_id") val tempId: Int = 0,
    @ColumnInfo(name = "min_temp") val minTemp: Int,
    @ColumnInfo(name = "max_temp") val maxTemp: Int
)

/* 목적(Purpose) */
@Entity(
    tableName = "purpose",
    indices = [Index(value = ["purpose_code"], unique = true)]
)
data class PurposeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "purpose_id") val purposeId: Int = 0,
    @ColumnInfo(name = "purpose_code") val purposeCode: String // office / celebration / date / travel / none
)

/* 선호 스타일(LikeStyle) */
@Entity(
    tableName = "style",
    indices = [Index(value = ["style_code"], unique = true)]
)
data class StyleEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "style_id") val styleId: Int = 0,
    @ColumnInfo(name = "style_code") val styleCode: String // formal / casual / street / lovely / none
)

/*미세먼지(AirQuality) */
@Entity(
    tableName = "air_quality",
    indices = [Index(value = ["air_quality_code"], unique = true)]
)
data class AirQualityEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "air_quality_id") val airQualityId: Int = 0,
    @ColumnInfo(name = "air_quality_code") val airQualityCode: String // good / normal / bad / hazard
)

/* 강수 레벨(RainLevel) */
@Entity(
    tableName = "rain_level",
    indices = [Index(value = ["rain_level_code"], unique = true)]
)
data class RainLevelEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rain_level_id") val rainLevelId: Int = 0,
    @ColumnInfo(name = "rain_level_code") val rainLevelCode: String // none / low / medium / high
)
/* 기분별 색상*/
@Entity(
    tableName = "mood",
    indices = [Index(value = ["mood_code"], unique = true)]
)
data class MoodEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "mood_id") val moodId: Int = 0,
    @ColumnInfo(name = "mood_code") val moodCode: String // good / normal / bad
)

/*Mood Color*/
@Entity(
    tableName = "mood_color",
    primaryKeys = ["mood_id", "cname"],   //  id 없이 복합 PK
    foreignKeys = [
        ForeignKey(
            entity = MoodEntity::class,
            parentColumns = ["mood_id"],
            childColumns = ["mood_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["hex"], unique = true), // 색상코드 중복 방지
        Index(value = ["mood_id"])             // 조회용 인덱스
    ]
)
data class MoodColorEntity(
    @ColumnInfo(name = "mood_id") val moodId: Int,
    @ColumnInfo(name = "cname") val cname: String, // ex) "노랑"
    @ColumnInfo(name = "hex") val hex: String      // ex) "#FFD54F"
)