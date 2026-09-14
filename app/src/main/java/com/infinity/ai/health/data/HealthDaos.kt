package com.infinity.ai.health.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VitalsDao {
    @Insert
    suspend fun insert(reading: VitalsReading): Long

    @Query("SELECT * FROM vitals_readings ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 100): Flow<List<VitalsReading>>

    @Query("SELECT * FROM vitals_readings WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getSince(since: Long): List<VitalsReading>

    @Query("SELECT * FROM vitals_readings WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: Long): List<VitalsReading>

    @Query("SELECT * FROM vitals_readings ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<VitalsReading?>

    @Query("DELETE FROM vitals_readings WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM vitals_readings")
    suspend fun deleteAll()
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: HealthSession): Long

    @Update
    suspend fun updateSession(session: HealthSession)

    @Query("SELECT * FROM health_sessions WHERE id = :id")
    suspend fun getSession(id: Long): HealthSession?

    @Query("SELECT * FROM health_sessions WHERE isActive = 1 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): HealthSession?

    @Query("SELECT * FROM health_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 20): Flow<List<HealthSession>>
}

@Dao
interface SessionReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: SessionReport): Long

    @Query("SELECT * FROM session_reports WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getBySession(sessionId: Long): SessionReport?

    @Query("SELECT * FROM session_reports ORDER BY generatedAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 10): Flow<List<SessionReport>>
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
interface HealthReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: HealthReportEntity): Long

    @Query("SELECT * FROM health_reports WHERE id = :id")
    suspend fun getById(id: Long): HealthReportEntity?

    /** Observe a single report as a live Flow — auto-updates when Qwen patches the AI fields. */
    @Query("SELECT * FROM health_reports WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<HealthReportEntity?>

    @Query("SELECT * FROM health_reports WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getBySession(sessionId: Long): HealthReportEntity?

    @Query("SELECT * FROM health_reports ORDER BY createdAt DESC")
    fun getAllSortedByDate(): Flow<List<HealthReportEntity>>

    @Query("SELECT * FROM health_reports ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<HealthReportEntity>>

    /** Patch-update just the AI analysis fields after Qwen finishes in background. */
    @Query("""
        UPDATE health_reports SET
            aiSummary                    = :aiSummary,
            observationsJson             = :observationsJson,
            physicalConcernsJson         = :physicalConcernsJson,
            foodRecommendationsJson      = :foodRecommendationsJson,
            exerciseRecommendationsJson  = :exerciseRecommendationsJson,
            lifestyleRecommendationsJson = :lifestyleRecommendationsJson,
            medicalAttentionJson         = :medicalAttentionJson,
            overallStatus                = :overallStatus,
            rawAiJson                    = :rawAiJson
        WHERE id = :id
    """)
    suspend fun updateAiAnalysis(
        id                          : Long,
        aiSummary                   : String,
        observationsJson            : String,
        physicalConcernsJson        : String,
        foodRecommendationsJson     : String,
        exerciseRecommendationsJson : String,
        lifestyleRecommendationsJson: String,
        medicalAttentionJson        : String,
        overallStatus               : String,
        rawAiJson                   : String
    )
    @Query("""
        UPDATE health_reports SET
            emailStatus    = :emailStatus,
            emailSentAt    = :emailSentAt,
            emailRecipient = :emailRecipient
        WHERE id = :id
    """)
    suspend fun updateEmailStatus(
        id            : Long,
        emailStatus   : String,
        emailSentAt   : Long?,
        emailRecipient: String
    )
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
