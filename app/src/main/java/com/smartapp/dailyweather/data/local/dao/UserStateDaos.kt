package com.smartapp.dailyweather.data.local.dao

import androidx.room.*
import com.smartapp.dailyweather.data.local.entity.*


@Dao
interface LastUserStateDao {

    /* ===== 히스토리 모드 ===== */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: LastUserStateEntity): Long

    // 가장 최근 1건 (문자열 시간 정렬도 안전: yyyy-MM-dd HH:mm:ss)
    @Query("SELECT * FROM last_user_state ORDER BY last_access_time DESC, id DESC LIMIT 1")
    suspend fun getLatest(): LastUserStateEntity?

    // 같은 날(문자열 날짜 비교용) 조회 예시: "2025-11-06%"
    @Query("SELECT * FROM last_user_state WHERE last_access_time LIKE :datePrefix || '%' ORDER BY last_access_time DESC, id DESC")
    suspend fun findByDatePrefix(datePrefix: String): List<LastUserStateEntity>

    @Query("DELETE FROM last_user_state")
    suspend fun clearAll()

    /* ===== 단일행 모드로 바꿀 때 사용 (주석 해제) ===== */
    // @Upsert
    // suspend fun upsert(entity: LastUserStateEntity): Long
}

@Dao
interface CoordinationSetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(set: CoordinationSetEntity): Long

    @Query("""
        SELECT * FROM coordination_set 
        WHERE last_user_state_id = :lusId 
        ORDER BY order_no ASC
    """)
    suspend fun findSets(lusId: Int): List<CoordinationSetEntity>

    @Query("DELETE FROM coordination_set WHERE last_user_state_id = :lusId")
    suspend fun deleteByLastUserState(lusId: Int)
}

@Dao
interface CoordinationItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(list: List<CoordinationItemEntity>)

    @Query("""
        SELECT * FROM coordination_item 
        WHERE set_id = :setId 
        ORDER BY category_id ASC
    """)
    suspend fun findItemsOfSet(setId: Int): List<CoordinationItemEntity>

    @Query("DELETE FROM coordination_item WHERE set_id = :setId")
    suspend fun deleteBySetId(setId: Int)
}
