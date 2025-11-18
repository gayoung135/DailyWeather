package com.smartapp.dailyweather.data.local.entity

import androidx.room.*

/**
 * 마지막 사용자 상태 (좌표/질문/시간)
 * - 시간은 "yyyy-MM-dd HH:mm:ss" 문자열로 저장
 * - tempId/purposeId/styleId 는 NULL 허용
 */
@Entity(tableName = "last_user_state")
data class LastUserStateEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "x")
    val x: Int,

    @ColumnInfo(name = "y")
    val y: Int,

    /** 사용자가 선택한 시/도 이름 (예: "부산광역시"), 없으면 null */
    @ColumnInfo(name = "region_name")
    val regionName: String?,

    /** 사용자가 선택한 시/군/구 이름 (예: "연제구"), 없으면 null */
    @ColumnInfo(name = "sub_region_name")
    val subRegionName: String?,


    /** temp_range 테이블의 PK를 가리키는 값 (버킷 id) */
    @ColumnInfo(name = "temp_id")
    val tempId: Int,

    /** 목적 id (nullable) */
    @ColumnInfo(name = "purpose_id")
    val purposeId: Int?,

    /** 스타일 id (nullable) */
    @ColumnInfo(name = "style_id")
    val styleId: Int?,

    /** "yyyy-MM-dd HH:mm:ss" 형식의 KST 문자열 */
    @ColumnInfo(name = "last_access_time")
    val lastAccessTime: String,

    /** 사용자가 선택한 기분 코드 (good / normal / bad), 선택 안 했으면 null */
    @ColumnInfo(name = "mood_code")
    val moodCode: String?,

    /** 질문 스킵 여부 */
    @ColumnInfo(name = "skipped")
    val skipped: Boolean
)

/**
 * 추천 코디 세트의 머리(코디1/코디2)
 * - 각 세트 생성 시간(createdAt) 문자열로 저장
 */
@Entity(
    tableName = "coordination_set",
    indices = [
        Index(value = ["last_user_state_id"]),
        //중복 방지
        Index(value = ["last_user_state_id","order_no"], unique = true),
        Index(value = ["order_no"]),
        Index(value = ["mood_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = LastUserStateEntity::class,
            parentColumns = ["id"],
            childColumns = ["last_user_state_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MoodEntity::class,
            parentColumns = ["mood_id"],
            childColumns  = ["mood_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class CoordinationSetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "set_id") val setId: Int = 0,

    @ColumnInfo(name = "last_user_state_id") val lastUserStateId: Int,

    /** 1 → 코디1, 2 → 코디2 */
    @ColumnInfo(name = "order_no") val orderNo: Int,

    /** "yyyy-MM-dd HH:mm:ss" */
    @ColumnInfo(name = "created_at") val createdAt: String,

    /** 기본 옵션: NULL 허용 */
    @ColumnInfo(name = "mood_id") val moodId: Int? = null
)


/**
 * 코디 세트 상세: 카테고리별 1개 아이템
 * - 복합 PK: (set_id, category_id)
 */
@Entity(
    tableName = "coordination_item",
    indices = [
        Index(value = ["set_id"]),
        Index(value = ["category_id"]),
        Index(value = ["item_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = CoordinationSetEntity::class,
            parentColumns = ["set_id"],
            childColumns = ["set_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class CoordinationItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Int = 0,

    @ColumnInfo(name = "set_id") val setId: Int,
    @ColumnInfo(name = "category_id") val categoryId: Int,
    @ColumnInfo(name = "item_id") val itemId: String
)
