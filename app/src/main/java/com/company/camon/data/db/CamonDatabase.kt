package com.company.camon.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.company.camon.data.model.MasterGear
import com.company.camon.data.model.UserGear

// DB에 들어갈 테이블(Entity)들을 등록합니다.
@Database(entities = [MasterGear::class, UserGear::class], version = 1, exportSchema = false)
abstract class CamonDatabase : RoomDatabase() {

    abstract fun gearDao(): GearDao

    companion object {
        @Volatile
        private var INSTANCE: CamonDatabase? = null

        // 싱글톤 패턴: 앱 전체에서 딱 하나의 DB 객체만 유지합니다.
        fun getDatabase(context: Context): CamonDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CamonDatabase::class.java,
                    "camon_database"
                )
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // 앱 최초 생성 시 기초 데이터 삽입 (예시)
                            // 실제로는 코루틴을 통해 삽입하거나 별도의 JSON 로더를 만듭니다.
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}