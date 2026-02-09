package com.company.camon.ui.home

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.company.camon.data.db.CamonDatabase
import com.company.camon.data.model.CampLog
import com.company.camon.data.network.SearchResultItem
import com.company.camon.data.network.WeatherApiService
import com.company.camon.data.network.naverApi
import com.company.camon.ui.component.GearGroupPicker
import com.company.camon.ui.component.IndividualGearPicker
import com.company.camon.ui.components.CampingMapView
import com.company.camon.util.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

@Composable
fun MainHomeScreen(context: Context, onNavigateToLog: (String) -> Unit, weatherApi: WeatherApiService) {
    // --- 1. 상태 관리 변수들 ---
    var selectedDate by remember { mutableStateOf(LocalDate.now()) } // 선택된 날짜
    var locationInput by remember { mutableStateOf("") } // 캠핑장 검색어 입력값
    var isPublic by remember { mutableStateOf(false) } // 공개/비공개 스위치
    var campLogs by remember { mutableStateOf(loadCampLogs(context)) } // 전체 캠핑 로그 데이터
    val scope = rememberCoroutineScope()

    // 💡 [추가] 달력 스크롤 제어를 위한 상태
    val calendarListState = rememberLazyListState()

    // --- 2. 장비 관련 상태 ---
    val allGroups = remember { loadGearGroups(context) } // 저장된 모든 장비 그룹
    // --- [수정] MainHomeScreen 상단 데이터 관측 부분 ---
    val db = remember { CamonDatabase.getDatabase(context) }
    val gearDao = db.gearDao()
    // Room DB에서 실시간으로 장비 리스트를 가져옵니다.
    val allGear by gearDao.getAllUserGears().collectAsState(initial = emptyList())
    var selectedGearIds by remember { mutableStateOf(setOf<String>()) } // 현재 선택된 장비 ID들 (중복방지 Set)

    // --- [상태 관리 변수 영역] ---
    var isEditing by remember { mutableStateOf(false) } // 💡 수정 모드 여부 추가

    // --- 3. 다이얼로그 제어 상태 ---
    var showGroupPicker by remember { mutableStateOf(false) }      // 그룹 선택 창 열림 여부
    var showIndividualPicker by remember { mutableStateOf(false) } // 개별 장비 선택 창 열림 여부
    var gearSearchQuery by remember { mutableStateOf("") }        // 개별 장비 선택 창 내 검색어
    var showMap by remember { mutableStateOf(false) }              // 지도 팝업 여부

    // --- 4. 검색 관련 상태 ---
    var mapTargetLocation by remember { mutableStateOf("") }
    var selectedSearchItem by remember { mutableStateOf<SearchResultItem?>(null) }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }

    var showLocationConfirm by remember { mutableStateOf(false) } // 다이얼로그 노출 여부
    var pendingItem by remember { mutableStateOf<SearchResultItem?>(null) } // 잠시 담아둘 아이템

    val currentLog = campLogs[selectedDate.toString()] // 💡 여기서 먼저 정의!

    var showDetailNotice by remember { mutableStateOf(false) }

    // 💡 1. 장소 변경 확인 다이얼로그
    if (showLocationConfirm) {
        AlertDialog(
            onDismissRequest = { showLocationConfirm = false },
            title = { Text("장소 변경 확인", fontWeight = FontWeight.Bold) },
            text = { Text("장소를 변경하면 지금까지 체크한 장비 내역이 모두 초기화됩니다. 정말 변경하시겠습니까?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        pendingItem?.let { item ->
                            // 💡 이제야 실제 입력 필드들을 업데이트합니다.
                            val cleanTitle = item.title.replace("<b>", "").replace("</b>", "")
                            locationInput = cleanTitle
                            selectedSearchItem = item

                            // 2. 💡 즉시 파일 저장 로직 수행
                            currentLog?.let { log ->
                                // 장소 정보와 체크 내역(비우기)을 한꺼번에 업데이트
                                val updatedLog = log.copy(
                                    location = cleanTitle,
                                    address = item.address,
                                    mapx = item.mapx,
                                    mapy = item.mapy,
                                    checkedGearIds = emptyList() // 체크 내역 초기화
                                )

                                val tempLogs = campLogs.toMutableMap()
                                tempLogs[selectedDate.toString()] = updatedLog

                                // 파일에 물리적으로 저장
                                saveCampLogs(context, tempLogs)

                                // 메모리(State)에도 반영
                                campLogs = tempLogs
                            }
                        }
                        // 3. 💡 [중요] 저장까지 끝났으니 바로 수정 모드 탈출!
                        isEditing = false
                        searchResults = emptyList()
                        showLocationConfirm = false
                    }
                ) {
                    Text("변경 및 초기화", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLocationConfirm = false
                    pendingItem = null

                    // 💡 핵심: 취소를 누르면 수정 모드를 종료하고 조회(카드) 모드로 돌아갑니다.
                    if (isEditing) {
                        isEditing = false
                        // 원래 장소명으로 복구 (저장 버튼을 안 눌러도 화면을 깔끔하게 유지하기 위함)
                        locationInput = currentLog?.location ?: ""
                    }

                    searchResults = emptyList() // 드롭다운도 닫기
                }) { Text("취소") }
            }
        )
    }

    // [다이얼로그] 지도 보기
    if (showMap) {
        AlertDialog(
            onDismissRequest = { showMap = false },
            confirmButton = { TextButton(onClick = { showMap = false }) { Text("닫기") } },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.8f),
            text = { Box(modifier = Modifier.fillMaxSize()) { CampingMapView(mapTargetLocation) } }
        )
    }

    // 💡 [추가] 현재 달력에서 보고 있는 날짜의 '월'을 계산
    // 스크롤이 멈춘 지점의 인덱스를 기반으로 해당 날짜의 연/월을 가져옵니다.
    val currentViewDate = remember {
        derivedStateOf {
            val index = calendarListState.firstVisibleItemIndex
            // -180부터 시작하는 리스트이므로 인덱스를 날짜로 변환
            LocalDate.now().plusDays((index - 180).toLong())
        }
    }

    // MainHomeScreen 내부 LaunchedEffect 등에서 사용
    val coords = GeoConverter.katechToWgs84(
        selectedSearchItem?.mapx ?: "",
        selectedSearchItem?.mapy ?: ""
    )

    if (coords != null) {
        val latitude = coords.first   // 위도 (37.xxxx)
        val longitude = coords.second // 경도 (126.xxxx)

        // 💡 이제 이 위경도를 가지고 날씨 API를 호출하면 됩니다!
    }

    // 상태 변수 확장
    var tempMax by remember { mutableStateOf("-") }
    var tempMin by remember { mutableStateOf("-") }
    var windMax by remember { mutableStateOf("-") }
    var windMin by remember { mutableStateOf("-") }


    // [수정] 앱 실행 시 딱 한 번만 오늘 날짜로 이동
    LaunchedEffect(Unit) {
        calendarListState.scrollToItem(180)
    }
    // --- [수정된 날씨 호출 로직] ---
    LaunchedEffect(selectedDate, selectedSearchItem) {
        // 💡 [추가] 날짜가 바뀌거나 화면이 갱신될 때 파일에서 로그를 새로 읽어옵니다.
        // 이렇게 하면 상세 화면에서 체크하고 돌아왔을 때 데이터가 딱 맞게 됩니다.
        campLogs = loadCampLogs(context)

        val today = LocalDate.now()

        // 1. 과거 날짜 처리
        if (selectedDate.isBefore(today)) {
            tempMax = "-"; tempMin = "-"; windMax = "-"; windMin = "-"
            return@LaunchedEffect
        }

        // 💡 핵심 수정: 검색된 아이템이 없더라도, 저장된 로그가 있다면 해당 좌표를 사용함
        val currentLog = campLogs[selectedDate.toString()]

        // 좌표 결정 우선순위: 1. 방금 검색한 아이템 -> 2. 이미 저장된 로그
        val targetMapX = selectedSearchItem?.mapx ?: currentLog?.mapx
        val targetMapY = selectedSearchItem?.mapy ?: currentLog?.mapy

        if (targetMapX != null && targetMapY != null) {
            val rawX = targetMapX.toDoubleOrNull() ?: 0.0
            val rawY = targetMapY.toDoubleOrNull() ?: 0.0

            // 네이버 좌표계 판별 및 변환 로직
            val (latitude, longitude) = if (rawX > 10000000) {
                Pair(rawY / 10000000.0, rawX / 10000000.0)
            } else {
                val coords = GeoConverter.katechToWgs84(targetMapX, targetMapY)
                if (coords != null) Pair(coords.first, coords.second) else Pair(0.0, 0.0)
            }

            if (latitude != 0.0 && longitude != 0.0) {
                try {
                    val response = weatherApi.getForecast(
                        lat = latitude,
                        lon = longitude,
                        apiKey = "27146ed0cf8609bb6f532dcd87488c8c"
                    )

                    val dailyData = response.list.filter { it.dt_txt.startsWith(selectedDate.toString()) }

                    if (dailyData.isNotEmpty()) {
                        tempMax = (dailyData.maxOfOrNull { it.main.temp_max } ?: 0.0).toInt().toString()
                        tempMin = (dailyData.minOfOrNull { it.main.temp_min } ?: 0.0).toInt().toString()
                        windMax = (dailyData.maxOfOrNull { it.wind.speed } ?: 0.0).toString()
                    } else {
                        tempMax = "-"; tempMin = "-"; windMax = "-"; windMin = "-"
                    }
                } catch (e: Exception) {
                    tempMax = "ERR"; tempMin = "ERR"
                }
            }
        } else {
            // 좌표 정보가 아예 없는 경우 (기록도 없고 검색도 안 함)
            tempMax = "-"; tempMin = "-"; windMax = "-"; windMin = "-"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        // 상단 인사말과 '오늘' 버튼을 한 줄에 배치
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ){
            Text(
                text = "${currentViewDate.value.year}년 ${currentViewDate.value.monthValue}월",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text("안녕하세요, 이종화님! 🏕️", style = MaterialTheme.typography.bodyMedium)

            // 💡 [추가] 오늘 날짜로 즉시 이동하는 버튼
            Button(
                onClick = {
                    selectedDate = LocalDate.now()
                    // 💡 [추가] 오늘 버튼 클릭 시 달력을 맨 앞(오늘)으로 스크롤
                    isEditing = false // 👈 오늘로 돌아갈 때도 수정 모드 해제!
                    scope.launch {
                        // 💡 [수정] 0번이 아니라 오늘 날짜인 180번 인덱스로 이동
                        // 약간의 여유를 위해 179번 정도로 보내면 오늘 날짜가 더 잘 보입니다.
                        calendarListState.animateScrollToItem(180)
                    }
                },
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("오늘", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // 주간 달력 UI
        // 💡 [수정] WeeklyCalendar에 listState를 전달합니다.
        WeeklyCalendar(
            selectedDate = selectedDate,
            hasLogDates = campLogs.keys,
            listState = calendarListState // state 전달
        ) { newDate -> // 💡 날짜가 선택되었을 때 실행되는 블록
            selectedDate = newDate
            isEditing = false // 👈 여기서 수정 모드를 해제합니다!
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 기온 정보
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Thermostat,
                        contentDescription = null,
                        tint = Color(0xFFFF5722), // 주황색 계열
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("기온 (최고/최저)", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = if (tempMax == "-") "-" else "${tempMax}° / ${tempMin}°",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (tempMax != "-") Color(0xFFFF5252) else Color.Black // 최고기온 빨간색 포인트
                        )
                    }
                }

                // 구분선
                Box(modifier = Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outlineVariant))

                // 풍속 정보
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Air,
                        contentDescription = null,
                        tint = Color(0xFF2196F3), // 파란색 계열
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("풍속 (최대)", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = if (windMax == "-") "-" else "${windMax} m/s",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (windMax != "-" && windMax.toDouble() > 7.0) Color.Red else Color.Black // 강풍 주의 표시
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val currentLog = campLogs[selectedDate.toString()]

        // 💡 기록이 없거나, 수정 버튼을 눌렀을 때 '입력창'을 보여줌
        if (currentLog == null || isEditing) {
            Text(
                text = if (isEditing) "${selectedDate} 기록 수정" else "${selectedDate}의 캠핑 기록",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // --- 입력 카드 영역 ---
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    )
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 캠핑장 검색 필드
                    OutlinedTextField(
                        value = locationInput,
                        onValueChange = {
                            locationInput = it
                            if (it.length >= 2) {
                                scope.launch {
                                    try {
                                        val response = naverApi.searchCamping(
                                            "8mtFAfTR89iqD77LO6us",
                                            "Wn0CK0Ie0Q",
                                            it
                                        )
                                        searchResults = response.items
                                    } catch (e: Exception) {
                                        searchResults = emptyList()
                                    }
                                }
                            } else {
                                searchResults = emptyList()
                            }
                        },
                        label = { Text("캠핑장 검색") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 검색 결과 드롭다운 표시
                    if (searchResults.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            searchResults.forEach { item ->
                                val cleanTitle = item.title.replace("<b>", "").replace("</b>", "")
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "$cleanTitle (${item.address})",
                                            fontSize = 12.sp
                                        )
                                    },
                                    onClick = {
                                        val cleanTitle = item.title.replace("<b>", "").replace("</b>", "")
                                        val hasChecked = currentLog?.checkedGearIds?.isNotEmpty() == true

                                        if (isEditing && hasChecked) {
                                            // 💡 중요: 여기서는 입력 필드(locationInput 등)를 절대 건드리지 않습니다.
                                            // 대신 "바꿀 녀석" 정보만 pendingItem에 보관하고 팝업을 띄웁니다.
                                            pendingItem = item
                                            showLocationConfirm = true
                                        } else {
                                            // 체크된 게 없을 때는 즉시 변경해도 무방합니다.
                                            locationInput = cleanTitle
                                            selectedSearchItem = item
                                            searchResults = emptyList()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 💡 [장비 선택 섹션] 그룹추가 & 개별추가 버튼
                    // 💡 수정 모드(isEditing)가 아닐 때만 이 섹션을 보여줍니다.
                    if (!isEditing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "🎒 장비 세팅: ${selectedGearIds.size}개",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.weight(1f))

                            // 그룹으로 불러오기 버튼
                            TextButton(onClick = { showGroupPicker = true }) {
                                Text("그룹 추가", fontSize = 12.sp)
                            }
                            // 개별로 하나씩 추가 버튼
                            TextButton(onClick = { showIndividualPicker = true }) {
                                Text("개별 추가", fontSize = 12.sp)
                            }
                        }
                    }

                    // 저장 및 공개 설정 로우
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isPublic) "🌍 공개" else "🔒 비공개", fontSize = 14.sp)
                            Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                        }
                        Button(onClick = {
                            if (locationInput.isNotBlank()) {
                                val existingLog = campLogs[selectedDate.toString()]

                                // 💡 [핵심 체크] 장소나 다른 주요 정보가 바뀌었는지 확인
                                val isLocationChanged = existingLog?.location != locationInput
                                // (필요하다면 공개여부나 장비 리스트 변경 여부도 체크 가능)

                                if (!isLocationChanged) {
                                    // 1. 장소가 그대로라면? 아무 작업 안 하고 그냥 수정 모드만 종료!
                                    isEditing = false
                                    locationInput = "" // 입력값 초기화
                                    selectedGearIds = emptySet()
                                    // Toast.makeText(context, "변경된 내용이 없어 그대로 유지합니다.", Toast.LENGTH_SHORT).show()
                                }else {
                                    // 2. 장소가 바뀌었다면? (신규 등록이거나 실제 변경인 경우)
                                    // 이때는 기존 로직대로 저장 (단, 체크 내역은 보존)
                                    val newLog = CampLog(
                                        date = selectedDate.toString(),
                                        location = locationInput,
                                        address = selectedSearchItem?.address ?: "",
                                        mapx = selectedSearchItem?.mapx ?: "",
                                        mapy = selectedSearchItem?.mapy ?: "",
                                        isPublic = isPublic,
                                        gearIds = selectedGearIds.toList() // 선택된 모든 장비 ID 저장
                                    )
                                    val currentLogs = loadCampLogs(context).toMutableMap()
                                    currentLogs[selectedDate.toString()] = newLog
                                    saveCampLogs(context, currentLogs)

                                    campLogs = currentLogs // UI 즉시 갱신
                                    locationInput = ""
                                    selectedGearIds = emptySet()
                                    isEditing = false // 👈 수정 완료 후 카드 뷰로 복귀
                                    Toast.makeText(context, "기록 저장 완료! ⛺", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }) { Text("저장") }
                    }
                }
            }
        } else {
            // 제목과 수정 버튼을 한 줄에 배치
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedDate}의 캠핑 계획",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 💡 수정 버튼 추가
                TextButton(
                    onClick = {
                        // 1. 현재 저장된 기록의 내용을 입력 필드 상태값들에 다시 채워넣음
                        locationInput = currentLog.location
                        selectedGearIds = currentLog.gearIds.toSet()
                        isPublic = currentLog.isPublic

                        // 2. 날씨 연동을 위해 검색 아이템 정보도 복구
                        selectedSearchItem = SearchResultItem(
                            title = currentLog.location,
                            address = currentLog.address,
                            roadAddress = currentLog.address,
                            mapx = currentLog.mapx,
                            mapy = currentLog.mapy
                        )

                        // 3. 핵심: campLogs에서 이 날짜를 잠시 제거하여 '등록 모드(if)'가 화면에 나오게 함
                        /*
                        val tempLogs = campLogs.toMutableMap()
                        tempLogs.remove(selectedDate.toString())
                        campLogs = tempLogs
                        */
                        // 2. 💡 화면 전환 (삭제하지 않고 상태만 변경)
                        isEditing = true

                        Toast.makeText(context, "수정 모드입니다. 내용을 고친 후 다시 저장해주세요.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("기록 수정", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 데이터 계산 (전체 대비 체크된 비율)
            val totalGear = currentLog.gearIds.size
            val packedGear = currentLog.checkedGearIds.size
            val progress = if (totalGear > 0) packedGear.toFloat() / totalGear else 0f
            val isComplete = progress == 1f && totalGear > 0

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDetailNotice = true }, // 💡 팝업 띄우기
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    // 완료 여부에 따라 배경색을 다르게 줌 (완료 시 연한 초록)
                    containerColor = if (isComplete) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "📍 ${currentLog.location}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = if (isComplete) Color(0xFF4CAF50) else Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 💡 진행률 바 (막대 그래프)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = if (isComplete) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 상태 요약 텍스트
                    Text(
                        text = if (isComplete) "패킹 완료! 이제 출발하세요 🎉" else "장비 $packedGear / $totalGear 체크됨 (${(progress * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isComplete) Color(0xFF2E7D32) else Color.DarkGray
                    )
                }
            }
        }
        // --- [진행률 카드 바로 아래에 추가] ---
        currentLog?.let { log ->
            // 1. 체크 안 된 장비들의 'ID 문자열'만 먼저 추출
            val remainingGearIds = log.gearIds.filter { id ->
                !log.checkedGearIds.contains(id)
            }

            // 2. 추출된 ID들을 객체(UserGear)로 변환
            val remainingGear = remainingGearIds.mapNotNull { id ->
                if (id.startsWith("custom|")) {
                    // 💡 [직접 입력 장비 처리] 문자열 쪼개서 임시 객체 생성
                    val parts = id.split("|")
                    com.company.camon.data.model.UserGear(
                        id = id.hashCode().toLong(), // 임시 ID
                        category = parts.getOrNull(1) ?: "기타",
                        brand = parts.getOrNull(2) ?: "",
                        modelName = parts.getOrNull(3) ?: "장비",
                        quantity = parts.getOrNull(4)?.toIntOrNull() ?: 1,
                        memo = parts.getOrNull(5) ?: ""
                    )
                } else {
                    // 💡 [내 장비 등록 장비 처리] DB에서 찾기
                    allGear.find { it.id.toString() == id.trim() }
                }
            }.sortedWith(
                compareBy<com.company.camon.data.model.UserGear> { gear ->
                    // 전체 리스트의 정렬 기준인 '단일 품목 우선'을 위해 전체 장비 중 카테고리 개수 파악
                    val isSingleInInternal = allGear.count { it.category == gear.category } == 1
                    if (isSingleInInternal) 0 else 1 // 단일 품목이면 0(앞), 아니면 1(뒤)
                }.thenBy { it.category }  // 그 다음 카테고리 가나다순
                    .thenBy { it.modelName } // 그 다음 모델명 가나다순
            )

            if (remainingGear.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                // 헤더 영역: 제목 + 전체보기 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "💡 잊으신 건 없나요?",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Gray
                    )
                    // 💡 [추가] 전체 체크리스트로 이동하는 버튼 (여기서 짐 싸기 화면으로!)
                    TextButton(
                        onClick = { onNavigateToLog(log.date) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("전체보기", fontSize = 12.sp)
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.heightIn(max = 300.dp)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(remainingGear) { gear ->
                            // 💡 카테고리에 따른 이모지 결정 로직 추가
                            val emoji = when (gear.category) {
                                "텐트" -> "⛺"
                                "타프" -> "⛱️"
                                "체어" -> "💺"
                                "테이블" -> "🪑"
                                "조명" -> "💡"
                                "침구" -> "🛌"
                                "취사" -> "🍳"
                                "화로대" -> "🔥"
                                "도구" -> "🧰"    // 💡 도구 전용 이모지 추가
                                "소모품" -> "🛒"  // 💡 소모품 전용 이모지 추가
                                else -> "📦"     // 기존 기타(🛠️)를 박스 아이콘으로 변경하면 더 깔끔합니다.
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 💡 기존 Inventory 아이콘 대신 이모지 텍스트를 넣습니다.
                                    Text(emoji, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // 브랜드와 모델명 표시
                                    Text(
                                        text = "${gear.brand} ${gear.modelName}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.weight(1f))

                                    // 💡 여기서 바로 체크하는 기능 (옵션)
                                    IconButton(
                                        onClick = {
                                            // 1. 현재 로그를 안전하게 가져옵니다.
                                            val currentLogData = campLogs[selectedDate.toString()]

                                            currentLogData?.let { log ->
                                                // 2. [핵심] 직접 입력 장비인지 확인하여 원본 ID(custom|...)를 찾습니다.
                                                val originalId = log.gearIds.find { id ->
                                                    if (id.startsWith("custom|")) {
                                                        val parts = id.split("|")
                                                        // 모델명과 브랜드가 일치하는지 확인
                                                        parts.getOrNull(3) == gear.modelName && parts.getOrNull(
                                                            2
                                                        ) == gear.brand
                                                    } else {
                                                        // 일반 장비는 숫자 ID 그대로 비교
                                                        id == gear.id.toString()
                                                    }
                                                } ?: ""

                                                if (originalId.isNotEmpty()) {
                                                    val updatedChecked =
                                                        log.checkedGearIds + originalId // 원본 문자열 저장!
                                                    val newLog =
                                                        log.copy(checkedGearIds = updatedChecked)
                                                    val updatedLogs = campLogs.toMutableMap()
                                                    updatedLogs[selectedDate.toString()] = newLog
                                                    saveCampLogs(context, updatedLogs)
                                                    campLogs = updatedLogs
                                                }

                                                // 3. 찾은 진짜 ID로 체크리스트 업데이트
                                                val updatedChecked = log.checkedGearIds + originalId
                                                val newLog =
                                                    log.copy(checkedGearIds = updatedChecked)

                                                val updatedLogs = campLogs.toMutableMap()
                                                updatedLogs[selectedDate.toString()] = newLog
                                                saveCampLogs(context, updatedLogs)

                                                // 4. UI 즉시 반영
                                                campLogs = updatedLogs
                                                Toast.makeText(
                                                    context,
                                                    "장비를 챙겼습니다! 🎒",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.RadioButtonUnchecked,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (remainingGear.size > 4) {
                    Text(
                        "외 ${remainingGear.size - 4}개의 장비가 더 있어요...",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            } else if (log.gearIds.isNotEmpty()) {
                // 모든 짐을 다 쌌을 때 나오는 메시지
                Spacer(modifier = Modifier.height(20.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉 완벽합니다! 모든 장비를 다 챙기셨어요.", fontSize = 14.sp, color = Color(0xFF2E7D32))
                }
            }
        }

    }



    // 1️⃣ 그룹 선택 창 (메인 화면용)
    if (showGroupPicker) {
        GearGroupPicker(
            allGroups = allGroups,
            onGroupSelected = { group ->
                // 메인에서는 파일 저장이 아니라, 현재 입력 중인 변수(selectedGearIds)에 합쳐줍니다.
                selectedGearIds = selectedGearIds + group.gearIds.toSet()
                showGroupPicker = false // 그룹은 선택 후 보통 닫음
            },
            onDismiss = { showGroupPicker = false }
        )
    }

    // 2️⃣ 개별 선택 창 (메인 화면용)
    if (showIndividualPicker) {
        IndividualGearPicker(
            allGear = allGear,
            alreadyAddedIds = selectedGearIds.toList(), // 현재까지 선택된 ID들 전달
            onGearSelected = { gear ->
                // 개별 장비를 선택할 때마다 세트에 추가
                selectedGearIds = selectedGearIds + gear.id.toString()
                Toast.makeText(context, "${gear.modelName} 선택됨", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showIndividualPicker = false }
        )
    }

    // 화면 하단에 다이얼로그 추가
    if (showDetailNotice) {
        AlertDialog(
            onDismissRequest = { showDetailNotice = false },
            title = { Text("준비 중인 기능") },
            text = { Text("캠핑장 상세 정보 서비스는 현재 준비 중입니다. 조금만 기다려 주세요! ⛺") },
            confirmButton = {
                TextButton(onClick = { showDetailNotice = false }) { Text("확인") }
            }
        )
    }
}

@Composable
fun WeeklyCalendar(
    selectedDate: LocalDate,
    hasLogDates: Set<String>,
    listState: LazyListState,
    onDateSelected: (LocalDate) -> Unit
) {
    // 💡 리스트 생성 시 오늘이 항상 0번째 인덱스가 되도록 조정 (선택 사항)
    // 💡 [수정] 오늘 기준 과거 180일 ~ 미래 180일 (약 1년 범위) 생성
    // 인덱스 180이 '오늘'이 됩니다.
    val days = remember { (-180..180).map { LocalDate.now().plusDays(it.toLong()) } }

    LazyRow(
        state = listState, // 💡 state 연결
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(days) { date ->
            val isSelected = date == selectedDate
            val hasLog = hasLogDates.contains(date.toString()) // 해당 날짜에 기록이 있는지 확인

            Surface(
                onClick = { onDateSelected(date) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .width(55.dp)
                    .height(85.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 요일 표시 (월, 화, 수...)
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                        fontSize = 12.sp,
                        color = if (isSelected) Color.White else Color.Gray
                    )
                    // 날짜 표시 (1, 2, 3...)
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color.Black
                    )
                    // 💡 기록이 있는 날짜는 하단에 빨간 점 표시
                    if (hasLog) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(6.dp)
                                .background(
                                    color = if (isSelected) Color.White else Color.Red,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

