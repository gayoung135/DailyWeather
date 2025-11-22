package com.smartapp.dailyweather.data.local.dao

import androidx.room.*
import com.smartapp.dailyweather.data.local.entity.*

@Dao
interface LastUserStateDao {

    /**
     * 🔵 단일행 모드 전제로 사용하는 DAO
     * - 항상 last_user_state 테이블에는 0 또는 1건만 존재하도록
     *   Repository 단에서 clearAll() 후 insert() 또는 update()만 사용.
     */

    // 새 레코드 삽입 (보통은 clearAll() 뒤에 한 번만 호출)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: LastUserStateEntity): Long

    // 기존 1건을 수정할 때 사용 (refreshLatestStateTempIfSkipped)
    @Update
    suspend fun update(entity: LastUserStateEntity): Int

    // 가장 최근 1건
    @Query(
        """
        SELECT * FROM last_user_state 
        ORDER BY last_access_time DESC, id DESC 
        LIMIT 1
        """
    )
    suspend fun getLatest(): LastUserStateEntity?

    // 같은 날(문자열 날짜 비교용) 조회 예시: "2025-11-23"
    @Query(
        """
        SELECT * FROM last_user_state 
        WHERE last_access_time LIKE :datePrefix || '%' 
        ORDER BY last_access_time DESC, id DESC
        """
    )
    suspend fun findByDatePrefix(datePrefix: String): List<LastUserStateEntity>

    // 단일행 모드 유지용: 삽입 전에 전부 밀어버릴 때 사용
    @Query("DELETE FROM last_user_state")
    suspend fun clearAll()

    /* ===== 단일행 모드로 완전히 바꾸고 싶을 때 대체 가능 (지금은 사용 안 함) ===== */
    // @Upsert
    // suspend fun upsert(entity: LastUserStateEntity): Long
}

@Dao
interface CoordinationSetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(set: CoordinationSetEntity): Long

    @Query(
        """
        SELECT * FROM coordination_set 
        WHERE last_user_state_id = :lusId 
        ORDER BY order_no ASC
        """
    )
    suspend fun findSets(lusId: Int): List<CoordinationSetEntity>

    @Query("DELETE FROM coordination_set WHERE last_user_state_id = :lusId")
    suspend fun deleteByLastUserState(lusId: Int)
}

@Dao
interface CoordinationItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(list: List<CoordinationItemEntity>)

    @Query(
        """
        SELECT * FROM coordination_item 
        WHERE set_id = :setId 
        ORDER BY category_id ASC
        """
    )
    suspend fun findItemsOfSet(setId: Int): List<CoordinationItemEntity>

    @Query("DELETE FROM coordination_item WHERE set_id = :setId")
    suspend fun deleteBySetId(setId: Int)
}
