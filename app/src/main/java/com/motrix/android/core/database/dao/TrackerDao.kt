package com.motrix.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.motrix.android.core.database.entity.TrackerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {

    @Query("SELECT * FROM tracker_cache ORDER BY id ASC")
    fun getAllTrackers(): Flow<List<TrackerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackers(trackers: List<TrackerEntity>)

    @Query("DELETE FROM tracker_cache")
    suspend fun deleteAllTrackers()

    @Query("SELECT COUNT(*) FROM tracker_cache")
    fun getTrackerCount(): Flow<Int>
}
