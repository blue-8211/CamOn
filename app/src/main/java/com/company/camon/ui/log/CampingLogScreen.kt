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
                actions = {
                    // 💡 장비 추가 버튼
                    IconButton(onClick = { showIndividualPicker = true }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "장비 추가", tint = MaterialTheme.colorScheme.primary)
                    }
                }
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
                                                val newSet = checkedGearIds.toMutableSet()
                                                if (checked) newSet.add(gear.id) else newSet.remove(gear.id)
                                                checkedGearIds = newSet
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
                                        val newSet = checkedGearIds.toMutableSet()
                                        if (isChecked) newSet.remove(gear.id) else newSet.add(gear.id)
                                        checkedGearIds = newSet
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- [4. 최종 저장 버튼] ---
                Button(
                    onClick = {
                        campLog?.let { currentLog ->
                            val updatedLog = currentLog.copy(checkedGearIds = checkedGearIds.toList())
                            val allLogs = loadCampLogs(context).toMutableMap()
                            allLogs[date] = updatedLog
                            saveCampLogs(context, allLogs)
                            campLog = updatedLog
                            Toast.makeText(context, "체크리스트가 저장되었습니다! 🏕️", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("체크리스트 저장")
                }
            }
        }
    }

    // --- [5. 추가 장비 선택 다이얼로그] ---
    if (showIndividualPicker) {
        AlertDialog(
            onDismissRequest = { showIndividualPicker = false },
            title = { Text("추가로 챙길 장비") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    OutlinedTextField(
                        value = gearSearchQuery,
                        onValueChange = { gearSearchQuery = it },
                        placeholder = { Text("장비 검색") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 💡 이미 포함된 장비는 검색 목록에서 제외
                    val availableGear = allGear.filter { gear ->
                        val matchesSearch = gear.name.contains(gearSearchQuery, ignoreCase = true)
                        val isAlreadyAdded = campLog?.gearIds?.contains(gear.id) ?: false
                        matchesSearch && !isAlreadyAdded
                    }

                    LazyColumn {
                        items(availableGear) { gear ->
                            ListItem(
                                headlineContent = { Text(gear.name) },
                                leadingContent = { Icon(Icons.Default.AddCircleOutline, null, tint = Color.Gray) },
                                modifier = Modifier.clickable {
                                    campLog?.let { log ->
                                        val newGearIds = log.gearIds + gear.id
                                        val updatedLog = log.copy(gearIds = newGearIds)
                                        val allLogs = loadCampLogs(context).toMutableMap()
                                        allLogs[date] = updatedLog
                                        saveCampLogs(context, allLogs)
                                        campLog = updatedLog
                                        // 팝업 안 닫고 연속 추가 가능
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showIndividualPicker = false; gearSearchQuery = "" }) { Text("완료") }
            }
        )
    }
}