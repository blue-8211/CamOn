package com.company.camon.ui.log

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.company.camon.util.loadCampLogs
import com.company.camon.util.loadGearList
import com.company.camon.util.saveCampLogs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingLogScreen(context: Context, date: String, onBack: () -> Unit) {
    // --- [1. 상태 관리 변수] ---
    // 현재 날짜의 로그 데이터를 불러옵니다.
    var campLog by remember { mutableStateOf(loadCampLogs(context)[date]) }
    val allGear = remember { loadGearList(context) }

    // 장비 추가 다이얼로그 및 검색어 상태
    var showIndividualPicker by remember { mutableStateOf(false) }
    var gearSearchQuery by remember { mutableStateOf("") }

    // 체크박스 상태 (저장된 리스트를 가져와서 관리)
    var checkedGearIds by remember { mutableStateOf(campLog?.checkedGearIds?.toSet() ?: emptySet()) }

    // 현재 로그의 gearIds에 포함된 장비들만 필터링하여 메인 리스트 구성
    val matchingGear = allGear.filter { gear -> campLog?.gearIds?.contains(gear.id) == true }

    // 💡 공통으로 사용할 저장 함수를 내부에서 정의하거나 로직을 합칩니다.
    val toggleGearCheck: (String, Boolean) -> Unit = { gearId, shouldCheck ->
        val newSet = checkedGearIds.toMutableSet()
        if (shouldCheck) newSet.add(gearId) else newSet.remove(gearId)

        // 1. UI 상태 변경
        checkedGearIds = newSet

        // 2. 즉시 파일 저장 로직 추가
        campLog?.let { currentLog ->
            val updatedLog = currentLog.copy(checkedGearIds = newSet.toList())
            val allLogs = loadCampLogs(context).toMutableMap()
            allLogs[date] = updatedLog
            saveCampLogs(context, allLogs)

            // 3. 현재 로그 상태도 동기화 (메인 화면으로 돌아갔을 때 반영되도록)
            campLog = updatedLog
        }
    }

    // --- [1. 상태 관리 변수 영역에 추가] ---
    var showGroupPicker by remember { mutableStateOf(false) } // 그룹 선택 창 열림 여부
    val allGroups = remember { com.company.camon.util.loadGearGroups(context) } // 모든 그룹 불러오기

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(campLog?.location ?: "상세 기록", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(date, fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기") }
                },
                actions = {}
            )
        }
    ) { padding ->
        if (campLog == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("해당 날짜의 기록이 없습니다.")
            }
        } else {
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {

                // --- [2. 체크리스트 헤더 및 진행률] ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("짐 챙기기 체크리스트", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${checkedGearIds.size} / ${matchingGear.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))

                // --- [3. 메인 체크리스트 (삭제 기능 포함)] ---
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    if (matchingGear.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("등록된 장비가 없습니다.\n우측 상단 + 버튼으로 추가하세요.", fontSize = 13.sp, color = Color.Gray)
                        }
                    } else {
                        LazyColumn {
                            items(matchingGear) { gear ->
                                val isChecked = checkedGearIds.contains(gear.id)
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            gear.name,
                                            color = if (isChecked) Color.Gray else Color.Unspecified,
                                            fontWeight = if (isChecked) FontWeight.Normal else FontWeight.Medium
                                        )
                                    },
                                    supportingContent = { Text(gear.brand, fontSize = 12.sp) },
                                    leadingContent = {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                toggleGearCheck(gear.id, checked) // 💡 자동 저장 함수 호출
                                            }
                                        )
                                    },
                                    // 💡 [핵심] 우측 삭제 버튼 추가
                                    trailingContent = {
                                        IconButton(onClick = {
                                            campLog?.let { currentLog ->
                                                // 1. gearIds와 checkedGearIds에서 모두 해당 장비 제거
                                                val updatedGearIds = currentLog.gearIds.filterNot { it == gear.id }
                                                val updatedCheckedIds = checkedGearIds.filterNot { it == gear.id }

                                                // 2. 객체 업데이트 및 파일 저장
                                                val updatedLog = currentLog.copy(
                                                    gearIds = updatedGearIds,
                                                    checkedGearIds = updatedCheckedIds
                                                )
                                                val allLogs = loadCampLogs(context).toMutableMap()
                                                allLogs[date] = updatedLog
                                                saveCampLogs(context, allLogs)

                                                // 3. UI 상태 즉시 반영
                                                campLog = updatedLog
                                                checkedGearIds = updatedCheckedIds.toSet()
                                                Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.LightGray)
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        toggleGearCheck(gear.id, !isChecked)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- [4. 최종 저장 버튼] ---
                // --- [4. 하단 액션 버튼: 그룹 및 개별 장비 추가] ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp) // 버튼 사이 간격
                ) {
                    // 1️⃣ 그룹 장비 추가 버튼
                    Button(
                        onClick = { showGroupPicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("장비그룹 추가", fontSize = 14.sp)
                    }

                    // 2️⃣ 개별 장비 추가 버튼
                    Button(
                        onClick = { showIndividualPicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("장비개별 추가", fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // --- [5. 추가 장비 선택 다이얼로그] ---
    if (showIndividualPicker) {
        AlertDialog(
            onDismissRequest = {
                showIndividualPicker = false
                gearSearchQuery = "" // 닫힐 때 검색어 초기화
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("장비 개별 추가", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    // 💡 완료 버튼 대신 우측 상단 X 아이콘
                    IconButton(onClick = {
                        showIndividualPicker = false
                        gearSearchQuery = ""
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1️⃣ 검색창 디자인 개선
                    OutlinedTextField(
                        value = gearSearchQuery,
                        onValueChange = { gearSearchQuery = it },
                        placeholder = { Text("어떤 장비를 찾으시나요?", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    // 2️⃣ 검색 결과 리스트 (카드 스타일)
                    val availableGear = allGear.filter { gear ->
                        val matchesSearch = gear.name.contains(gearSearchQuery, ignoreCase = true)
                        val isAlreadyAdded = campLog?.gearIds?.contains(gear.id) ?: false
                        matchesSearch && !isAlreadyAdded
                    }

                    if (availableGear.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("찾으시는 장비가 없어요 😅", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableGear) { gear ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        campLog?.let { log ->
                                            val newGearIds = log.gearIds + gear.id
                                            val updatedLog = log.copy(gearIds = newGearIds)
                                            val allLogs = loadCampLogs(context).toMutableMap()
                                            allLogs[date] = updatedLog
                                            saveCampLogs(context, allLogs)
                                            campLog = updatedLog
                                            // 💡 추가 팁: 토스트 메시지로 추가 알림 주기
                                            Toast.makeText(context, "${gear.name} 추가됨!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.AddCircleOutline,
                                            null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(gear.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            if (gear.brand.isNotEmpty()) {
                                                Text(gear.brand, fontSize = 12.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
            shape = RoundedCornerShape(24.dp)
        )
    }

    // --- [다이얼로그: 장비 그룹 불러오기] ---
    if (showGroupPicker) {
        AlertDialog(
            onDismissRequest = { showGroupPicker = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("장비 그룹 추가", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    // 💡 개별 추가와 동일하게 상단 X 버튼 배치
                    IconButton(onClick = { showGroupPicker = false }) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }
            },
            text = {
                // 💡 세로 길이를 적절히 조절하고 스크롤 가능하게 설정
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp) // 카드 사이 간격
                ) {
                    items(allGroups) { group ->
                        // 💡 기본 ListItem 대신 커스텀 카드 사용
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                campLog?.let { log ->
                                    val newGearIds = (log.gearIds.toSet() + group.gearIds.toSet()).toList()
                                    val updatedLog = log.copy(gearIds = newGearIds)
                                    val allLogs = loadCampLogs(context).toMutableMap()
                                    allLogs[date] = updatedLog
                                    saveCampLogs(context, allLogs)
                                    campLog = updatedLog
                                    showGroupPicker = false
                                    Toast.makeText(context, "${group.name} 추가 완료!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = group.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "장비 ${group.gearIds.size}개 포함",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            shape = RoundedCornerShape(24.dp) // 다이얼로그 모서리도 더 둥글게
        )
    }
}