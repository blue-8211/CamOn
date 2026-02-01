package com.company.camon.ui.gear

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.company.camon.data.model.GearGroup
import com.company.camon.data.model.GearItem
import com.company.camon.util.loadGearGroups
import com.company.camon.util.saveGearGroups
import com.company.camon.util.loadGearList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearGroupScreen(context: Context) {
    // --- 1. 데이터 관측 ---
    var gearGroups by remember { mutableStateOf(loadGearGroups(context)) }
    val allGearList = remember { loadGearList(context) }

    // --- 2. 상태 관리 ---
    var showAddNameDialog by remember { mutableStateOf(false) }
    var showGearSelectDialog by remember { mutableStateOf(false) }
    var currentEditingGroup by remember { mutableStateOf<GearGroup?>(null) }
    var newGroupName by remember { mutableStateOf("") }

    // 장비 선택용 임시 상태
    var selectedGearIds by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }

    // --- 3. UI 구성 ---
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("장비 그룹 🎒", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = {
                newGroupName = ""
                showAddNameDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (gearGroups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("등록된 그룹이 없습니다.\n자주 쓰는 장비를 묶어보세요.", textAlign = TextAlign.Center, color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(gearGroups) { group ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            currentEditingGroup = group
                            selectedGearIds = group.gearIds.toSet()
                            searchQuery = "" // 편집 시작 시 검색어 초기화
                            showGearSelectDialog = true
                        }
                    ) {
                        ListItem(
                            headlineContent = { Text(group.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("포함 장비: ${group.gearIds.size}개") },
                            trailingContent = {
                                // 💡 삭제 버튼 추가
                                IconButton(onClick = {
                                    // 해당 그룹 제외하고 다시 저장
                                    val updatedList = gearGroups.filter { it.id != group.id }
                                    gearGroups = updatedList
                                    saveGearGroups(context, updatedList)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.Gray)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // --- [다이얼로그 1] 이름 설정 ---
    if (showAddNameDialog) {
        AlertDialog(
            onDismissRequest = { showAddNameDialog = false },
            title = { Text("새 그룹 생성") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("그룹명 (예: 백패킹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newGroupName.isNotBlank()) {
                        currentEditingGroup = GearGroup(name = newGroupName)
                        selectedGearIds = emptySet()
                        showAddNameDialog = false
                        showGearSelectDialog = true
                    }
                }) { Text("다음") }
            }
        )
    }

    // --- [다이얼로그 2] 장비 검색 및 다중 선택 ---
    if (showGearSelectDialog && currentEditingGroup != null) {
        AlertDialog(
            onDismissRequest = { showGearSelectDialog = false },
            title = { Text("${currentEditingGroup?.name} 장비 구성") },
            text = {
                Column(modifier = Modifier.heightIn(max = 450.dp)) {
                    // 🔍 검색 필드
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("장비명 검색...", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 💡 필터링 로직: 검색어에 맞거나 이미 선택된 아이템만 표시
                    val filteredList = allGearList.filter { gear ->
                        gear.name.contains(searchQuery, ignoreCase = true) ||
                                gear.brand.contains(searchQuery, ignoreCase = true) ||
                                selectedGearIds.contains(gear.id)
                    }.sortedByDescending { selectedGearIds.contains(it.id) } // 선택된 아이템을 상단으로

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (filteredList.isEmpty()) {
                            item {
                                Text(
                                    "검색 결과가 없습니다.",
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp, color = Color.Gray
                                )
                            }
                        }

                        items(filteredList) { gear ->
                            val isChecked = selectedGearIds.contains(gear.id)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedGearIds = if (isChecked) selectedGearIds - gear.id // 이름이 아니라 ID여야 함
                                        else selectedGearIds + gear.id
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            ) {
                                Checkbox(checked = isChecked, onCheckedChange = null)
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(gear.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("${gear.brand} | ${gear.category}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updatedGroup = currentEditingGroup!!.copy(gearIds = selectedGearIds.toList())

                    // 1. 최신 리스트 생성
                    val newList = if (gearGroups.any { it.id == updatedGroup.id }) {
                        gearGroups.map { if (it.id == updatedGroup.id) updatedGroup else it }
                    } else {
                        gearGroups + updatedGroup
                    }

                    // 2. 파일에 즉시 저장 (Context를 넘겨서 확실히 저장)
                    saveGearGroups(context, newList)

                    // 3. 현재 화면의 상태값도 갱신 (그래야 탭 안에서 바로 보임)
                    gearGroups = newList

                    showGearSelectDialog = false
                    currentEditingGroup = null
                }) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { showGearSelectDialog = false }) { Text("취소") }
            }
        )
    }
}