package com.infinity.ai.health.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VitalsReading::class,
        HealthSession::class,
        SessionReport::class,
        AnomalyEvent::class,
        DeviceEntity::class,
        HealthReportEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun vitalsDao(): VitalsDao
    abstract fun sessionDao(): SessionDao
    abstract fun sessionReportDao(): SessionReportDao
    abstract fun anomalyDao(): AnomalyDao
    abstract fun deviceDao(): DeviceDao
    abstract fun healthReportDao(): HealthReportDao

    companion object {
        @Volatile private var INSTANCE: HealthDatabase? = null

        fun getInstance(context: Context): HealthDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HealthDatabase::class.java,
                    "gone_health.db"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                .also { INSTANCE = it }
            }
    }
}
