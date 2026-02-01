package com.company.camon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.company.camon.data.network.NaverSearchApi
import com.company.camon.ui.gear.GearMainScreen
import com.company.camon.ui.home.MainHomeScreen
import com.company.camon.ui.log.CampingLogScreen // 상세 화면 import 확인!
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// 1. 앱의 메인 진입점
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainNavigationScreen()
            }
        }
    }
}

// 2. 화면 정의 (Sealed Class)
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "홈", Icons.Default.Home)
    object Gear : Screen("gear", "장비", Icons.Default.Build)
    object Calendar : Screen("calendar", "달력", Icons.Default.DateRange)
}

// 3. 메인 네비게이션 구조
@Composable
fun MainNavigationScreen() {
    val context = LocalContext.current

    // 현재 선택된 하단 탭 상태
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    // 💡 상세 화면 제어를 위한 상태 (날짜 문자열이 들어오면 상세 화면으로 간주)
    var detailLogDate by remember { mutableStateOf<String?>(null) }

    // 네이버 API 객체 싱글톤 유지
    val naverApi = remember {
        Retrofit.Builder()
            .baseUrl("https://openapi.naver.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NaverSearchApi::class.java)
    }

    Scaffold(
        bottomBar = {
            // 💡 상세 화면을 보고 있을 때는 바텀바를 숨겨서 몰입도를 높입니다.
            if (detailLogDate == null) {
                NavigationBar {
                    listOf(Screen.Home, Screen.Gear, Screen.Calendar).forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = selectedScreen == screen,
                            onClick = {
                                selectedScreen = screen
                                // 다른 탭으로 이동하면 상세 화면 상태 초기화
                                detailLogDate = null
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // 💡 [화면 전환 로직]
            // 1순위: 상세 화면 데이터(날짜)가 있으면 상세 페이지를 보여줌
            if (detailLogDate != null) {
                CampingLogScreen(
                    context = context,
                    date = detailLogDate!!,
                    onBack = { detailLogDate = null } // 뒤로가기 클릭 시 다시 메인으로
                )
            }
            // 2순위: 상세 화면 데이터가 없으면 하단 탭에 따른 메인 화면들을 보여줌
            else {
                when (selectedScreen) {
                    is Screen.Home -> MainHomeScreen(
                        context = context,
                        onNavigateToLog = { date ->
                            // 홈 화면에서 일정 클릭 시 상세 화면 날짜 상태를 업데이트!
                            detailLogDate = date
                        }
                    )
                    is Screen.Gear -> GearMainScreen(context = context, naverApi = naverApi)
                    is Screen.Calendar -> {
                        Text("준비 중인 달력 상세 화면입니다.", modifier = Modifier.padding(20.dp))
                    }
                }
            }
        }
    }
}