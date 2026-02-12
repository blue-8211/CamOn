package com.company.camon.ui.gear

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.company.camon.data.db.CamonDatabase
import com.company.camon.data.db.GearDao
import com.company.camon.data.model.MasterGear
import com.company.camon.data.model.UserGear
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape // 👈 이 줄을 추가하세요!
import org.jsoup.Jsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler // 👈 뒤로가기 제어를 위해 추가
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Refresh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearRegistrationScreen(context: Context) {
    val db = remember { CamonDatabase.getDatabase(context) }
    val gearDao = db.gearDao()
    val userGearList by gearDao.getAllUserGears().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // 화면 상태 관리 (false: 목록, true: 등록 단계)
    var isRegistrationMode by remember { mutableStateOf(false) }

    if (isRegistrationMode) {
        RegistrationFlow(
            gearDao = gearDao, // 👈 배달 완료!
            onBack = { isRegistrationMode = false },
            onSave = { newGear ->
                scope.launch {
                    gearDao.insertUserGear(newGear)
                    isRegistrationMode = false
                }
            }
        )
    } else {
        // --- [목록 모드] 내 장비 리스트 ---
        Scaffold(
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { isRegistrationMode = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("장비 추가") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp) // 카드 사이 간격
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {

                            Text(
                                "내 창고 📦",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "총 ${userGearList.size}개의 장비가 보관 중입니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }

                        // 💡 [추가] 5번 요구사항: Debug 전용 리셋 버튼 (개발 중에만 사용)
                        // 실제 배포 시에는 이 버튼을 숨기거나 특정 동작(7번 클릭 등)으로 활성화하면 좋습니다.
                        IconButton(onClick = {
                            scope.launch {
                                // 💡 [수정] DatabaseInitializer의 로직을 강제 호출하여 리셋
                                // (이 부분은 DatabaseInitializer 파일 작업 후 연결)
                                Toast.makeText(context, "데이터 재동기화 중...", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Debug Reset", tint = Color.LightGray)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (userGearList.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("등록된 장비가 없습니다.\n첫 번째 장비를 등록해보세요!", textAlign = TextAlign.Center, color = Color.LightGray)
                        }
                    }
                } else {
                    items(userGearList) { gear ->
                        GearItemCard(
                            gear = gear,
                            onDelete = { scope.launch { gearDao.deleteUserGear(gear) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RegistrationFlow(
    gearDao: GearDao, // 👈 여기 추가!
    onBack: () -> Unit,
    onSave: (UserGear) -> Unit
    ) {
    var currentStep by remember { mutableIntStateOf(1) } // 1단계부터 시작

    // 1. 뒤로가기 버그 해결 (35 -> 3으로 강제 지정)
    BackHandler {
        when (currentStep) {
            1 -> onBack()
            35 -> currentStep = 3
            else -> currentStep -= 1
        }
    }

    // 사용자가 입력/선택 중인 데이터
    var category by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var quantity by remember { mutableIntStateOf(1) }
    var isWinterOnly by remember { mutableStateOf(false) }
    var isFirewoodUse by remember { mutableStateOf(false) }

    var memo by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 상단 뒤로가기 및 단계 표시
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (currentStep == 1) onBack()
                else if (currentStep == 35) currentStep = 3
                else currentStep--
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
            }
            // 단계 표시 (35단계일 때는 3단계로 표시하거나 '직접입력'으로 표시)
            val stepLabel = if (currentStep == 35) "3" else currentStep.toString()
            Text("장비 등록 ($stepLabel/4)", fontWeight = FontWeight.Bold) // 전체 단계를 4단계로 조정 제안
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (currentStep) {
            1 -> { // 1단계: 카테고리 선택
                CategorySelectStep(onCategorySelected = {
                    category = it
                    currentStep = 2
                })
            }
            2 -> {
                val brands by produceState<List<String>>(initialValue = emptyList(), category) {
                    value = gearDao.getBrandsByCategory(category)
                }

                // 브랜드 직접 입력을 위한 로컬 상태 (2단계에서만 사용)
                var customBrand by remember { mutableStateOf("") }
                var isDirectInputMode by remember { mutableStateOf(false) }

                Column(modifier = Modifier.fillMaxSize()) {
                    Text("어떤 브랜드의 $category 인가요?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isDirectInputMode || brands.isEmpty()) {
                        // 1. 브랜드 직접 입력 화면 (리스트에 없거나 DB가 비었을 때)
                        OutlinedTextField(
                            value = brand, // 상위 변수 brand에 직접 저장
                            onValueChange = { brand = it },
                            label = { Text("브랜드 이름 입력") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            placeholder = { Text("예: 헬스포츠, 노르디스크") },
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (brand.isNotBlank()) {
                                    // ✨ 브랜드가 없는 상태이므로 3단계(리스트)를 건너뛰고
                                    // 바로 35단계(모델명 직접입력)로 보냅니다.
                                    currentStep = 35
                                }
                            },
                            enabled = brand.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("브랜드 확인")
                        }

                        if (brands.isNotEmpty()) {
                            TextButton(
                                onClick = { isDirectInputMode = false },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("다시 리스트에서 고르기")
                            }
                        }
                    } else {
                        // 2. 브랜드 리스트 화면
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(brands) { brandName ->
                                Card(
                                    onClick = {
                                        brand = brandName
                                        currentStep = 3 // 리스트에서 고르면 모델 선택(3단계)으로 이동
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(brandName, modifier = Modifier.padding(16.dp))
                                }
                            }

                            // 브랜드가 리스트에 없을 때를 위한 버튼
                            item {
                                OutlinedButton(
                                    onClick = { isDirectInputMode = true },
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("찾는 브랜드가 없어요 (직접 입력)")
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                val models by produceState<List<MasterGear>>(initialValue = emptyList(), category, brand) {
                    value = gearDao.getModels(category, brand)
                }

                Column {
                    Text("$brand 의 모델을 선택해주세요", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("찾으시는 모델이 없으면 하단 버튼을 눌러주세요.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

                    Spacer(modifier = Modifier.height(16.dp))

                    if (models.isNotEmpty()) {
                        // 1. 마스터 DB에 데이터가 있는 경우 (리스트로 보여줌)
                        LazyColumn(
                            modifier = Modifier.weight(1f).padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(models) { gear ->
                                Card(
                                    onClick = {
                                        modelName = gear.modelName
                                        linkUrl = "" // ✨ 링크 초기화
                                        currentStep = 4
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(gear.modelName, modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                    } else {
                        // 데이터가 없을 때 사용자에게 알려주는 메시지
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("등록된 모델 정보가 없습니다.", color = Color.Gray)
                        }
                    }

                    // 2. [하단 고정] 직접 입력 버튼 (데이터 유무와 상관없이 항상 노출)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { currentStep = 35 },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("리스트에 없어요 (직접 입력)")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 3.5단계: 직접 입력 및 스토어 링크 (기획안 6번 핵심)
            35 -> {
                var nameSuggestions by remember { mutableStateOf(listOf<String>()) }
                var isSearching by remember { mutableStateOf(false) }
                var hasError by remember { mutableStateOf(false) }

                LaunchedEffect(linkUrl) {
                    if (linkUrl.startsWith("http")) {
                        isSearching = true
                        hasError = false
                        val results = extractProductNameFromUrl(linkUrl)

                        if (results.isEmpty() || results.any { it.contains("오류") || it.contains("실패") }) {
                            hasError = true
                            nameSuggestions = emptyList()
                        } else {
                            nameSuggestions = results
                            hasError = false
                        }
                        isSearching = false
                    }
                }

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("모델 등록 ✍️", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("직접 입력하거나 아래 링크를 활용해 정보를 찾으세요.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

                    Spacer(modifier = Modifier.height(24.dp))

                    // 1. [상단] 모델명 입력창 (가장 중요하므로 위로!)
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("모델명") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                // ✨ 입력창에 포커스가 잡히는(클릭하는) 순간 링크 초기화!
                                if (focusState.isFocused) {
                                    linkUrl = ""
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("예: 바랑에르돔 8-10") }
                    )

                    // 2. 추천 키워드 (성공 시 노출)
                    if (nameSuggestions.isNotEmpty() &&
                        !nameSuggestions.any { it.contains("에러") || it.contains("보안") || it.contains("page") }) {
                        Text("발견된 이름 추천:", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelMedium)
                        FlowRow(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            nameSuggestions.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = {
                                        modelName = suggestion
                                        linkUrl = "" // ✨ 링크 초기화
                                        // 제안 목록도 더 이상 필요 없으니 비워줍니다.
                                        nameSuggestions = emptyList()
                                    },
                                    label = { Text(suggestion) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Divider(color = Color.LightGray.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. [하단] 스토어 링크 보조 도구
                    Text("정보 찾기 도구 (선택사항)", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = linkUrl,
                        onValueChange = { linkUrl = it },
                        label = { Text("스토어 링크 붙여넣기") },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("링크를 넣으면 모델명을 추천해드려요") },
                        trailingIcon = {
                            if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    )

                    if (hasError) {
                        Text("보안 정책상 이름을 가져오지 못했습니다. 직접 입력해주세요!",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // 4. 다음 버튼
                    Button(
                        onClick = {
                            linkUrl = "" // ✨ 다음 단계로 갈 때도 링크 정보는 비우고 갑니다.
                            currentStep = 4
                        },
                        enabled = modelName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("다음 단계로", fontWeight = FontWeight.Bold)
                    }
                }
            }
            4 -> {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("상세 설정 🏕️", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${brand} - ${modelName}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)

                    Spacer(modifier = Modifier.height(32.dp))

                    // 1. 수량 조절
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("수량", style = MaterialTheme.typography.labelLarge)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                FilledIconButton(onClick = { if (quantity > 1) quantity-- }) { Text("-") }
                                Text("$quantity", modifier = Modifier.padding(horizontal = 32.dp), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                FilledIconButton(onClick = { quantity++ }) { Text("+") }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 2. 메모 입력 (구매링크 삭제, 메모만 남김)
                    Text("메모", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = memo,
                        onValueChange = { memo = it },
                        label = { Text("장비에 대해 적어주세요 (색상, 상태 등)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3 // 메모하기 편하게 칸을 좀 넓혔습니다.
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    // 3. 최종 저장 버튼
                    Button(
                        onClick = {
                            val newGear = UserGear(
                                category = category,
                                brand = brand,
                                modelName = modelName,
                                quantity = quantity,
                                memo = memo,
                                linkUrl = "", // 링크는 빈 값으로 저장
                                isWinterOnly = false,
                                isFirewoodUse = false
                            )
                            onSave(newGear)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("창고에 넣기 📦", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySelectStep(onCategorySelected: (String) -> Unit) {
    val categories = listOf("텐트" to "⛺", "타프" to "⛱️", "테이블" to "🪑", "체어" to "💺", "조명" to "💡", "취사" to "🍳", "기타" to "🛠️")

    LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { (name, emoji) ->
            Card(onClick = { onCategorySelected(name) }) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(emoji, fontSize = 24.sp)
                    Text(name)
                }
            }
        }
    }
}

suspend fun extractProductNameFromUrl(url: String): List<String> {
    return withContext(Dispatchers.IO) {
        try {
            // 1. 광고 파라미터가 섞이면 복잡하니 순수 주소만 추출
            val cleanUrl = if (url.contains("?")) url.split("?")[0] else url

            val doc = Jsoup.connect(cleanUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cache-Control", "max-age=0")
                .header("Connection", "keep-alive")
                .referrer("https://m.search.naver.com")
                .ignoreHttpErrors(true) // 404나 500 에러나도 일단 읽기 시도
                .timeout(10000)
                .get()

            // 2. og:title 태그 찾기 (네이버 상품명은 여기에 숨어있음)
            val productName = doc.select("meta[property=og:title]").attr("content")

            if (productName.isNotBlank()) {
                val cleanName = productName
                    .replace(" : 네이버 쇼핑", "")
                    .replace(" : 네이버 스마트스토어", "")
                    .trim()
                listOf(cleanName)
            } else {
                // og:title 없으면 일반 제목이라도 가져오기
                val title = doc.title().split(":")[0].trim()
                if(title.isNotBlank()) listOf(title) else listOf("직접 입력해주세요")
            }
        } catch (e: Exception) {
            // 에러 원인을 더 정확히 보기 위해 로그 출력
            Log.e("Camon_Jsoup", "실패 이유: ${e.localizedMessage}")
            listOf("연결 실패: 주소를 다시 확인해주세요")
        }
    }
}

// --- [디자인 리뉴얼] 목록 화면 아이템 카드 ---
@Composable
fun GearItemCard(gear: UserGear, onDelete: () -> Unit) {
    val emoji = when(gear.category) {
        "텐트" -> "⛺"
        "체어" -> "💺"
        "테이블" -> "🪑"
        "조명" -> "💡"
        "타프" -> "⛱️"
        "취사" -> "🍳"
        else -> "🛠️"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 카테고리 아이콘
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 장비 정보
            Column(modifier = Modifier.weight(1f)) {
                Text(gear.brand, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(gear.modelName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (gear.memo.isNotBlank()) {
                    Text(gear.memo, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // 수량 표시
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "x${gear.quantity}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "삭제", tint = Color.LightGray)
            }
        }
    }
}