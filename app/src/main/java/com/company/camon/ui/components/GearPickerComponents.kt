package com.company.camon.ui.component

import android.widget.Toast
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
// 💡 GearItem 모델을 임포트합니다. 경로가 맞는지 확인해주세요!
import com.company.camon.data.model.GearItem
import com.company.camon.data.model.GearGroup

// 1️⃣ 그룹 선택 다이얼로그 (이전과 동일)
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
                Text("장비 그룹 추가", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(allGroups) { group ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onGroupSelected(group) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GridView, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(group.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("장비 ${group.gearIds.size}개 포함", fontSize = 12.sp, color = Color.Gray)
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

// 2️⃣ 개별 장비 선택 다이얼로그 (GearItem 적용)
@Composable
fun IndividualGearPicker(
    allGear: List<GearItem>, // 💡 Gear -> GearItem 수정
    alreadyAddedIds: List<String>,
    onGearSelected: (GearItem) -> Unit, // 💡 Gear -> GearItem 수정
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredGear = allGear.filter {
        it.name.contains(searchQuery, ignoreCase = true) && !alreadyAddedIds.contains(it.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("장비 개별 추가", fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
                    placeholder = { Text("장비 검색", fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredGear) { gear ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onGearSelected(gear) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AddCircleOutline, null, tint = Color.Gray)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(gear.name, fontWeight = FontWeight.SemiBold)
                                    // 💡 GearItem에 brand 필드가 있는지 확인해보세요!
                                    Text(gear.brand, fontSize = 12.sp, color = Color.Gray)
                                }
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