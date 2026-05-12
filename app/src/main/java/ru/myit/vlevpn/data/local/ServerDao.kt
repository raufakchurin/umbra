package ru.myit.vlevpn.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ServerEntity?>

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ServerEntity?

    @Query("SELECT * FROM servers WHERE subscriptionId = :subscriptionId")
    suspend fun getBySubscriptionId(subscriptionId: String): List<ServerEntity>

    @Query("SELECT * FROM servers")
    suspend fun getAll(): List<ServerEntity>

    @Query("SELECT COUNT(*) FROM servers")
    suspend fun count(): Int

    @Query("UPDATE servers SET subscriptionActivatedAtMillis = :activatedAtMillis WHERE subscriptionId = :subscriptionId")
    suspend fun activateSubscription(subscriptionId: String, activatedAtMillis: Long)

    @Query("UPDATE servers SET subscriptionAutoUpdateOnLaunchEnabled = :enabled WHERE subscriptionId = :subscriptionId")
    suspend fun updateSubscriptionAutoUpdateOnLaunch(subscriptionId: String, enabled: Boolean)

    @Query("UPDATE servers SET subscriptionActivatedAtMillis = :activatedAtMillis WHERE id = :serverId")
    suspend fun activateSingleServer(serverId: String, activatedAtMillis: Long)

    @Upsert
    suspend fun upsert(server: ServerEntity)

    @Upsert
    suspend fun upsertAll(servers: List<ServerEntity>)

    @Delete
    suspend fun delete(server: ServerEntity)

    @Query("DELETE FROM servers WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscriptionId(subscriptionId: String)
}
