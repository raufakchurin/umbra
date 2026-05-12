package ru.myit.vlevpn.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.myit.vlevpn.data.telemetry.MobileTelemetryOutboxDao
import ru.myit.vlevpn.data.telemetry.MobileTelemetryOutboxEntity

@Database(
    entities = [ServerEntity::class, MobileTelemetryOutboxEntity::class],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun mobileTelemetryOutboxDao(): MobileTelemetryOutboxDao
}
