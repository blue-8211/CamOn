package com.company.camon.ui.gear

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.company.camon.data.model.GearItem
import com.company.camon.data.network.NaverSearchApi
import com.company.camon.data.network.ShopItem
import com.company.camon.util.loadGearList
import com.company.camon.util.saveGearList
import kotlinx.coroutines.launch


/**
 * [기능 1] 이름 세탁기: 광고 수식어를 제거하여 깔끔한 장비명 추출
 */
fun cleanProductName(title: String): String {
    return title
        .replace("<b>", "").replace("</b>", "")
        .replace("&quot;", "\"").replace("&amp;", "&")
        .replace("\\[.*?\\]".toRegex(), "")
        .replace("\\(.*?\\)".toRegex(), "")
        .replace("【.*?】".toRegex(), "")
        .trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearRegistrationScreen(context: Context, naverApi: NaverSearchApi) {
    // --- 상태 데이터 ---
    var gearName by remember { mutableStateOf("") }
    var gearList by remember { mutableStateOf(loadGearList(context)) }
    var shopResults by remember { mutableStateOf<List<ShopItem>>(emptyList()) }

    val categories = listOf("전체", "텐트", "타프", "테이블", "체어", "조명", "취사")
    var selectedCategory by remember { mutableStateOf("전체") }
    val popularBrands = listOf("헬리녹스", "스노우피크", "노르디스크", "콜맨", "코베아", "크레모아")
    val scope = rememberCoroutineScope()

    // --- 다이얼로그 전용 상태 ---
    var showEditDialog by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf("") }
    var selectedCategoryInDialog by remember { mutableStateOf("") }
    var quantity by remember { mutableIntStateOf(1) } // 💡 수량 상태 추가
    var tempShopItem by remember { mutableStateOf<ShopItem?>(null) }

    // 현재 탭에 따른 필터링 리스트
    val filteredList = if (selectedCategory == "전체") gearList else gearList.filter { it.category == selectedCategory }

    // API 검색 함수
    fun performSearch(query: String, category: String) {
        scope.launch {
            try {
                val smartQuery = if (category != "전체") "$category $query" else query
                val response = naverApi.searchGear("8mtFAfTR89iqD77LO6us", "Wn0CK0Ie0Q", smartQuery)
                shopResults = response.items
            } catch (e: Exception) { shopResults = emptyList() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("내 장비 창고 🛠️", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            // 💡 그룹 화면으로 이동하는 임시 버튼
            Button(onClick = {
                // 내비게이션을 쓰신다면 navController.navigate("group_screen")
                // 지금은 테스트를 위해 화면 전환 로직을 여기에 연결해야 합니다.
            }) {
                Text("그룹 관리")
            }
        }

        // 1. 카테고리 탭
        LazyRow(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        selectedCategory = category
                        if (gearName.isNotEmpty()) performSearch(gearName, category)
                    },
                    label = { Text(category) }
                )
            }
        }

        // 2. 브랜드 퀵 버튼
        Text("인기 브랜드", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        LazyRow(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(popularBrands) { brand ->
                SuggestionChip(
                    onClick = {
                        gearName = brand
                        performSearch(brand, selectedCategory)
                    },
                    label = { Text(brand) }
                )
            }
        }

        // 3. 검색창
        OutlinedTextField(
            value = gearName,
            onValueChange = {
                gearName = it
                if (it.length >= 2) performSearch(it, selectedCategory)
                else shopResults = emptyList()
            },
            label = { Text("장비명 검색 (예: 체어원)") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (gearName.isNotEmpty()) {
                    IconButton(onClick = { gearName = ""; shopResults = emptyList() }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            }
        )

        // 4. 검색 결과 드롭다운
        if (shopResults.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.heightIn(max = 250.dp)) {
                    shopResults.forEach { item ->
                        val cleanedTitle = cleanProductName(item.title)
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = item.image, contentDescription = null,
                                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${item.brand} | $cleanedTitle", fontSize = 12.sp, maxLines = 1)
                                }
                            },
                            onClick = {
                                editingName = cleanedTitle
                                tempShopItem = item
                                quantity = 1 // 수량 초기화
                                selectedCategoryInDialog = if (selectedCategory == "전체") "" else selectedCategory
                                showEditDialog = true
                            }
                        )
                    }
                }
            }
        }

        // 검색 결과 없을 때 직접 입력 유도
        if (gearName.length >= 2 && shopResults.isEmpty()) {
            TextButton(
                onClick = {
                    editingName = gearName
                    tempShopItem = ShopItem(title = gearName, brand = "직접입력", image = "", lprice = "0", category1 = "", category2 = "")
                    quantity = 1
                    selectedCategoryInDialog = if (selectedCategory == "전체") "" else selectedCategory
                    showEditDialog = true
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("'${gearName}' 직접 등록하기") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. 내 장비 목록 표시 (수량 배지 포함)
        Text("📦 목록 (${filteredList.size})", style = MaterialTheme.typography.titleSmall)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filteredList) { gear ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(gear.name, fontWeight = FontWeight.Bold)
                                if (gear.quantity > 1) { // 💡 수량이 2개 이상일 때만 배지 표시
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("x${gear.quantity}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                            }
                        },
                        supportingContent = { Text("${gear.brand} | ${gear.category}") },
                        leadingContent = {
                            if (gear.imageUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = gear.imageUrl, contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else { Icon(Icons.Default.Build, null, modifier = Modifier.size(48.dp)) }
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                gearList = gearList.filter { it != gear }
                                saveGearList(context, gearList)
                            }) { Icon(Icons.Default.Delete, null, tint = Color.Gray) }
                        }
                    )
                }
            }
        }
    }

    // 6. [다이얼로그] 장비 정보 확인 및 수량 조절
    if (showEditDialog && tempShopItem != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("장비 등록 확인") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        label = { Text("표시될 이름") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 💡 수량 조절 섹션
                    Text("수량", style = MaterialTheme.typography.labelLarge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        // --- 마이너스 버튼 ---
                        FilledIconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFEEEEEE), // 연한 그레이
                                contentColor = Color.Black
                            )
                        ) {
                            Text("-", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }

                        // --- 숫자 표시 ---
                        Text(
                            text = "$quantity",
                            modifier = Modifier.padding(horizontal = 20.dp),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )

                        // --- 플러스 버튼 ---
                        FilledIconButton(
                            onClick = { quantity++ },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            // 플러스도 텍스트로 맞추면 밸런스가 좋습니다!
                            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("카테고리", style = MaterialTheme.typography.labelLarge)

                    // 카테고리 칩 배치
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        categories.filter { it != "전체" }.chunked(3).forEach { rowList ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowList.forEach { cat ->
                                    FilterChip(
                                        selected = selectedCategoryInDialog == cat,
                                        onClick = { selectedCategoryInDialog = cat },
                                        label = { Text(cat, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newGear = GearItem(
                            brand = tempShopItem!!.brand,
                            name = editingName,
                            category = selectedCategoryInDialog,
                            imageUrl = tempShopItem!!.image,
                            isManual = tempShopItem!!.brand == "직접입력",
                            quantity = quantity // 💡 수량 저장
                        )
                        gearList = gearList + newGear
                        saveGearList(context, gearList)
                        showEditDialog = false
                        gearName = ""; shopResults = emptyList()
                    },
                    enabled = selectedCategoryInDialog.isNotEmpty()
                ) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("취소") } }
        )
    }
}