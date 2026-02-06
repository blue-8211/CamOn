package com.company.camon.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사용자가 소유한 실제 장비 데이터 (내 창고)
 */
@Entity(tableName = "user_gear")
data class UserGear(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 기본 정보
    val category: String,  // 카테고리
    val brand: String,     // 브랜드명
    val modelName: String, // 모델명

    // 관리 정보
    val quantity: Int = 1, // 수량
    val memo: String = "", // 메모

    // 기획안 5번: 감성/용도 체크박스 (캠퍼의 디테일)
    val isWinterOnly: Boolean = false,  // 동계 전용 여부
    val isFirewoodUse: Boolean = false, // 화목난로 사용 여부

    // 기획안 6번: 예외 루트용 필드
    val imageUrl: String = "",  // 장비 사진 (로컬 경로 또는 URL)
    val linkUrl: String = "",   // 구매 링크 (스토어 링크 붙여넣기 대응)

    // 정렬을 위한 등록 일시
    val createdAt: Long = System.currentTimeMillis()
)