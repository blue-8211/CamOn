package com.company.camon.ui.log

import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.company.camon.data.db.CamonDatabase // 💡 추가
import com.company.camon.data.model.UserGear // 💡 GearItem 대신 UserGear
import com.company.camon.ui.component.GearGroupPicker
import com.company.camon.ui.component.IndividualGearPicker
import com.company.camon.util.loadCampLogs
import com.company.camon.util.loadGearList
import com.company.camon.util.saveCampLogs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingLogScreen(context: Context, date: String, onBack: () -> Unit) {
    // --- [1. 데이터 및 DB 관측] ---
    val db = remember { CamonDatabase.getDatabase(context) }
    val gearDao = db.gearDao()
    // 💡 [수정] Room DB에서 실시간 장비 리스트를 가져옵니다.
    val allGear by gearDao.getAllUserGears().collectAsState(initial = emptyList())
    // 기존처럼 campLog를 사용합니다.
    var campLog by remember { mutableStateOf(loadCampLogs(context)[date]) }

    // 💡 [핵심] checkedGearIds는 변수가 아니라 campLog에서 실시간으로 읽어오는 '창문' 역할만 합니다.
    val checkedGearIds = remember(campLog) {
        campLog?.checkedGearIds?.toSet() ?: emptySet()
    }

// 화면 진입 시 최신화 (메인 화면 반영용)
    LaunchedEffect(Unit) {
        campLog = loadCampLogs(context)[date]
    }

    // 장비 추가 다이얼로그 및 검색어 상태
    var showIndividualPicker by remember { mutableStateOf(false) }
    var gearSearchQuery by remember { mutableStateOf("") }

    var showQuickAdd by remember { mutableStateOf(false) } // 직접 입력 다이얼로그
    val scope = rememberCoroutineScope()

    // 현재 로그의 gearIds에 포함된 장비들만 필터링하여 메인 리스트 구성
    // 💡 [수정] matchingGear 타입을 UserGear로 변경하고 ID 매칭 로직 보강
    val matchingGear = remember(allGear, campLog) {
        campLog?.gearIds?.mapNotNull { id ->
            if (id.startsWith("custom|")) {
                // 1. 직접 입력(리스트만 추가)인 경우: ID 문자열을 쪼개서 임시 객체 생성
                val parts = id.split("|")
                UserGear(
                    id = id.hashCode().toLong(), // 중복 방지용 임시 ID
                    category = parts.getOrNull(1) ?: "기타",
                    brand = parts.getOrNull(2) ?: "",
                    modelName = parts.getOrNull(3) ?: "장비",
                    quantity = parts.getOrNull(4)?.toIntOrNull() ?: 1,
                    memo = parts.getOrNull(5) ?: ""
                )
            } else {
                // 💡 .trim()을 추가하여 공백으로 인한 매칭 실패 방지
                val cleanId = id.trim()
                // 2. 창고에 있는 장비(숫자 ID)인 경우: DB(allGear)에서 찾음
                allGear.find { it.id.toString() == cleanId }
            }
        } ?: emptyList()
    }

    val toggleGearCheck: (String, Boolean) -> Unit = { gearId, shouldCheck ->
        // 1. 파일에서 전체 데이터를 즉시 읽어옵니다.
        val allLogs = loadCampLogs(context).toMutableMap()
        val currentLog = allLogs[date]

        currentLog?.let { log ->
            // 2. 체크 상태 업데이트 (MutableSet으로 중복 방지 및 처리)
            val newCheckedSet = log.checkedGearIds.toMutableSet()
            if (shouldCheck) {
                newCheckedSet.add(gearId)
            } else {
                newCheckedSet.remove(gearId)
            }

            // 3. 수정된 데이터 객체 생성 및 파일 저장
            val updatedLog = log.copy(checkedGearIds = newCheckedSet.toList())
            allLogs[date] = updatedLog
            saveCampLogs(context, allLogs)

            // 4. 💡 [핵심] campLog 상태 변수를 업데이트!
            // 이렇게 하면 위에서 선언한 val checkedGearIds가 자동으로 이 값을 반영합니다.
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
                        // 💡 [수정] UserGear 정렬 로직 (name -> modelName)
                        val sortedGear = matchingGear.sortedWith(
                            compareBy<UserGear> { checkedGearIds.contains(it.id.toString()) }
                                .thenBy { it.category }
                                .thenBy { it.modelName }
                        )

                        LazyColumn {
                            // 💡 matchingGear 대신 sortedGear를 사용합니다.
                            items(sortedGear, key = { it.id }) { gear ->
                                val isChecked = checkedGearIds.contains(gear.id.toString())
                                val emoji = when(gear.category) {
                                    "텐트" -> "⛺"
                                    "타프" -> "⛱️"
                                    "체어" -> "💺"
                                    "테이블" -> "🪑"
                                    "조명" -> "💡"
                                    "침구" -> "🛌"
                                    "취사" -> "🍳"
                                    "화로대" -> "🔥"
                                    else -> "🛠️" // 기본 아이콘
                                }

                                ListItem(
                                    headlineContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // 💡 카테고리 태그(Surface)를 제거하고 이모지와 이름을 더 가깝게 배치
                                            Text(emoji, fontSize = 20.sp)
                                            Spacer(modifier = Modifier.width(12.dp))

                                            // 장비 모델명
                                            Text(
                                                text = gear.modelName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (isChecked) Color.Gray else Color.Unspecified,
                                                textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                                fontWeight = if (isChecked) FontWeight.Normal else FontWeight.SemiBold
                                            )
                                        }
                                    },
                                    supportingContent = {
                                        // 💡 브랜드 정보를 한 줄 아래에 은은하게 배치 (여백을 이모지 크기에 맞춤)
                                        Text(
                                            text = "${gear.brand} | ${gear.category}",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(start = 32.dp) // 이모지 뒤에 딱 맞게 정렬
                                        )
                                    },
                                    leadingContent = {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { toggleGearCheck(gear.id.toString(), it) }
                                        )
                                    },
                                    // 💡 [핵심] 우측 삭제 버튼 추가
                                    trailingContent = {
                                        IconButton(onClick = {
                                            // 1. 파일에서 전체 데이터를 즉시 새로 읽어옵니다. (동기화의 핵심)
                                            val currentLogs = loadCampLogs(context).toMutableMap()
                                            val targetLog = currentLogs[date]

                                            targetLog?.let { log ->
                                                // 2. 장비 목록에서 삭제할 ID 찾기
                                                val updatedGearIds = log.gearIds.filterNot { id ->
                                                    if (id.startsWith("custom|")) {
                                                        val parts = id.split("|")
                                                        parts.getOrNull(3) == gear.modelName && parts.getOrNull(2) == gear.brand
                                                    } else {
                                                        id == gear.id.toString()
                                                    }
                                                }

                                                // 3. 체크리스트에서도 똑같이 제거
                                                // 직접 입력 장비는 originalId 문자열로, 일반 장비는 숫자ID로 체크되어 있으므로 둘 다 대응
                                                val updatedCheckedIds = log.checkedGearIds.filterNot { id ->
                                                    if (id.startsWith("custom|")) {
                                                        val parts = id.split("|")
                                                        parts.getOrNull(3) == gear.modelName && parts.getOrNull(2) == gear.brand
                                                    } else {
                                                        id == gear.id.toString()
                                                    }
                                                }

                                                // 4. 수정된 로그 객체를 전체 맵에 다시 넣고 저장
                                                val updatedLog = log.copy(
                                                    gearIds = updatedGearIds,
                                                    checkedGearIds = updatedCheckedIds
                                                )
                                                currentLogs[date] = updatedLog
                                                saveCampLogs(context, currentLogs)

                                                // 5. [중요] 화면을 담당하는 상태 변수를 업데이트해서 UI를 즉시 바꿉니다.
                                                // 만약 상단에서 'allLogs'를 쓰기로 했다면 allLogs = currentLogs
                                                // 'campLog'를 쓰고 있다면 campLog = updatedLog 를 해줍니다.
                                                campLog = updatedLog

                                                Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.LightGray)
                                        }
                                    },
                                    modifier = Modifier.clickable { toggleGearCheck(gear.id.toString(), !isChecked) }
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp) // 버튼 사이 간격
                ) {
                    Button(onClick = { showGroupPicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                        Text("내장비그룹 추가", fontSize = 10.sp)
                    }
                    Button(onClick = { showIndividualPicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("내장비개별 추가", fontSize = 10.sp)
                    }
                    // 💡 3번 버튼: 직접 입력 추가
                    Button(onClick = { showQuickAdd = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                        Text("미등록장비 추가", fontSize = 10.sp)
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
                    val newGearIds = log.gearIds + gear.id.toString()
                    val updatedLog = log.copy(gearIds = newGearIds)
                    val allLogs = loadCampLogs(context).toMutableMap()
                    allLogs[date] = updatedLog
                    saveCampLogs(context, allLogs)
                    campLog = updatedLog
                    Toast.makeText(context, "${gear.modelName} 추가됨", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                showIndividualPicker = false
                gearSearchQuery = "" // 검색어 초기화
            }
        )
    }

    // --- [CampingLogScreen.kt 내부 호출 부분] ---
    if (showQuickAdd) {
        QuickGearAddDialog(
            onDismiss = { showQuickAdd = false },
            onAddToList = { id ->
                // 1. 전달받은 ID의 앞뒤 공백 제거 (매우 중요)
                val cleanId = id.trim()

                // 2. 상태값(campLog)에 의존하지 않고, 파일에서 직접 최신 로그를 읽어옵니다.
                val allLogs = loadCampLogs(context).toMutableMap()
                val currentLog = allLogs[date]

                if (currentLog != null) {
                    // 💡 디버깅용 로그 추가
                    println("DEBUG: 추가하려는 ID -> '$cleanId'")
                    println("DEBUG: 현재 리스트 상태 -> ${currentLog.gearIds}")

                    // 3. 중복 체크: 문자열로 정확히 비교
                    val isDuplicate = currentLog.gearIds.any { it.trim() == cleanId }

                    if (!isDuplicate) {
                        // 4. 중복이 아닐 때만 리스트에 추가하고 저장
                        val updatedLog = currentLog.copy(gearIds = currentLog.gearIds + cleanId)
                        allLogs[date] = updatedLog
                        saveCampLogs(context, allLogs)

                        // 5. 화면 UI 갱신 (상태값 업데이트)
                        campLog = updatedLog

                        Toast.makeText(context, "체크리스트에 추가되었습니다.", Toast.LENGTH_SHORT).show()
                        showQuickAdd = false // 팝업 닫기
                    } else {
                        // 6. 진짜로 중복된 경우
                        println("DEBUG: 중복 발생! 이미 리스트에 '$cleanId'가 있습니다.")
                        Toast.makeText(context, "이미 체크리스트에 있는 장비입니다.", Toast.LENGTH_SHORT).show()
                        // 중복이더라도 사용자가 팝업을 닫을 수 있게 하거나,
                        // 창고 등록은 성공했으니 팝업을 유지할지 선택하게 합니다.
                    }
                }
            },
            onSaveToWarehouse = { b, m, c, memo ->
                // 💡 [수정] UserGear의 모든 파라미터를 명시적으로 전달
                val newGear = UserGear(
                    category = c,
                    brand = b,
                    modelName = m,
                    quantity = 1,
                    memo = memo,
                    isWinterOnly = false,
                    isFirewoodUse = false,
                    imageUrl = "",
                    linkUrl = ""
                )
                val generatedId = gearDao.insertUserGear(newGear)
                generatedId.toString()
            }
        )
    }
}

@Composable
fun QuickGearAddDialog(
    onDismiss: () -> Unit,
    onAddToList: (id: String) -> Unit,
    onSaveToWarehouse: suspend (brand: String, model: String, category: String, memo: String) -> String
) {
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") } // 💡 메모 상태 추가
    var selectedCategory by remember { mutableStateOf("기타") }
    var isSavedToWarehouse by remember { mutableStateOf(false) }
    var savedId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val categories = listOf("텐트", "타프", "체어", "테이블", "조명", "침구", "식기", "취사", "화로대", "기타")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSavedToWarehouse) "저장 완료" else "장비 직접 입력", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isSavedToWarehouse) {
                    OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("브랜드") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("모델명") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = memo, onValueChange = { memo = it }, label = { Text("메모 (선택사항)") }, modifier = Modifier.fillMaxWidth())

                    Text("카테고리", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { cat ->
                            FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat) })
                        }
                    }
                } else {
                    Text("'${model}' 장비가 내 창고에 등록되었습니다.\n이 장비를 현재 체크리스트에도 추가할까요?")
                }
            }
        },
        confirmButton = {
            if (!isSavedToWarehouse) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        // 리스트만 추가할 때는 구분자를 통해 임시 데이터 생성
                        val tempId = "custom|$selectedCategory|$brand|$model|1|$memo"
                        onAddToList(tempId)
                    }) { Text("리스트만 추가") }

                    Button(onClick = {
                        scope.launch {
                            // 💡 [수정] 결과를 변수에 직접 받아서 상태를 업데이트합니다.
                            val resultId = onSaveToWarehouse(brand, model, selectedCategory, memo)
                            if (resultId.isNotBlank()) {
                                savedId = resultId
                                isSavedToWarehouse = true
                            }
                        }
                    }) { Text("내 장비 등록") }
                }
            } else {
                Button(onClick = { onAddToList(savedId) }) { Text("체크리스트 추가") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isSavedToWarehouse) "닫기" else "취소") }
        }
    )
}
