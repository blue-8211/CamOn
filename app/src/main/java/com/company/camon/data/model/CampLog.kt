package com.company.camon.data.model

/**
 * 캠핑 기록 데이터 모델
 * @param date 날짜 (Key값으로 사용됨, 예: "2024-05-20")
 * @param location 캠핑장 이름
 * @param address 캠핑장 주소
 * @param mapx 네이버 지도 X 좌표
 * @param mapy 네이버 지도 Y 좌표
 * @param isPublic 공개 여부
 * @param gearIds 이 캠핑에 가져간 장비들의 고유 ID 리스트 (추가됨!)
 * @param memo 간단한 메모나 후기 (추가하면 좋음)
 */
data class CampLog(
    val startDate: String,         // "2026-02-13" (출발일)
    val nights: Int = 0,           // 0=당일, 1=1박, 2=2박 ...
    val location: String,
    val address: String = "",
    val mapx: String = "",
    val mapy: String = "",
    val isPublic: Boolean = false,
    val gearIds: List<String> = emptyList(), // 장비 연결 고리
    val checkedGearIds: List<String> = emptyList(), // 💡 추가: 체크된 장비 ID 리스트
    val memo: String = ""
)