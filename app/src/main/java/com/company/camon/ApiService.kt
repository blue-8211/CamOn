package com.company.camon

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

// 서버로 보낼 택배 상자
data class LoginRequest(
    val idToken: String  // 👈 파이썬의 idToken: str과 반드시 일치!
)

// 서버에서 받을 응답지
data class LoginResponse(
    val message: String
)

interface ApiService {
    @POST("login") // 👈 파이썬 @app.post("/login")과 일치!
    fun sendIdToken(@Body request: LoginRequest): Call<LoginResponse>
}