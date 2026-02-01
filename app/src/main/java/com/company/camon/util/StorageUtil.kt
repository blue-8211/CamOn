package com.company.camon.util

import android.content.Context
import com.company.camon.data.model.CampLog
import com.company.camon.data.model.GearGroup
import com.company.camon.data.model.GearItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val PREF_NAME = "camon_prefs"
private const val KEY_GEAR_GROUPS = "gear_groups"
private const val KEY_CAMP_LOGS = "camp_logs_v2" // 버전 관리용 키 변경
private const val KEY_GEAR_LIST = "gear_list_v2"

/**
 * 💡 공통 Gson 인스턴스
 */
private val gson = Gson()

// --- 1. 장비 그룹 (GearGroup) ---
fun saveGearGroups(context: Context, groups: List<GearGroup>) {
    val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val json = gson.toJson(groups)
    sharedPref.edit().putString(KEY_GEAR_GROUPS, json).commit() // commit으로 즉시 저장
}

fun loadGearGroups(context: Context): List<GearGroup> {
    val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val json = sharedPref.getString(KEY_GEAR_GROUPS, null) ?: return emptyList()
    val type = object : TypeToken<List<GearGroup>>() {}.type
    return gson.fromJson(json, type)
}

// --- 2. 캠핑 기록 (CampLog) ---
// 💡 기존의 문자열 결합 방식을 버리고 JSON 방식으로 전면 수정합니다.
fun saveCampLogs(context: Context, logs: Map<String, CampLog>) {
    val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val json = gson.toJson(logs)
    sharedPref.edit().putString(KEY_CAMP_LOGS, json).commit()
}

fun loadCampLogs(context: Context): Map<String, CampLog> {
    val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val json = sharedPref.getString(KEY_CAMP_LOGS, null) ?: return emptyMap()
    val type = object : TypeToken<Map<String, CampLog>>() {}.type
    return gson.fromJson(json, type)
}

// --- 3. 장비 리스트 (GearItem) ---
fun saveGearList(context: Context, list: List<GearItem>) {
    val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val json = gson.toJson(list)
    sharedPref.edit().putString(KEY_GEAR_LIST, json).commit()
}

fun loadGearList(context: Context): List<GearItem> {
    val sharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val json = sharedPref.getString(KEY_GEAR_LIST, null) ?: return emptyList()
    val type = object : TypeToken<List<GearItem>>() {}.type
    return gson.fromJson(json, type)
}