package com.motrix.android.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class TaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "gid")
    val gid: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "state")
    val state: String,

    @ColumnInfo(name = "url")
    val url: String? = null,

    @ColumnInfo(name = "dir")
    val dir: String,

    @ColumnInfo(name = "total_length", defaultValue = "0")
    val totalLength: Long = 0,

    @ColumnInfo(name = "completed_length", defaultValue = "0")
    val completedLength: Long = 0,

    @ColumnInfo(name = "download_speed", defaultValue = "0")
    val downloadSpeed: Long = 0,

    @ColumnInfo(name = "upload_speed", defaultValue = "0")
    val uploadSpeed: Long = 0,

    @ColumnInfo(name = "connections", defaultValue = "0")
    val connections: Int = 0,

    @ColumnInfo(name = "num_seeders", defaultValue = "0")
    val numSeeders: Int = 0,

    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "user_agent")
    val userAgent: String? = null,

    @ColumnInfo(name = "referer")
    val referer: String? = null,

    @ColumnInfo(name = "headers")
    val headers: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "is_selected", defaultValue = "1")
    val isSelected: Int = 1
)
