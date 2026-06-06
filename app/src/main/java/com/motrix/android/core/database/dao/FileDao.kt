package com.motrix.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.motrix.android.core.database.entity.FileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {

    @Query("SELECT * FROM download_files WHERE task_gid = :taskGid ORDER BY file_index ASC")
    fun getFilesForTask(taskGid: String): Flow<List<FileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileEntity>)

    @Query("UPDATE download_files SET selected = :selected WHERE id = :id")
    suspend fun updateFileSelection(id: Long, selected: Int)

    @Query("DELETE FROM download_files WHERE task_gid = :taskGid")
    suspend fun deleteFilesForTask(taskGid: String)
}
