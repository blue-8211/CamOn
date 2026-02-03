package com.company.camon.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    // OpenWeatherMap 기준 (무료 키 발급이 매우 빠릅니다)
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric" // 섭씨 온도를 위해 metric 사용
    ): WeatherResponse

    // 💡 [추가] 5일 예보 API
    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): ForecastResponse
}

// 💡 결과를 받기 위한 데이터 모델 (심플하게 필요한 것만 정의)
data class WeatherResponse(
    val main: MainData,
    val wind: WindData
)

// Forecast 데이터 구조 (간소화 버전)
data class ForecastResponse(
    val list: List<ForecastItem>
)

data class ForecastItem(
    val dt: Long,             // Unix 타임스탬프
    val main: MainData,       // temp_max, temp_min 포함
    val wind: WindData,       // speed 포함
    val dt_txt: String        // "2026-02-05 12:00:00" 형태
)

// 💡 [수정] 기온 관련 변수들을 추가합니다.
data class MainData(
    val temp: Double,
    val temp_max: Double, // 최고 기온
    val temp_min: Double  // 최저 기온
)

data class WindData(
    val speed: Double
)