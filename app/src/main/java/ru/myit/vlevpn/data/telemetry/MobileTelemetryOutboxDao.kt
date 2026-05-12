package ru.myit.vlevpn.data.telemetry

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MobileTelemetryOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MobileTelemetryOutboxEntity): Long

    @Query(
        """
        SELECT * FROM mobile_telemetry_outbox
        WHERE nextRetryAtMillis <= :nowMillis
        ORDER BY nextRetryAtMillis ASC, createdAtMillis ASC
        LIMIT :limit
        """,
    )
    suspend fun getPending(nowMillis: Long, limit: Int): List<MobileTelemetryOutboxEntity>

    @Query("DELETE FROM mobile_telemetry_outbox WHERE eventId IN (:eventIds)")
    suspend fun deleteByEventIds(eventIds: List<String>)

    @Query("DELETE FROM mobile_telemetry_outbox WHERE createdAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query(
        """
        DELETE FROM mobile_telemetry_outbox
        WHERE eventId NOT IN (
            SELECT eventId FROM mobile_telemetry_outbox
            ORDER BY createdAtMillis DESC
            LIMIT :maxRows
        )
        """,
    )
    suspend fun trimToMaxRows(maxRows: Int)

    @Query(
        """
        UPDATE mobile_telemetry_outbox
        SET retryCount = retryCount + 1,
            nextRetryAtMillis = :nextRetryAtMillis,
            lastError = :lastError,
            updatedAtMillis = :updatedAtMillis
        WHERE eventId IN (:eventIds)
        """,
    )
    suspend fun markFailed(
        eventIds: List<String>,
        nextRetryAtMillis: Long,
        updatedAtMillis: Long,
        lastError: String?,
    )
}
