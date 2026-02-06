package com.company.camon.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 앱에서 기본적으로 제공하는 장비 마스터 데이터 (자동완성용)
 */
@Entity(tableName = "master_gear")
data class MasterGear(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val category: String, // 텐트, 타프, 체어 등
    val brand: String,    // 노르디스크, 헬리녹스 등
    val modelName: String, // 노나돔, 체어원 등
    val alias: String? = null // 검색 최적화를 위한 별칭 (선택사항)
)