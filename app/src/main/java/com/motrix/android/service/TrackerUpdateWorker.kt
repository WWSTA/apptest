package com.motrix.android.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import com.motrix.android.core.database.dao.TrackerDao
import com.motrix.android.core.database.entity.TrackerEntity
import com.motrix.android.core.engine.DownloadEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

@HiltWorker
class TrackerUpdateWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val trackerDao: TrackerDao,
    private val downloadEngine: DownloadEngine,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("TrackerUpdateWorker: Starting tracker update")

        return try {
            val trackerList = fetchTrackerList()
            if (trackerList.isEmpty()) {
                Timber.w("TrackerUpdateWorker: Empty tracker list fetched")
                return Result.success()
            }

            // Update tracker cache in Room
            withContext(Dispatchers.IO) {
                trackerDao.deleteAllTrackers()
                val entities = trackerList.map { url ->
                    TrackerEntity(
                        url = url,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                trackerDao.insertTrackers(entities)
            }

            Timber.d("TrackerUpdateWorker: Cached ${trackerList.size} trackers")

            // Update active BT tasks with new trackers
            updateActiveBtTasks(trackerList)

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "TrackerUpdateWorker: Failed to update trackers")
            Result.retry()
        }
    }

    private suspend fun fetchTrackerList(): List<String> = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_all.txt",
            "https://cdn.jsdelivr.net/gh/ngosang/trackerslist/trackers_all.txt",
        )

        for (url in urls) {
            try {
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    return@withContext body.lines()
                        .map { it.trim() }
                        .filter { it.startsWith("udp://") || it.startsWith("http://") || it.startsWith("https://") }
                        .distinct()
                }
            } catch (e: Exception) {
                Timber.w(e, "TrackerUpdateWorker: Failed to fetch from $url, trying next mirror")
            }
        }

        emptyList()
    }

    private suspend fun updateActiveBtTasks(trackerList: List<String>) {
        if (trackerList.isEmpty()) return

        try {
            val activeResult = downloadEngine.getActiveTasks()
            activeResult.onSuccess { tasks ->
                for (task in tasks) {
                    if (task.bittorrent != null) {
                        val trackerOption = mapOf(
                            "bt-tracker" to trackerList.joinToString(",")
                        )
                        downloadEngine.changeTaskOption(task.gid, trackerOption)
                            .onFailure { Timber.e(it, "Failed to update trackers for ${task.gid}") }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "TrackerUpdateWorker: Failed to update BT tasks")
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "tracker_update_work"

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
