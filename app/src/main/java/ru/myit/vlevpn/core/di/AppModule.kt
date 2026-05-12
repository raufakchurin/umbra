package ru.myit.vlevpn.core.di

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.SocketFactory
import okhttp3.OkHttpClient
import ru.myit.vlevpn.core.logging.InMemoryLogRepository
import ru.myit.vlevpn.core.logging.LogRepository
import ru.myit.vlevpn.core.network.ProtectedSocketFactory
import ru.myit.vlevpn.core.network.ProtectedTraffic
import ru.myit.vlevpn.data.apps.AndroidInstalledAppsRepository
import ru.myit.vlevpn.data.backend.BackendApi
import ru.myit.vlevpn.data.backend.FakeBackendApi
import ru.myit.vlevpn.data.backend.FakeBackendRepository
import ru.myit.vlevpn.data.local.AppDatabase
import ru.myit.vlevpn.data.local.ServerDao
import ru.myit.vlevpn.data.ping.DefaultPingRepository
import ru.myit.vlevpn.data.server.DataStoreSettingsRepository
import ru.myit.vlevpn.data.server.RoomServerRepository
import ru.myit.vlevpn.data.subscription.ServerImportRepositoryImpl
import ru.myit.vlevpn.data.telemetry.MobileTelemetryOutboxDao
import ru.myit.vlevpn.domain.repository.BackendRepository
import ru.myit.vlevpn.domain.repository.InstalledAppsRepository
import ru.myit.vlevpn.domain.repository.PingRepository
import ru.myit.vlevpn.domain.repository.ServerImportRepository
import ru.myit.vlevpn.domain.repository.ServerRepository
import ru.myit.vlevpn.domain.repository.SettingsRepository
import ru.myit.vlevpn.runtime.AndroidProxyRuntime
import ru.myit.vlevpn.runtime.LibXrayRuntime
import ru.myit.vlevpn.runtime.ProxyRuntime
import ru.myit.vlevpn.runtime.VpnProtectBridge
import ru.myit.vlevpn.runtime.XrayRuntime

@Module
@InstallIn(SingletonComponent::class)
interface BindingsModule {
    @Binds
    @Singleton
    fun bindLogRepository(impl: InMemoryLogRepository): LogRepository

    @Binds
    @Singleton
    fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    fun bindServerRepository(impl: RoomServerRepository): ServerRepository

    @Binds
    @Singleton
    fun bindServerImportRepository(impl: ServerImportRepositoryImpl): ServerImportRepository

    @Binds
    @Singleton
    fun bindBackendApi(impl: FakeBackendApi): BackendApi

    @Binds
    @Singleton
    fun bindBackendRepository(impl: FakeBackendRepository): BackendRepository

    @Binds
    @Singleton
    fun bindPingRepository(impl: DefaultPingRepository): PingRepository

    @Binds
    @Singleton
    fun bindInstalledAppsRepository(impl: AndroidInstalledAppsRepository): InstalledAppsRepository

    @Binds
    @Singleton
    fun bindXrayRuntime(impl: LibXrayRuntime): XrayRuntime

    @Binds
    @Singleton
    fun bindProxyRuntime(impl: AndroidProxyRuntime): ProxyRuntime
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "vle_vpn.db")
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .addMigrations(MIGRATION_7_8)
            .addMigrations(MIGRATION_8_9)
            .addMigrations(MIGRATION_9_10)
            .build()

    @Provides
    fun provideServerDao(database: AppDatabase): ServerDao = database.serverDao()

    @Provides
    fun provideMobileTelemetryOutboxDao(database: AppDatabase): MobileTelemetryOutboxDao =
        database.mobileTelemetryOutboxDao()

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE servers ADD COLUMN flow TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE servers ADD COLUMN fingerprint TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE servers ADD COLUMN publicKey TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE servers ADD COLUMN shortId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE servers ADD COLUMN spiderX TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE servers ADD COLUMN networkMode TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE servers ADD COLUMN headerHost TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionName TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionImportedAtMillis INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionActivatedAtMillis INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionPosition INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionUpdateIntervalHours INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionUploadBytes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionDownloadBytes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionTotalBytes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionExpireAtMillis INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionSupportUrl TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionWebPageUrl TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionSourceUrl TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionAnnounce TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionAutoUpdateOnLaunchEnabled INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS mobile_telemetry_outbox (
                    eventId TEXT NOT NULL PRIMARY KEY,
                    payloadJson TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL,
                    nextRetryAtMillis INTEGER NOT NULL DEFAULT 0,
                    retryCount INTEGER NOT NULL DEFAULT 0,
                    lastError TEXT,
                    updatedAtMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_mobile_telemetry_outbox_createdAtMillis ON mobile_telemetry_outbox(createdAtMillis)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_mobile_telemetry_outbox_nextRetryAtMillis ON mobile_telemetry_outbox(nextRetryAtMillis)")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionProviderId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE servers ADD COLUMN subscriptionProviderDomainHash TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE servers ADD COLUMN protocolPayloadJson TEXT NOT NULL DEFAULT ''")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = baseOkHttpBuilder()
        .build()

    @Provides
    @Singleton
    @ProtectedTraffic
    fun provideProtectedOkHttpClient(protectBridge: VpnProtectBridge): OkHttpClient = baseOkHttpBuilder()
        .socketFactory(
            ProtectedSocketFactory(
                delegate = SocketFactory.getDefault(),
                protectBridge = protectBridge,
            ),
        )
        .build()

    private fun baseOkHttpBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
}
