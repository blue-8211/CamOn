package com.company.camon.ui.log

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.company.camon.data.model.CampLog
import com.company.camon.util.loadCampLogs
import com.company.camon.util.saveCampLogs
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampingLogDetailScreen(context: Context, date: String, onBack: () -> Unit) {
    // 1. DB 인스턴스 및 데이터 관측 (GearDao 4번 메서드 활용)
    val db = remember { com.company.camon.data.db.CamonDatabase.getDatabase(context) }
    val gearDao = db.gearDao()

    // 💡 Flow를 State로 변환하여 실시간 리스트를 가져옵니다.
    val allGear by gearDao.getAllUserGears().collectAsState(initial = emptyList())

    // 1. 데이터 로드
    var campLog by remember { mutableStateOf(loadCampLogs(context)[date]) }

    // CampingLogDetailScreen.kt 상단 상태 선언 부분 수정
    var rating by remember { mutableIntStateOf(campLog?.rating ?: 5) }
    var mood by remember { mutableStateOf(campLog?.mood ?: "😄") }
    var weatherDesc by remember { mutableStateOf(campLog?.weatherDesc ?: "☀️") }
    var note by remember { mutableStateOf(campLog?.note ?: "") }

    // 💡 날짜 비교 로직 추가
    val selectedDate = LocalDate.parse(date)
    val today = LocalDate.now()
    val isFuture = selectedDate.isAfter(today) // 미래 여부 확인

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("캠핑 기록 상세", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        campLog?.let { log ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // --- 헤더: 장소 및 날짜 ---
                Text(text = "📍 ${log.location}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(text = "${log.startDate} (${log.nights}박)", color = Color.Gray, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(20.dp))

                // --- [Step 2 & 3] 감성 기록 영역 ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 평점 (별점 대신 숫자/텍스트로 우선 구현)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⭐ 평점", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(12.dp))
                            (1..5).forEach { star ->
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    // 💡 미래라면 클릭 비활성화 및 색상 연하게
                                    tint = if (star <= rating) Color(0xFFFFB300) else Color.LightGray,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .then(
                                            if (!isFuture) Modifier.clickable { rating = star }
                                            else Modifier // 미래면 클릭 이벤트 안 붙임
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 기분 및 날씨 선택 (기획 4번 등록 기능)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DetailSelectChip("기분", mood, listOf("😄", "😊", "😐", "😞", "😫"), enabled = !isFuture) { mood = it }
                            DetailSelectChip("날씨", weatherDesc, listOf("☀️", "☁️", "🌧️", "❄️", "🌫️"), enabled = !isFuture) { weatherDesc = it }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 💡 2. 가져간 장비(checkedGearIds)를 실제 UserGear 객체와 매칭
                val groupedPackedGear = remember(allGear, log.checkedGearIds) {
                    log.checkedGearIds.mapNotNull { id ->
                        val cleanId = id.trim()
                        if (cleanId.startsWith("custom|")) {
                            // 직접 입력(미등록) 장비 파싱
                            val parts = cleanId.split("|")
                            com.company.camon.data.model.UserGear(
                                id = cleanId.hashCode().toLong(),
                                category = parts.getOrNull(1) ?: "기타",
                                brand = parts.getOrNull(2) ?: "",
                                modelName = parts.getOrNull(3) ?: "장비",
                                quantity = 1
                            )
                        } else {
                            // 💡 Dao의 getAllUserGears()에서 가져온 리스트에서 ID 매칭
                            allGear.find { it.id.toString() == cleanId }
                        }
                    }.groupBy { it.category } // 카테고리별 그룹화
                }

                // 💡 [3. UI 구현: 카테고리별 출력]
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "🎒 함께한 장비 (${log.checkedGearIds.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    if (groupedPackedGear.isEmpty()) {
                        Text("가져간 장비가 없습니다.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }

                    groupedPackedGear.forEach { (category, gears) ->
                        Spacer(modifier = Modifier.height(16.dp))

                        // 카테고리 헤더
                        val emoji = when(category) {
                            "텐트" -> "⛺" "타프" -> "⛱️" "체어" -> "💺" "테이블" -> "🪑"
                            "조명" -> "💡" "침구" -> "🛌" "취사" -> "🍳" "화로대" -> "🔥"
                            "도구" -> "🧰" "소모품" -> "🛒" else -> "📦"
                        }
                        Text(text = "$emoji $category", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        // 장비 칩 리스트
                        FlowRow(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            gears.forEach { gear ->
                                SuggestionChip(
                                    onClick = { },
                                    label = {
                                        Text("${gear.brand} ${gear.modelName}", fontSize = 11.sp)
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- 메모 영역 (기획 4번) ---
                Text(text = "📝 메모", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp),
                    placeholder = { Text("그날의 추억을 짧게 남겨보세요.") },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(30.dp))

                // 저장 버튼
                if (!isFuture) {
                    Button(
                        onClick = {
                            campLog?.let { currentLog ->
                                // 1. 전체 로그 데이터를 파일에서 다시 읽어옵니다.
                                val allLogs = loadCampLogs(context).toMutableMap()

                                // 2. 현재 화면에서 입력된 값들로 기존 로그 정보를 업데이트(복사)합니다.
                                val updatedLog = currentLog.copy(
                                    rating = rating,
                                    mood = mood,
                                    weatherDesc = weatherDesc,
                                    note = note
                                )

                                // 💡 [중요 로그] 저장하는 '키'값이 정확히 무엇인지 확인
                                android.util.Log.d("SAVE_CHECK", "저장하는 날짜 키: '$date'")

                                // 3. 업데이트된 로그를 전체 맵에 다시 넣습니다.
                                allLogs[date] = updatedLog

                                // 4. 파일(JSON)로 최종 저장합니다.
                                saveCampLogs(context, allLogs)

                                // 5. 현재 화면의 상태도 업데이트하여 즉시 반영되게 합니다.
                                campLog = updatedLog

                                Toast.makeText(context, "캠핑의 추억이 저장되었습니다! 🏕️", Toast.LENGTH_SHORT).show()

                                // 저장 후 자동으로 뒤로가기를 원하시면 추가, 계속 보길 원하시면 생략 가능합니다.
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp), // 버튼 높이를 조금 키우면 클릭감이 좋아집니다.
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("추억 저장하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // 미래일 경우 안내 문구 하나 띄워주면 친절하겠죠?
                    Text(
                        "아직 캠핑 전이라 기록을 남길 수 없어요. 다녀온 후에 만나요! 🏕️",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("기록을 불러올 수 없습니다.")
        }
    }
}

@Composable
fun DetailSelectChip(
    label: String,
    selected: String,
    options: List<String>,
    enabled: Boolean, // 💡 활성화 여부 파라미터 추가
    onSelect: (String) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Row(modifier = Modifier.padding(top = 4.dp)) {
            options.forEach { opt ->
                Text(
                    text = opt,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clickable(enabled = enabled) { onSelect(opt) } // 💡 enabled가 false면 클릭 안 됨
                        .alpha(if (selected == opt) 1f else if (enabled) 0.3f else 0.1f) // 💡 비활성 시 더 투명하게
                )
            }
        }
    }
}