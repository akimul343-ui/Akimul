package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SubscriptionSource
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscription_sources ORDER BY id ASC")
    fun getAllSubscriptions(): Flow<List<SubscriptionSource>>

    @Query("SELECT * FROM subscription_sources WHERE isEnabled = 1")
    suspend fun getEnabledSubscriptions(): List<SubscriptionSource>

    @Query("SELECT * FROM subscription_sources WHERE id = :id LIMIT 1")
    suspend fun getSubscriptionById(id: Long): SubscriptionSource?

    @Query("SELECT COUNT(*) FROM subscription_sources")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionSource): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subscriptions: List<SubscriptionSource>)

    @Update
    suspend fun updateSubscription(subscription: SubscriptionSource)

    @Delete
    suspend fun deleteSubscription(subscription: SubscriptionSource)

    @Query("DELETE FROM subscription_sources WHERE id = :id")
    suspend fun deleteById(id: Long)
}
