package com.company.camon.data.db

import androidx.room.*
import com.company.camon.data.model.MasterGear
import com.company.camon.data.model.UserGear
import kotlinx.coroutines.flow.Flow

@Dao
interface GearDao {
    // --- [마스터 DB: 80% 기본 데이터 조회] ---

    // 1. 특정 카테고리에 포함된 브랜드만 중복 없이 가져오기 (예: '텐트' 선택 시 '노르디스크', '코베아' 등)
    @Query("SELECT DISTINCT brand FROM master_gear WHERE category = :category")
    suspend fun getBrandsByCategory(category: String): List<String>

    // 2. 특정 브랜드의 모델명들 가져오기 (예: '헬리녹스' 선택 시 '체어원', '선셋체어' 등)
    @Query("SELECT * FROM master_gear WHERE category = :category AND brand = :brand")
    suspend fun getModels(category: String, brand: String): List<MasterGear>


    // --- [유저 DB: 내 창고 관리] ---

    // 3. 내 장비 저장 (기획안 7단계: 완료 버튼 클릭 시)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserGear(gear: UserGear): Long // 👈 반드시 : Long을 추가해야 ID가 반환됩니다!

    // 4. 내 장비 목록 전체 가져오기 (최신순)
    @Query("SELECT * FROM user_gear ORDER BY createdAt DESC")
    fun getAllUserGears(): Flow<List<UserGear>>

    // 5. 특정 카테고리별 내 장비 필터링
    @Query("SELECT * FROM user_gear WHERE category = :category ORDER BY createdAt DESC")
    fun getUserGearsByCategory(category: String): Flow<List<UserGear>>

    // 6. 장비 삭제
    @Delete
    suspend fun deleteUserGear(gear: UserGear)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMasterGear(masterGear: MasterGear) // 👈 마스터 데이터 심을 때 필요!
    // --- [추가 1. 도구/소모품 전용 조회] ---
    // 💡 도구 추가 버튼이나 소모품 팝업에서 특정 카테고리 아이템만 가져올 때 사용합니다.
    @Query("SELECT * FROM master_gear WHERE category = :category ORDER BY modelName ASC")
    fun getMasterGearsByCategory(category: String): Flow<List<MasterGear>>

    // --- [추가 2. 일반 장비 검색 최적화] ---
    // 💡 내 창고에 장비 등록할 때, '도구'나 '소모품'은 검색 결과에서 제외하기 위함입니다.
    @Query("""
        SELECT * FROM master_gear 
        WHERE category NOT IN ('도구', '소모품') 
        AND (brand LIKE '%' || :query || '%' OR modelName LIKE '%' || :query || '%')
    """)
    suspend fun searchMainGears(query: String): List<MasterGear>

    // 💡 특정 카테고리의 모든 마스터 기어 가져오기 (Suspend 버전 - 일회성 조회용)
    @Query("SELECT * FROM master_gear WHERE category = :category")
    suspend fun getMasterGearsByCategoryOnce(category: String): List<MasterGear>
}