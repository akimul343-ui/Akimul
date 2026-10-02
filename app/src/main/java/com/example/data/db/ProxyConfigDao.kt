package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ProxyConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyConfigDao {
    @Query("SELECT * FROM proxy_configs ORDER BY id DESC")
    fun getAllConfigs(): Flow<List<ProxyConfig>>

    @Query("SELECT * FROM proxy_configs WHERE isFavorite = 1 ORDER BY id DESC")
    fun getFavoriteConfigs(): Flow<List<ProxyConfig>>

    @Query("SELECT * FROM proxy_configs WHERE protocol = :protocol ORDER BY id DESC")
    fun getConfigsByProtocol(protocol: String): Flow<List<ProxyConfig>>

    @Query("SELECT COUNT(*) FROM proxy_configs")
    fun getConfigCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM proxy_configs")
    suspend fun getConfigCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<ProxyConfig>)

    @Update
    suspend fun updateConfig(config: ProxyConfig)

    @Query("UPDATE proxy_configs SET latencyMs = :latencyMs WHERE id = :id")
    suspend fun updateLatency(id: Long, latencyMs: Long)

    @Query("UPDATE proxy_configs SET isFavorite = :isFav WHERE id = :id")
    suspend fun setFavorite(id: Long, isFav: Boolean)

    @Delete
    suspend fun deleteConfig(config: ProxyConfig)

    @Query("DELETE FROM proxy_configs WHERE sourceUrl = :sourceUrl")
    suspend fun deleteConfigsBySource(sourceUrl: String)

    @Query("DELETE FROM proxy_configs")
    suspend fun deleteAllConfigs()
}
