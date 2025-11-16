package com.smartapp.dailyweather.data.local.entity

import androidx.room.*

/**
 * 마지막 사용자 상태 (좌표/질문/시간)
 * - 시간은 "yyyy-MM-dd HH:mm:ss" 문자열로 저장
 * - tempId/purposeId/styleId 는 NULL 허용
 */
@Entity(
    tableName = "last_user_state",
    indices = [
        Index(value = ["last_access_time"]),      // 정렬/검색 최적화
        Index(value = ["x", "y"])  ,               // 위치 비교 최적화
        Index(value = ["temp_id"]),
        Index(value = ["purpose_id"]),
        Index(value = ["style_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TempRangeEntity::class,
            parentColumns = ["temp_id"],
            childColumns = ["temp_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.NO_ACTION   // NOT NULL FK라 삭제전이 없음
        ),
        ForeignKey(
            entity = PurposeEntity::class,
            parentColumns = ["purpose_id"],
            childColumns = ["purpose_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = StyleEntity::class,
            parentColumns = ["style_id"],
            childColumns = ["style_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class LastUserStateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Int = 0,

    @ColumnInfo(name = "x") val x: Int,
    @ColumnInfo(name = "y") val y: Int,

    @ColumnInfo(name = "temp_id") val tempId: Int,
    @ColumnInfo(name = "purpose_id") val purposeId: Int? = null,
    @ColumnInfo(name = "style_id") val styleId: Int? = null,

    @ColumnInfo(name = "skip_question") val skipQuestion: Boolean,

    // "yyyy-MM-dd HH:mm:ss" (예: "2025-11-06 18:42:00")
    @ColumnInfo(name = "last_access_time") val lastAccessTime: String
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
    primaryKeys = ["set_id", "category_id"],
    indices = [
        Index(value = ["item_id"]),
        Index(value = ["category_id"])
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
    @ColumnInfo(name = "set_id") val setId: Int,
    @ColumnInfo(name = "category_id") val categoryId: Int,
    @ColumnInfo(name = "item_id") val itemId: String
)
