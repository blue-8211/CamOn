package com.company.camon.ui.calendar

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.company.camon.data.model.CampLog
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

@Composable
fun CalendarScreen(
    context: Context,
    campLogs: Map<String, CampLog>,
    onDateSelectedForAdd: (LocalDate) -> Unit, // 기록 없는 날 -> 홈으로 이동
    onLogClick: (LocalDate) -> Unit // 기록 있는 날 -> 상세 이동
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val daysInMonth = remember(currentMonth) { getDaysInMonth(currentMonth) }

    // 💡 현재 표시 중인 달의 로그들만 필터링
    // CalendarScreen.kt
    val monthlyLogs = remember(currentMonth, campLogs) {
        // 💡 it.startDate 대신 entries.key를 사용해 보세요.
        campLogs.entries.filter { (key, log) ->
            val logDate = LocalDate.parse(key) // 맵의 키값으로 날짜 분석
            logDate.year == currentMonth.year && logDate.month == currentMonth.month
        }.map { it.value }
    }

    val totalCamps = monthlyLogs.size
    val totalNights = monthlyLogs.sumOf { it.nights }

    // CalendarScreen.kt 수정
    val averageRating = remember(monthlyLogs) {
        // 1. 오늘 날짜 가져오기
        val today = LocalDate.now()

        // 2. 오늘을 포함해 이미 지난 캠핑들만 필터링 (미래 제외)
        val completedLogs = monthlyLogs.filter {
            val logDate = LocalDate.parse(it.startDate)
            !logDate.isAfter(today) // 오늘보다 이후(미래)가 아닌 것들만!
        }

        if (completedLogs.isEmpty()) {
            0.0
        } else {
            // 3. 필터링된 데이터로만 평균 계산
            val sum = completedLogs.sumOf { it.rating }
            val count = completedLogs.size
            val avg = sum.toDouble() / count

            android.util.Log.d("DEBUG_CAL", "합산대상: ${completedLogs.map { it.location }}")
            android.util.Log.d("DEBUG_CAL", "합계: $sum, 개수: $count, 최종평균: $avg")
            avg
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 헤더 영역 (연월 선택)
        item {
            CalendarHeader(currentMonth, onMonthChange = { currentMonth = it })
        }

        // 2. 달력 본체 영역
        item {
            Column {
                DaysOfWeekHeader()
                Spacer(modifier = Modifier.height(8.dp))

                // 날짜 그리드는 높이를 고정하거나 content의 높이에 맞춤
                val gridHeight = (daysInMonth.size / 7 * 65).dp
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(gridHeight),
                    userScrollEnabled = false, // 바깥 LazyColumn이 스크롤을 담당하므로 꺼둠
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(daysInMonth) { date ->
                        if (date != null) {
                            val log = findLogForDate(date, campLogs)
                            CalendarDayItem(date, log, onClick = {
                                if (log != null) onLogClick(date) else onDateSelectedForAdd(date)
                            })
                        } else {
                            Spacer(modifier = Modifier.aspectRatio(1f))
                        }
                    }
                }
            }
        }

        // 3. [추가] 이번 달 캠핑 요약 카드
        item {
            Text("📊 이번 달 캠핑 요약", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    SummaryColumn("캠핑", "${totalCamps}회")
                    SummaryColumn("숙박", "${totalNights}박")
                    SummaryColumn(
                        label = "평균 평점",
                        value = if (monthlyLogs.isEmpty()) "-" else "⭐ ${String.format("%.1f", averageRating)}"
                    )
                }
            }
        }

        // 4. [추가] 최근 캠핑 기록 리스트
        item {
            Text("🗓️ 최근 캠핑 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(monthlyLogs.sortedBy { it.startDate }) { log ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onLogClick(LocalDate.parse(log.startDate)) },
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = log.location, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${log.startDate} ~ ${LocalDate.parse(log.startDate).plusDays(log.nights.toLong())} (${log.nights}박)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun CalendarDayItem(
    date: LocalDate,
    log: CampLog?,
    onClick: () -> Unit
) {
    val isToday = date == LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp) // 셀 전체 높이를 고정하여 균형을 잡음
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 날짜 숫자 영역 (높이 고정으로 아래 바 위치를 고정)
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isToday) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                )
            }
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.primary else Color.Black
            )
        }

        Spacer(modifier = Modifier.height(6.dp)) // 숫자와 바 사이 여백

        // 💡 [기획 2번] 구글 캘린더 스타일 컬러 바
        if (log != null) {
            val startDate = LocalDate.parse(log.startDate)
            val endDate = startDate.plusDays(log.nights.toLong())

            val isStart = date == startDate
            val isEnd = date == endDate

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .padding(horizontal = if (isStart || isEnd) 2.dp else 0.dp) // 시작/끝만 살짝 여백
                    .background(
                        color = Color(0xFF6750A4).copy(alpha = 0.8f),
                        shape = RoundedCornerShape(
                            topStart = if (isStart) 4.dp else 0.dp,
                            bottomStart = if (isStart) 4.dp else 0.dp,
                            topEnd = if (isEnd) 4.dp else 0.dp,
                            bottomEnd = if (isEnd) 4.dp else 0.dp
                        )
                    )
            )
        }else {
            // 기록이 없는 날도 공간은 차지하게 하여 전체 높이 유지
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// 해당 날짜를 포함하는 캠핑 로그 찾기
fun findLogForDate(date: LocalDate, logs: Map<String, CampLog>): CampLog? {
    return logs.values.find { log ->
        val start = LocalDate.parse(log.startDate)
        val end = start.plusDays(log.nights.toLong())
        !date.isBefore(start) && !date.isAfter(end)
    }
}

// 달력 날짜 리스트 생성 (시작 요일 맞추기용 null 포함)
fun getDaysInMonth(yearMonth: YearMonth): List<LocalDate?> {
    val firstDay = yearMonth.atDay(1)
    val firstDayOfWeek = firstDay.dayOfWeek.value % 7 // 0(일) ~ 6(토)
    val daysInMonth = yearMonth.lengthOfMonth()

    val list = mutableListOf<LocalDate?>()
    repeat(firstDayOfWeek) { list.add(null) }
    for (day in 1..daysInMonth) {
        list.add(yearMonth.atDay(day))
    }
    return list
}

@Composable
fun CalendarHeader(
    currentMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${currentMonth.year}년 ${currentMonth.monthValue}월",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row {
            IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "이전달")
            }
            IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "다음달")
            }
        }
    }
}

@Composable
fun DaysOfWeekHeader() {
    val daysOfWeek = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        daysOfWeek.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = when(day) {
                    "일" -> Color.Red
                    "토" -> Color.Blue
                    else -> Color.Gray
                }
            )
        }
    }
}