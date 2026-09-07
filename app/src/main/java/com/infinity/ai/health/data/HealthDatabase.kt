package com.infinity.ai.health.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VitalsReading::class, AnomalyEvent::class, DeviceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun vitalsDao(): VitalsDao
    abstract fun anomalyDao(): AnomalyDao
    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile private var INSTANCE: HealthDatabase? = null

        fun getInstance(context: Context): HealthDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HealthDatabase::class.java,
                    "gone_health.db"
                ).build().also { INSTANCE = it }
            }
    }
}
