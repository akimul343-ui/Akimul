package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscription_sources")
data class SubscriptionSource(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val lastUpdated: Long = 0L,
    val configCount: Int = 0,
    val lastStatus: String = "Not synced yet"
)
