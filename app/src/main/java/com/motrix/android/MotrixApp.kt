package com.motrix.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.motrix.android.service.DownloadWorker
import com.motrix.android.service.TrackerUpdateWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MotrixApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        initTimber()
        schedulePeriodicWorks()
        triggerInitialSync()
    }

    private fun initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun schedulePeriodicWorks() {
        val workManager = WorkManager.getInstance(this)

        val syncWork = PeriodicWorkRequestBuilder<DownloadWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(DownloadWorker.constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            DownloadWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncWork
        )

        val trackerWork = PeriodicWorkRequestBuilder<TrackerUpdateWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(TrackerUpdateWorker.constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            TrackerUpdateWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            trackerWork
        )
    }

    private fun triggerInitialSync() {
        val workManager = WorkManager.getInstance(this)
        workManager.enqueue(DownloadWorker.createOneTimeWorkRequest())
    }
}

private object Log {
    const val DEBUG = 3
    const val ERROR = 6
}
