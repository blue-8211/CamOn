package com.company.camon.data.model

// 1. 개별 장비 모델 (ID 추가)
data class GearItem(
    val id: String = java.util.UUID.randomUUID().toString(), // 고유 식별자 추가
    val brand: String,
    val name: String,
    val category: String,
    val imageUrl: String = "",
    val isManual: Boolean = false,
    val quantity: Int = 1
)

// 2. 장비 그룹 모델 (바구니 역할)
data class GearGroup(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,               // 예: "백패킹 모드", "가족 오토캠핑"
    val description: String = "",   // 그룹에 대한 간단한 설명
    val gearIds: List<String> = emptyList() // 포함된 장비들의 ID 목록
)