package com.motrix.android.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.motrix.android.MainActivity
import com.motrix.android.core.engine.DownloadEngine
import androidx.core.app.NotificationManagerCompat
import com.motrix.android.core.engine.model.TaskInfo
import com.motrix.android.core.engine.model.TaskState
import com.motrix.android.domain.repository.TaskRepository
import com.motrix.android.feature.notification.DownloadNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadEngine: DownloadEngine

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var notificationManager: DownloadNotificationManager

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var progressJob: Job? = null
    private var isForeground = false

    companion object {
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.motrix.android.ACTION_START_DOWNLOAD"
        const val ACTION_STOP = "com.motrix.android.ACTION_STOP_DOWNLOAD"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDownload()
            ACTION_STOP -> stopDownload()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        progressJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startDownload() {
        if (!isForeground) {
            val notification = createForegroundNotification("Download in progress")
            try {
                startForeground(NOTIFICATION_ID, notification)
                isForeground = true
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (e is ForegroundServiceStartNotAllowedException) {
                        Timber.e(e, "Cannot start foreground service from background")
                        return
                    }
                }
                Timber.e(e, "Failed to start foreground")
            }
        }

        observeProgress()
    }

    private fun stopDownload() {
        progressJob?.cancel()
        progressJob = null

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isForeground = false

        releaseWakeLock()
        stopSelf()
    }

    private fun observeProgress() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            taskRepository.getGlobalStat()
                .catch { Timber.e(it, "Failed to observe global stat in service") }
                .collect { stat ->
                    if (stat.numActive == 0 && stat.numWaiting == 0) {
                        stopDownload()
                        return@collect
                    }

                    val activeResult = downloadEngine.getActiveTasks()
                    activeResult.onSuccess { tasks ->
                        tasks.forEach { task ->
                            notificationManager.updateDownloadNotification(task)
                        }
                        updateForegroundNotification(tasks)
                    }
                }
        }
    }

    private fun updateForegroundNotification(tasks: List<TaskInfo>) {
        if (!isForeground) return

        val activeCount = tasks.count { it.state == TaskState.ACTIVE }
        val totalSpeed = tasks.sumOf { it.downloadSpeed }
        val speedText = com.motrix.android.core.common.util.FormatUtils.formatSpeed(totalSpeed)

        val notification = createForegroundNotification(
            "$activeCount active · $speedText",
        )
        val nm = NotificationManagerCompat.from(this@DownloadService)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createForegroundNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(
            this,
            DownloadNotificationManager.CHANNEL_DOWNLOAD_PROGRESS,
        )
            .setContentTitle("Motrix")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Motrix::DownloadWakeLock",
        ).apply {
            acquire(4 * 60 * 60 * 1000L) // 4 hours max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
}
