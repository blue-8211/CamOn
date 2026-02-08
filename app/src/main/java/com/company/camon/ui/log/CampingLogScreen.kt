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

    // --- [기존 showQuickAdd 아래에 추가] ---
    var showMasterItemPicker by remember { mutableStateOf(false) } // 마스터 팝업 제어
    var targetCategory by remember { mutableStateOf("도구") } // "도구" 또는 "소모품"

    // DB에서 마스터 아이템들 실시간 관측
    val masterItemsByCat by gearDao.getMasterGearsByCategory(targetCategory).collectAsState(initial = emptyList())

    var isMenuExpanded by remember { mutableStateOf(false) } // 메뉴 확장 여부

    // 현재 로그의 gearIds에 포함된 장비들만 필터링하여 메인 리스트 구성
    // 💡 [수정] matchingGear 타입을 UserGear로 변경하고 ID 매칭 로직 보강
    val matchingGear = remember(allGear, campLog) {
        campLog?.gearIds?.mapNotNull { id ->
            val gearObj = if (id.startsWith("custom|")) {
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
            if (gearObj != null) id to gearObj else null
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

    val deleteGear: (String) -> Unit = { idToDelete ->
        val allLogs = loadCampLogs(context).toMutableMap()
        val log = allLogs[date]
        log?.let {
            // 💡 복잡한 역추적 필요 없이 전달받은 originalId만 리스트에서 빼면 끝!
            val updatedGearIds = it.gearIds.filterNot { id -> id == idToDelete }
            val updatedCheckedIds = it.checkedGearIds.filterNot { id -> id == idToDelete }

            val updatedLog = it.copy(gearIds = updatedGearIds, checkedGearIds = updatedCheckedIds)
            allLogs[date] = updatedLog
            saveCampLogs(context, allLogs)
            campLog = updatedLog
            Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

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
        },
        // 💡 FAB 영역 추가
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // 메뉴가 열렸을 때 나타나는 작은 버튼들
                if (isMenuExpanded) {
                    FloatingMenuItem(text = "내 장비 그룹 추가", icon = Icons.Default.Dashboard, color = MaterialTheme.colorScheme.secondary) {
                        showGroupPicker = true
                        isMenuExpanded = false
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    FloatingMenuItem(text = "내 장비 개별 추가", icon = Icons.Default.Add, color = MaterialTheme.colorScheme.primary) {
                        showIndividualPicker = true
                        isMenuExpanded = false
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    FloatingMenuItem(text = "✍️ 미등록 장비 추가", icon = Icons.Default.Edit, color = MaterialTheme.colorScheme.tertiary) {
                        showQuickAdd = true
                        isMenuExpanded = false
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    FloatingMenuItem(text = "🧰 도구 추가", icon = Icons.Default.Build, color = Color(0xFF607D8B)) {
                        targetCategory = "도구" // 또는 선택 로직 추가
                        showMasterItemPicker = true
                        isMenuExpanded = false
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // 💡 4. 소모품 추가 (새로 추가)
                    FloatingMenuItem(text = "🛒 소모품 추가", icon = Icons.Default.ShoppingBasket, color = Color(0xFFFFA000)) {
                        targetCategory = "소모품" // 👈 타겟을 소모품으로 설정
                        showMasterItemPicker = true
                        isMenuExpanded = false
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 메인 [+] 버튼
                ExtendedFloatingActionButton(
                    onClick = { isMenuExpanded = !isMenuExpanded },
                    containerColor = if (isMenuExpanded) Color.Gray else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (isMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "추가"
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isMenuExpanded) "닫기" else "장비 추가")
                }
            }
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

                // --- [LinearProgressIndicator 와 Card 사이(약 165라인 근처)에 추가] ---
                val hasConsumables = matchingGear.any { it.first.startsWith("custom|소모품|") }
                val hasTools = matchingGear.any { it.first.startsWith("custom|도구|") }

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // 1. 도구 유도 섹션 (도구가 없을 때만 노출)
                    if (!hasTools) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = Color(0xFFE1F5FE).copy(alpha = 0.6f), // 도구는 연한 파란색 계열
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF0288D1), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("망치, 팩 등 기본 도구를 추가할까요? ", fontSize = 12.sp, color = Color.DarkGray)
                                Text(
                                    text = "[+ 도구 추가]",
                                    fontSize = 12.sp,
                                    color = Color(0xFF0288D1),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        targetCategory = "도구"
                                        showMasterItemPicker = true
                                    }
                                )
                            }
                        }
                    }

                    // 2. 소모품 유도 섹션 (소모품이 없을 때만 노출)
                    if (!hasConsumables) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = Color(0xFFFFF9C4).copy(alpha = 0.6f), // 소모품은 연한 노란색 계열
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFFBC02D), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("부탄가스, 휴지 등 소모품도 잊지 마세요! ", fontSize = 12.sp, color = Color.DarkGray)
                                Text(
                                    text = "[+ 소모품 추가]",
                                    fontSize = 12.sp,
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        targetCategory = "소모품"
                                        showMasterItemPicker = true
                                    }
                                )
                            }
                        }
                    }
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
                            compareBy<Pair<String, UserGear>> { checkedGearIds.contains(it.first) } // 진짜 ID로 체크 확인
                                .thenBy { it.second.category }
                                .thenBy { it.second.modelName }
                        )

                        LazyColumn {
                            // 💡 matchingGear 대신 sortedGear를 사용합니다.
                            items(sortedGear, key = { it.first }) { (originalId, gear) ->
                                val isChecked = checkedGearIds.contains(originalId)
                                val emoji = when(gear.category) {
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

                                // 💡 [여기서부터 ListItem 대신 들어가는 압축형 Row]
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { toggleGearCheck(originalId, !isChecked) }
                                            .padding(horizontal = 12.dp, vertical = 4.dp), // 패딩 대폭 축소
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. 체크박스
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { toggleGearCheck(originalId, it) },
                                            modifier = Modifier.size(32.dp) // 클릭 영역 확보
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // 2. 이모지 + 이름 + 브랜드 (중앙 텍스트 영역)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(emoji, fontSize = 16.sp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = gear.modelName,
                                                    fontSize = 15.sp,
                                                    fontWeight = if (isChecked) FontWeight.Normal else FontWeight.Bold,
                                                    color = if (isChecked) Color.Gray else Color.Unspecified,
                                                    style = androidx.compose.ui.text.TextStyle(
                                                        textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                                    )
                                                )
                                            }
                                            Text(
                                                text = "${gear.brand} | ${gear.category}",
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                modifier = Modifier.padding(start = 24.dp) // 이모지 너비만큼 밀어줌
                                            )
                                        }

                                        // 3. 삭제 버튼 (작고 깔끔하게 유지)
                                        IconButton(
                                            onClick = { deleteGear(originalId) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "삭제",
                                                tint = Color.LightGray.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    // 💡 얇은 구분선 (항목 간 경계 명확화)
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = Color.LightGray.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- [4. 최종 저장 버튼] ---
                // --- [4. 하단 액션 버튼: 그룹 및 개별 장비 추가] ---
                /*
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp) // 버튼 사이 간격
                ) {
                    Button(onClick = { showGroupPicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                        Text("그룹 추가", fontSize = 10.sp)
                    }
                    Button(onClick = { showIndividualPicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("개별 추가", fontSize = 10.sp)
                    }
                    // 💡 3번 버튼: 직접 입력 추가
                    Button(onClick = { showQuickAdd = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                        Text("미등록장비 추가", fontSize = 10.sp)
                    }
                    // 💡 [새로 추가] 도구 추가 버튼
                    Button(
                        onClick = {
                            targetCategory = "도구"
                            showMasterItemPicker = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B)) // 도구 느낌의 차분한 색
                    ) {
                        Text("도구 추가", fontSize = 10.sp)
                    }
                }*/
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

    // --- [CampingLogScreen 최하단(다이얼로그 모음)에 추가] ---
    if (showMasterItemPicker) {
        MasterItemPickerDialog(
            title = if (targetCategory == "도구") "기본 도구 선택" else "필수 소모품 추천",
            items = masterItemsByCat,
            onItemsSelected = { selectedList ->
                val allLogs = loadCampLogs(context).toMutableMap()
                val log = allLogs[date]
                log?.let { currentLog ->
                    // custom|카테고리|브랜드|모델명 형식으로 ID 생성
                    val newIds = selectedList.map { "custom|${it.category}|${it.brand}|${it.modelName}" }
                    val updatedIds = (currentLog.gearIds + newIds).distinct()

                    val updatedLog = currentLog.copy(gearIds = updatedIds)
                    allLogs[date] = updatedLog
                    saveCampLogs(context, allLogs)
                    campLog = updatedLog
                }
                showMasterItemPicker = false
                Toast.makeText(context, "체크리스트에 추가되었습니다.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showMasterItemPicker = false }
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

@Composable
fun MasterItemPickerDialog(
    title: String,
    items: List<com.company.camon.data.model.MasterGear>,
    onItemsSelected: (List<com.company.camon.data.model.MasterGear>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedItems = remember { mutableStateListOf<com.company.camon.data.model.MasterGear>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            if (items.isEmpty()) {
                Text("데이터가 없습니다. 💉 버튼으로 데이터를 먼저 심어주세요.", fontSize = 13.sp)
            } else {
                LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                    items(items) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (selectedItems.contains(item)) selectedItems.remove(item) else selectedItems.add(item)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedItems.contains(item),
                                onCheckedChange = {
                                    if (it) selectedItems.add(item) else selectedItems.remove(item)
                                }
                            )
                            Text("${item.brand} ${item.modelName}", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onItemsSelected(selectedItems) }) { Text("추가하기") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
fun FloatingMenuItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = color,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
