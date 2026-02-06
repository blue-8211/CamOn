package com.company.camon.ui.gear

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.company.camon.data.db.CamonDatabase
import com.company.camon.data.model.GearGroup
import com.company.camon.util.loadGearGroups
import com.company.camon.util.saveGearGroups

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearGroupScreen(context: Context) {
    // 1. DB 및 데이터 관측 (Room DB 연동)
    val db = remember { CamonDatabase.getDatabase(context) }
    val gearDao = db.gearDao()
    val allUserGears by gearDao.getAllUserGears().collectAsState(initial = emptyList())
    var gearGroups by remember { mutableStateOf(loadGearGroups(context)) }

    // 2. 상태 관리
    var showAddNameDialog by remember { mutableStateOf(false) }
    var showGearSelectDialog by remember { mutableStateOf(false) }
    var currentEditingGroup by remember { mutableStateOf<GearGroup?>(null) }
    var newGroupName by remember { mutableStateOf("") }

    var selectedGearIds by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }

    // 3. UI 구성
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    newGroupName = ""
                    showAddNameDialog = true
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("새 그룹") },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("장비 그룹 🎒", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("캠핑 성격에 맞춰 장비를 묶어보세요.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

            Spacer(modifier = Modifier.height(24.dp))

            if (gearGroups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("등록된 그룹이 없습니다.\n자주 쓰는 장비를 세트로 묶어보세요.", textAlign = TextAlign.Center, color = Color.LightGray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(gearGroups) { group ->
                        GroupCard(
                            group = group,
                            onEdit = {
                                currentEditingGroup = group
                                selectedGearIds = group.gearIds.toSet()
                                searchQuery = ""
                                showGearSelectDialog = true
                            },
                            onDelete = {
                                val updatedList = gearGroups.filter { it.id != group.id }
                                gearGroups = updatedList
                                saveGearGroups(context, updatedList)
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
            title = { Text("새 그룹 생성", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("그룹명 (예: 백패킹)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
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
            title = { Text("${currentEditingGroup?.name} 구성하기", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 450.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("장비명 검색...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        val filteredList = allUserGears.filter { gear ->
                            gear.modelName.contains(searchQuery, ignoreCase = true) ||
                                    gear.brand.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredList.isEmpty()) {
                            item {
                                Text("검색 결과가 없습니다.", modifier = Modifier.fillMaxWidth().padding(20.dp), textAlign = TextAlign.Center, color = Color.Gray)
                            }
                        }

                        items(filteredList) { gear ->
                            val isChecked = selectedGearIds.contains(gear.id.toString())
                            val emoji = when(gear.category) {
                                "텐트" -> "⛺" "체어" -> "💺" "테이블" -> "🪑" "조명" -> "💡" else -> "🛠️"
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedGearIds = if (isChecked) selectedGearIds - gear.id.toString()
                                        else selectedGearIds + gear.id.toString()
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Checkbox(checked = isChecked, onCheckedChange = null)
                                Text(emoji, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 8.dp))
                                Column {
                                    Text(gear.modelName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text("${gear.brand} | ${gear.category}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updatedGroup = currentEditingGroup!!.copy(gearIds = selectedGearIds.toList())
                    val newList = if (gearGroups.any { it.id == updatedGroup.id }) {
                        gearGroups.map { if (it.id == updatedGroup.id) updatedGroup else it }
                    } else {
                        gearGroups + updatedGroup
                    }
                    saveGearGroups(context, newList)
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

@Composable
fun GroupCard(group: GearGroup, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Icons.Default.Backpack 대신 기본 제공되는 List 사용 (에러 방지)
                Icon(Icons.Default.List, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("장비 ${group.gearIds.size}개 담김", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color.LightGray.copy(alpha = 0.6f))
            }
        }
    }
}