package com.company.camon.ui.component

import androidx.compose.foundation.BorderStroke
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
// 💡 UserGear 모델을 임포트합니다!
import com.company.camon.data.model.UserGear
import com.company.camon.data.model.GearGroup

// 1️⃣ 그룹 선택 다이얼로그 (디자인 리뉴얼)
@Composable
fun GearGroupPicker(
    allGroups: List<GearGroup>,
    onGroupSelected: (GearGroup) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("장비 그룹 추가 🎒", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(allGroups) { group ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onGroupSelected(group) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shadowElevation = 2.dp
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(group.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("장비 ${group.gearIds.size}개 세트", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        shape = RoundedCornerShape(24.dp)
    )
}

// 2️⃣ 개별 장비 선택 다이얼로그 (UserGear 연동 및 디자인 통일)
@Composable
fun IndividualGearPicker(
    allGear: List<UserGear>, // 💡 GearItem -> UserGear로 변경
    alreadyAddedIds: List<String>,
    onGearSelected: (UserGear) -> Unit, // 💡 GearItem -> UserGear로 변경
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    // 💡 it.name -> it.modelName으로 변경하고 ID 비교 로직 추가
    val filteredGear = allGear.filter {
        it.modelName.contains(searchQuery, ignoreCase = true) &&
                !alreadyAddedIds.contains(it.id.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("장비 개별 추가 ⛺", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                IconButton(onClick = {
                    onDismiss()
                    searchQuery = ""
                }) { Icon(Icons.Default.Close, null) }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("장비명 또는 브랜드 검색", fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredGear) { gear ->
                        val emoji = when(gear.category) {
                            "텐트" -> "⛺" "체어" -> "💺" "테이블" -> "🪑" "조명" -> "💡" else -> "🛠️"
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onGearSelected(gear) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(emoji, fontSize = 20.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(gear.modelName, fontWeight = FontWeight.SemiBold) // 💡 name -> modelName
                                    Text("${gear.brand} | ${gear.category}", fontSize = 12.sp, color = Color.Gray)
                                }
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        shape = RoundedCornerShape(24.dp)
    )
}