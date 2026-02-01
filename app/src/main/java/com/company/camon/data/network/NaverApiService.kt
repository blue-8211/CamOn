package com.company.camon.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// 네이버 검색 결과 모델
data class NaverSearchResponse(val items: List<SearchResultItem>)

data class SearchResultItem(
    val title: String,
    val address: String,
    val roadAddress: String,
    val mapx: String,
    val mapy: String
)

// 1. 쇼핑 검색 결과 모델 추가
data class NaverShopResponse(
    val items: List<ShopItem>
)

data class ShopItem(
    val title: String,      // 상품명 (HTML 태그 포함됨)
    val image: String,      // 상품 이미지 URL
    val lprice: String,     // 최저가 (나중에 가격 정보 넣을 때 활용)
    val brand: String,      // 브랜드명
    val category1: String,  // 대분류
    val category2: String   // 중분류 (카테고리 매칭 시 활용)
)

// 네이버 검색 API 인터페이스
interface NaverSearchApi {
    @GET("v1/search/local.json")
    suspend fun searchCamping(
        @Header("X-Naver-Client-Id") clientId: String,
        @Header("X-Naver-Client-Secret") clientSecret: String,
        @Query("query") query: String,
        @Query("display") display: Int = 5
    ): NaverSearchResponse

    @GET("v1/search/shop.json")
    suspend fun searchGear(
        @Header("X-Naver-Client-Id") clientId: String,
        @Header("X-Naver-Client-Secret") clientSecret: String,
        @Query("query") query: String,
        @Query("display") display: Int = 10
    ): NaverShopResponse
}

// Retrofit 객체 생성
val retrofit = Retrofit.Builder()
    .baseUrl("https://openapi.naver.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
val naverApi = retrofit.create(NaverSearchApi::class.java)

