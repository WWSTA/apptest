package com.motrix.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.motrix.android.core.database.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM download_tasks ORDER BY updated_at DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE state = :state ORDER BY updated_at DESC")
    fun getTasks(state: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE gid = :gid")
    fun getTask(gid: String): Flow<TaskEntity?>

    @Query("SELECT * FROM download_tasks WHERE state = 'ACTIVE' ORDER BY updated_at DESC")
    fun getActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE state IN (:states) ORDER BY updated_at DESC")
    fun getTasksByState(states: List<String>): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Query("DELETE FROM download_tasks WHERE gid = :gid")
    suspend fun deleteTask(gid: String)

    @Query("DELETE FROM download_tasks WHERE gid IN (:gids)")
    suspend fun deleteTasks(gids: List<String>)

    @Query("UPDATE download_tasks SET state = :state, updated_at = :updatedAt WHERE gid = :gid")
    suspend fun updateTaskState(gid: String, state: String, updatedAt: Long)

    @Query(
        """
        UPDATE download_tasks
        SET completed_length = :completedLength,
            download_speed = :downloadSpeed,
            upload_speed = :uploadSpeed,
            connections = :connections,
            num_seeders = :numSeeders,
            updated_at = :updatedAt
        WHERE gid = :gid
        """
    )
    suspend fun updateTaskProgress(
        gid: String,
        completedLength: Long,
        downloadSpeed: Long,
        uploadSpeed: Long,
        connections: Int,
        numSeeders: Int,
        updatedAt: Long
    )
}
