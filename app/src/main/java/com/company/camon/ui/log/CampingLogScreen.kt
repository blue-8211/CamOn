package com.company.camon.ui.log

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.company.camon.data.model.GearItem
import com.company.camon.ui.component.GearGroupPicker
import com.company.camon.ui.component.IndividualGearPicker
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

                // --- [2. 체크리스트 헤더 및 진행률 개선] ---
                val totalGear = matchingGear.size
                val packedGear = checkedGearIds.size
                val progress = if (totalGear > 0) packedGear.toFloat() / totalGear else 0f

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom // 텍스트와 숫자의 밑선을 맞춤
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (progress == 1f) Icons.Default.CheckCircle else Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = if (progress == 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "준비 현황",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 💡 숫자 표기 (강조)
                        Text(
                            text = "$packedGear / $totalGear",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 💡 시각적 진행률 바 추가
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = if (progress == 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    // 퍼센트 텍스트 (우측 하단 소형)
                    Text(
                        text = "${(progress * 100).toInt()}% 완료",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- [3. 메인 체크리스트 (삭제 기능 포함)] ---
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    if (matchingGear.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("등록된 장비가 없습니다.", fontSize = 13.sp, color = Color.Gray)
                        }
                    } else {
                        // 💡 체크 안 된 장비(false)가 위로, 체크 된 장비(true)가 아래로 오도록 정렬
                        val sortedGear = matchingGear.sortedWith(
                            compareBy<GearItem> { gear ->
                                // 1순위: 체크 여부 (체크 안 된 것이 위로)
                                checkedGearIds.contains(gear.id)
                            }.thenBy { gear ->
                                // 2순위: 카테고리명 (ㄱㄴㄷ 순으로 모아서 보여줌)
                                gear.category
                            }.thenBy { gear ->
                                // 3순위: 이름 (같은 카테고리 내에서 이름순)
                                gear.name
                            }
                        )

                        LazyColumn {
                            // 💡 matchingGear 대신 sortedGear를 사용합니다.
                            items(sortedGear, key = { it.id }) { gear ->
                                val isChecked = checkedGearIds.contains(gear.id)

                                ListItem(
                                    headlineContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // 💡 카테고리 태그 추가
                                            Surface(
                                                color = if (isChecked) Color.LightGray.copy(alpha = 0.3f)
                                                else MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = gear.category,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isChecked) Color.Gray else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            // 장비 이름
                                            Text(
                                                text = gear.name,
                                                color = if (isChecked) Color.Gray else Color.Unspecified,
                                                textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                                fontWeight = if (isChecked) FontWeight.Normal else FontWeight.Medium
                                            )
                                        }
                                    },
                                    supportingContent = {
                                        Text(gear.brand, fontSize = 11.sp, modifier = Modifier.padding(start = 42.dp))
                                    },
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

    // 1️⃣ 공통 그룹 선택 창 호출
    if (showGroupPicker) {
        GearGroupPicker(
            allGroups = allGroups,
            onGroupSelected = { group ->
                campLog?.let { log ->
                    val newGearIds = (log.gearIds.toSet() + group.gearIds.toSet()).toList()
                    // 즉시 저장 로직
                    val updatedLog = log.copy(gearIds = newGearIds)
                    val allLogs = loadCampLogs(context).toMutableMap()
                    allLogs[date] = updatedLog
                    saveCampLogs(context, allLogs)

                    campLog = updatedLog // UI 갱신
                    showGroupPicker = false // 그룹 추가 후 닫기
                    Toast.makeText(context, "${group.name} 그룹이 추가되었습니다!", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showGroupPicker = false }
        )
    }

// 2️⃣ 공통 개별 장비 선택 창 호출
    if (showIndividualPicker) {
        IndividualGearPicker(
            allGear = allGear,
            alreadyAddedIds = campLog?.gearIds ?: emptyList(),
            onGearSelected = { gear ->
                campLog?.let { log ->
                    val newGearIds = log.gearIds + gear.id
                    // 즉시 저장 로직
                    val updatedLog = log.copy(gearIds = newGearIds)
                    val allLogs = loadCampLogs(context).toMutableMap()
                    allLogs[date] = updatedLog
                    saveCampLogs(context, allLogs)

                    campLog = updatedLog // UI 갱신
                    // 💡 개별 추가는 창을 닫지 않고 연속 추가 가능하게 유지!
                    Toast.makeText(context, "${gear.name} 추가됨", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                showIndividualPicker = false
                gearSearchQuery = "" // 검색어 초기화
            }
        )
    }
}