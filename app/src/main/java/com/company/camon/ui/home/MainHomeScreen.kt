package com.company.camon.ui.home

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.company.camon.data.model.CampLog
import com.company.camon.data.network.SearchResultItem
import com.company.camon.data.network.naverApi
import com.company.camon.ui.components.CampingMapView
import com.company.camon.util.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

@Composable
fun MainHomeScreen(context: Context, onNavigateToLog: (String) -> Unit) {
    // --- 1. 상태 관리 변수들 ---
    var selectedDate by remember { mutableStateOf(LocalDate.now()) } // 선택된 날짜
    var locationInput by remember { mutableStateOf("") } // 캠핑장 검색어 입력값
    var isPublic by remember { mutableStateOf(false) } // 공개/비공개 스위치
    var campLogs by remember { mutableStateOf(loadCampLogs(context)) } // 전체 캠핑 로그 데이터
    val scope = rememberCoroutineScope()

    // --- 2. 장비 관련 상태 ---
    val allGroups = remember { loadGearGroups(context) } // 저장된 모든 장비 그룹
    val allGear = remember { loadGearList(context) }     // 저장된 모든 개별 장비 리스트
    var selectedGearIds by remember { mutableStateOf(setOf<String>()) } // 현재 선택된 장비 ID들 (중복방지 Set)

    // --- 3. 다이얼로그 제어 상태 ---
    var showGroupPicker by remember { mutableStateOf(false) }      // 그룹 선택 창 열림 여부
    var showIndividualPicker by remember { mutableStateOf(false) } // 개별 장비 선택 창 열림 여부
    var gearSearchQuery by remember { mutableStateOf("") }        // 개별 장비 선택 창 내 검색어
    var showMap by remember { mutableStateOf(false) }              // 지도 팝업 여부

    // --- 4. 검색 관련 상태 ---
    var mapTargetLocation by remember { mutableStateOf("") }
    var selectedSearchItem by remember { mutableStateOf<SearchResultItem?>(null) }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }

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

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("안녕하세요, 이종화님! 🏕️", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))

        // 주간 달력 UI
        WeeklyCalendar(selectedDate, campLogs.keys) { selectedDate = it }
        Spacer(modifier = Modifier.height(30.dp))

        Text("${selectedDate}의 캠핑 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // --- 입력 카드 영역 ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                                    val response = naverApi.searchCamping("8mtFAfTR89iqD77LO6us", "Wn0CK0Ie0Q", it)
                                    searchResults = response.items
                                } catch (e: Exception) { searchResults = emptyList() }
                            }
                        } else { searchResults = emptyList() }
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
                                text = { Text("$cleanTitle (${item.address})", fontSize = 12.sp) },
                                onClick = {
                                    locationInput = cleanTitle
                                    selectedSearchItem = item
                                    searchResults = emptyList()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 💡 [장비 선택 섹션] 그룹추가 & 개별추가 버튼
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎒 장비 세팅: ${selectedGearIds.size}개", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                            Toast.makeText(context, "기록 저장 완료! ⛺", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("저장") }
                }
            }
        }

        // --- 하단: 저장된 일정 표시 카드 ---
        campLogs[selectedDate.toString()]?.let { log ->
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onNavigateToLog(log.date) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                ListItem(
                    headlineContent = { Text("📍 ${log.location}", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("가져간 장비: ${log.gearIds.size}개 (체크리스트 보기)") },
                    trailingContent = { Icon(Icons.Default.KeyboardArrowRight, null) }
                )
            }
        }
    }

    // --- [다이얼로그] 1. 장비 그룹 선택 ---
    if (showGroupPicker) {
        AlertDialog(
            onDismissRequest = { showGroupPicker = false },
            title = { Text("장비 그룹 불러오기") },
            text = {
                LazyColumn {
                    items(allGroups) { group ->
                        ListItem(
                            headlineContent = { Text(group.name) },
                            supportingContent = { Text("장비 ${group.gearIds.size}개 포함") },
                            modifier = Modifier.clickable {
                                // 기존 리스트에 그룹 내 ID들을 합침 (중복 자동 제거)
                                selectedGearIds = selectedGearIds + group.gearIds.toSet()
                                showGroupPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGroupPicker = false }) { Text("취소") } }
        )
    }

    // --- [다이얼로그] 2. 개별 장비 선택 (검색 기능 포함) ---
    if (showIndividualPicker) {
        AlertDialog(
            onDismissRequest = { showIndividualPicker = false },
            title = { Text("장비 개별 추가") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    OutlinedTextField(
                        value = gearSearchQuery,
                        onValueChange = { gearSearchQuery = it },
                        placeholder = { Text("장비 이름/브랜드 검색") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val filteredGear = allGear.filter {
                        it.name.contains(gearSearchQuery, ignoreCase = true) ||
                                it.brand.contains(gearSearchQuery, ignoreCase = true)
                    }

                    LazyColumn {
                        items(filteredGear) { gear ->
                            val isChecked = selectedGearIds.contains(gear.id)
                            ListItem(
                                headlineContent = { Text(gear.name) },
                                supportingContent = { Text(gear.brand) },
                                leadingContent = {
                                    Checkbox(checked = isChecked, onCheckedChange = null)
                                },
                                modifier = Modifier.clickable {
                                    // 토글 로직: 있으면 빼고 없으면 넣기
                                    selectedGearIds = if (isChecked) selectedGearIds - gear.id
                                    else selectedGearIds + gear.id
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showIndividualPicker = false
                    gearSearchQuery = "" // 다이얼로그 닫을 때 검색어 초기화
                }) { Text("완료") }
            }
        )
    }
}

@Composable
fun WeeklyCalendar(
    selectedDate: LocalDate,
    hasLogDates: Set<String>,
    onDateSelected: (LocalDate) -> Unit
) {
    // 현재 날짜 기준으로 전후 7일씩 보여주는 리스트 생성
    val days = remember { (-7..7).map { LocalDate.now().plusDays(it.toLong()) } }

    LazyRow(
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