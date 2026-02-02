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
}

// 💡 결과를 받기 위한 데이터 모델 (심플하게 필요한 것만 정의)
data class WeatherResponse(
    val main: MainData,
    val wind: WindData
)

data class MainData(val temp: Double)
data class WindData(val speed: Double)