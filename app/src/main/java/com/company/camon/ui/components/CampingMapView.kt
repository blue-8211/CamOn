package com.company.camon.ui.components

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CampingMapView(locationName: String) {
    val encodedLocation = Uri.encode(locationName)
    // 모바일 전용 네이버 지도 검색 URL
    val mapUrl = "https://m.map.naver.com/search2/search.naver?query=$encodedLocation"

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // 💡 핵심: 외부 브라우저 실행을 막고 WebView 안에서만 돌게 함
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        return false // false를 반환해야 WebView 내부에서 페이지 이동이 일어납니다.
                    }
                }

                settings.apply {
                    javaScriptEnabled = true // 자바스크립트 필수
                    domStorageEnabled = true // 네이버 지도 로딩에 필수
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }

                loadUrl(mapUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}