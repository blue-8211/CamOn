package com.company.camon.util

import android.content.Context
import com.company.camon.data.db.GearDao
import com.company.camon.data.model.MasterGear
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DatabaseInitializer {
    private const val PREF_NAME = "camon_prefs"
    private const val KEY_MASTER_VERSION = "master_db_version"

    // 💡 [중요] 마스터 데이터 버전 (JSON 수정 시 이 숫자를 올리세요)
    private const val CURRENT_VERSION = 2

    suspend fun initializeMasterData(context: Context, gearDao: GearDao) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastVersion = prefs.getInt(KEY_MASTER_VERSION, -1)

        // 업데이트가 필요하거나 최초 실행인 경우
        if (lastVersion < CURRENT_VERSION) {
            try {
                // 1. 기존 마스터 데이터 리셋 (3번 요구사항)
                gearDao.deleteAllMasterGears()

                // 2. assets/gear_master.json 읽기 (4번 요구사항)
                val jsonString = context.assets.open("gear_master.json")
                    .bufferedReader().use { it.readText() }

                // 3. JSON -> List 변환 (8번 요구사항)
                val type = object : TypeToken<List<MasterGear>>() {}.type
                val masterGears: List<MasterGear> = Gson().fromJson(jsonString, type)

                // 4. DB 일괄 삽입
                gearDao.insertMasterGears(masterGears)

                // 5. 버전 기록 업데이트
                prefs.edit().putInt(KEY_MASTER_VERSION, CURRENT_VERSION).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}