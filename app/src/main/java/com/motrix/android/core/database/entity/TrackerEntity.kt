package com.motrix.android.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracker_cache")
data class TrackerEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "url", unique = true)
    val url: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
