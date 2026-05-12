package ru.myit.vlevpn.data.telemetry

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mobile_telemetry_outbox",
    indices = [
        Index("createdAtMillis"),
        Index("nextRetryAtMillis"),
    ],
)
data class MobileTelemetryOutboxEntity(
    @PrimaryKey val eventId: String,
    val payloadJson: String,
    val createdAtMillis: Long,
    val nextRetryAtMillis: Long = 0L,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val updatedAtMillis: Long = createdAtMillis,
)
