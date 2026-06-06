package com.motrix.android.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_files",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["gid"],
            childColumns = ["task_gid"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "task_gid", index = true)
    val taskGid: String,

    @ColumnInfo(name = "file_index")
    val fileIndex: Int,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "length", defaultValue = "0")
    val length: Long = 0,

    @ColumnInfo(name = "completed_length", defaultValue = "0")
    val completedLength: Long = 0,

    @ColumnInfo(name = "selected", defaultValue = "1")
    val selected: Int = 1
)
