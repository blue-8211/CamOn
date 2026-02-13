package com.company.camon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.company.camon.data.network.WeatherApiService
import com.company.camon.ui.gear.GearMainScreen
import com.company.camon.ui.home.MainHomeScreen
import com.company.camon.ui.log.CampingLogScreen // 상세 화면 import 확인!
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.company.camon.data.db.CamonDatabase
import com.company.camon.ui.calendar.CalendarScreen
import com.company.camon.ui.log.CampingLogDetailScreen
import com.company.camon.util.DatabaseInitializer
import com.company.camon.util.loadCampLogs
import java.time.LocalDate

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

    // 💡 [추가] 앱 시작 시 마스터 데이터 초기화 로직 실행 (2, 3, 8번 요구사항)
    LaunchedEffect(Unit) {
        val db = CamonDatabase.getDatabase(context)
        val gearDao = db.gearDao()
        DatabaseInitializer.initializeMasterData(context, gearDao)
    }


    // 현재 선택된 하단 탭 상태
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    // 💡 상세 화면 제어를 위한 상태 (날짜 문자열이 들어오면 상세 화면으로 간주)
    var detailLogDate by remember { mutableStateOf<String?>(null) }

    // 💡 1. 달력에서 선택한 날짜를 홈 화면과 공유하기 위한 변수
    var calendarSelectedDate by remember { mutableStateOf(LocalDate.now()) }

    // 네이버 API 객체 싱글톤 유지
    val naverApi = remember {
        Retrofit.Builder()
            .baseUrl("https://openapi.naver.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NaverSearchApi::class.java)
    }

    // 💡 날씨 API 객체 (remember를 사용하여 성능 최적화)
    val weatherApi = remember {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    var isDetailViewMode by remember { mutableStateOf(false) }

    var campLogs by remember { mutableStateOf(loadCampLogs(context)) }

    Scaffold(
        bottomBar = {
            // 💡 상세 화면을 보고 있을 때는 바텀바를 숨겨서 몰입도를 높입니다.
            if (detailLogDate == null) {
                NavigationBar (
                    modifier = Modifier.height(60.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    listOf(Screen.Home, Screen.Gear, Screen.Calendar).forEach { screen ->
                        NavigationBarItem(
                            selected = selectedScreen == screen,
                            onClick = {
                                selectedScreen = screen
                                detailLogDate = null
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.label,
                                    modifier = Modifier.size(22.dp) // 아이콘 크기 적당하게 유지
                                )
                            },
                            label = {
                                Text(
                                    text = screen.label,
                                    fontSize = 10.sp, // 글자 크기를 살짝 더 줄임
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            alwaysShowLabel = true, // 라벨을 항상 보여주어 위치 변동 방지
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
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
                if (isDetailViewMode) {
                    // 💡 달력에서 진입 시: 상세 조회/등록 화면
                    CampingLogDetailScreen(
                        context = context,
                        date = detailLogDate!!,
                        onBack = {
                            detailLogDate = null
                            isDetailViewMode = false // 뒤로갈 때 초기화
                            // 💡 [핵심] 상세 화면에서 돌아올 때 데이터를 다시 로드하여 상태를 갱신합니다.
                            campLogs = loadCampLogs(context)
                            // 확인용 로그 (Logcat에서 갱신 여부 확인)
                            //android.util.Log.d("NAV_DEBUG", "데이터 리로드 완료: ${campLogs.size}개의 기록")
                        }
                    )
                } else {
                    CampingLogScreen(
                        context = context,
                        date = detailLogDate!!,
                        onBack = { detailLogDate = null } // 뒤로가기 클릭 시 다시 메인으로
                    )
                }
            }
            // 2순위: 상세 화면 데이터가 없으면 하단 탭에 따른 메인 화면들을 보여줌
            else {
                when (selectedScreen) {
                    is Screen.Home -> MainHomeScreen(
                        context = context,
                        initialDate = calendarSelectedDate, // 👈 달력에서 선택된 날짜 전달
                        onNavigateToLog = { date -> detailLogDate = date },
                        weatherApi = weatherApi
                    )
                    is Screen.Gear -> GearMainScreen(
                        context = context,
                        naverApi = naverApi
                    )
                    is Screen.Calendar -> CalendarScreen(
                        context = context,
                        campLogs = campLogs, // 💡 항상 최신 상태인 campLogs를 전달
                        onDateSelectedForAdd = { date ->
                            // 💡 [기획 3번] 기록 없는 날 클릭 시: 홈으로 이동 + 날짜 선택
                            calendarSelectedDate = date
                            selectedScreen = Screen.Home
                        },
                        onLogClick = { date ->
                            // 💡 [수정] 기록이 있는 날을 클릭하면 상세 화면 날짜 상태를 업데이트합니다.
                            detailLogDate = date.toString()
                            isDetailViewMode = true // 💡 달력에서 클릭할 때는 '상세 조회 모드' 활성화
                        }
                    )
                }
            }
        }
    }
}