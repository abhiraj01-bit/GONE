package com.infinity.ai.health.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VitalsDao {
    @Insert
    suspend fun insert(reading: VitalsReading): Long

    @Query("SELECT * FROM vitals_readings ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 100): Flow<List<VitalsReading>>

    @Query("SELECT * FROM vitals_readings WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getSince(since: Long): List<VitalsReading>

    @Query("SELECT * FROM vitals_readings ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<VitalsReading?>

    @Query("DELETE FROM vitals_readings WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface AnomalyDao {
    @Insert
    suspend fun insert(event: AnomalyEvent): Long

    @Update
    suspend fun update(event: AnomalyEvent)

    @Query("SELECT * FROM anomaly_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<AnomalyEvent>>

    @Query("SELECT * FROM anomaly_events WHERE acknowledged = 0 ORDER BY timestamp DESC")
    fun getUnacknowledged(): Flow<List<AnomalyEvent>>

    @Query("UPDATE anomaly_events SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: Long)

    @Query("SELECT * FROM anomaly_events ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestOnce(): AnomalyEvent?
}

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    @Query("SELECT * FROM devices WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryDevice(): DeviceEntity?

    @Query("SELECT * FROM devices ORDER BY lastConnected DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>
}
