package com.company.camon.ui.gear

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.company.camon.data.network.NaverSearchApi

@Composable
fun GearMainScreen(context: Context, naverApi: NaverSearchApi) {
    // 현재 선택된 탭 상태 (0: 내 창고, 1: 장비 그룹)
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("내 창고 📦", "장비 그룹 🎒")

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. 상단 탭 레이아웃
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        // 2. 선택된 탭에 따른 화면 표시
        when (selectedTab) {
            0 -> GearRegistrationScreen(context)
            1 -> GearGroupScreen(context)
        }
    }
}