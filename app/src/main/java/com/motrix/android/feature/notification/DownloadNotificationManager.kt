package com.motrix.android.feature.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.motrix.android.MainActivity
import com.motrix.android.core.engine.model.TaskInfo
import com.motrix.android.core.engine.model.TaskState
import com.motrix.android.core.common.util.FormatUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DownloadNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val notificationManager = NotificationManagerCompat.from(context)

    companion object {
        const val CHANNEL_DOWNLOAD_PROGRESS = "download_progress"
        const val CHANNEL_DOWNLOAD_COMPLETE = "download_complete"
        const val CHANNEL_DOWNLOAD_ERROR = "download_error"

        const val GROUP_DOWNLOADS = "com.motrix.android.DOWNLOADS"

        const val ACTION_PAUSE = "com.motrix.android.ACTION_PAUSE"
        const val ACTION_RESUME = "com.motrix.android.ACTION_RESUME"
        const val ACTION_CANCEL = "com.motrix.android.ACTION_CANCEL"

        private const val REQUEST_CODE_OPEN = 1000
        private const val REQUEST_CODE_PAUSE = 1001
        private const val REQUEST_CODE_RESUME = 1002
        private const val REQUEST_CODE_CANCEL = 1003
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_DOWNLOAD_PROGRESS,
                    "Download Progress",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows download progress and speed"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_DOWNLOAD_COMPLETE,
                    "Download Complete",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Notifies when downloads complete"
                },
                NotificationChannel(
                    CHANNEL_DOWNLOAD_ERROR,
                    "Download Error",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Notifies when downloads fail"
                },
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }

    fun showDownloadNotification(taskInfo: TaskInfo): Int {
        val notificationId = taskInfo.gid.hashCode()
        val notification = buildProgressNotification(taskInfo)
        notificationManager.notify(notificationId, notification)
        return notificationId
    }

    fun updateDownloadNotification(taskInfo: TaskInfo) {
        val notificationId = taskInfo.gid.hashCode()
        val notification = buildProgressNotification(taskInfo)
        notificationManager.notify(notificationId, notification)
    }

    fun showCompleteNotification(taskInfo: TaskInfo) {
        val notificationId = taskInfo.gid.hashCode()
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_COMPLETE)
            .setContentTitle(taskInfo.name)
            .setContentText("Download complete")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setGroup(GROUP_DOWNLOADS)
            .setAutoCancel(true)
            .setContentIntent(createOpenAppPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun showErrorNotification(taskInfo: TaskInfo) {
        val notificationId = taskInfo.gid.hashCode()
        val errorMessage = taskInfo.errorMessage ?: "Unknown error"
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_ERROR)
            .setContentTitle("${taskInfo.name} - Error")
            .setContentText(errorMessage)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setGroup(GROUP_DOWNLOADS)
            .setAutoCancel(true)
            .setContentIntent(createOpenAppPendingIntent())
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Error ${taskInfo.errorCode}: $errorMessage")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(gid: String) {
        val notificationId = gid.hashCode()
        notificationManager.cancel(notificationId)
    }

    private fun buildProgressNotification(taskInfo: TaskInfo): Notification {
        val progress = if (taskInfo.totalLength > 0) {
            ((taskInfo.completedLength * 100) / taskInfo.totalLength).toInt()
        } else 0

        val speedText = FormatUtils.formatSpeed(taskInfo.downloadSpeed)
        val sizeText = FormatUtils.formatFileSize(taskInfo.completedLength) +
            " / " + FormatUtils.formatFileSize(taskInfo.totalLength)

        val isPaused = taskInfo.state == TaskState.PAUSED

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_PROGRESS)
            .setContentTitle(taskInfo.name)
            .setContentText("$speedText · $sizeText")
            .setSmallIcon(
                when (taskInfo.state) {
                    TaskState.ACTIVE -> android.R.drawable.stat_sys_download
                    TaskState.PAUSED -> android.R.drawable.ic_media_pause
                    else -> android.R.drawable.stat_sys_download
                }
            )
            .setProgress(100, progress, false)
            .setOngoing(!isPaused)
            .setGroup(GROUP_DOWNLOADS)
            .setContentIntent(createOpenAppPendingIntent())
            .addAction(
                if (isPaused) createResumeAction(taskInfo.gid) else createPauseAction(taskInfo.gid)
            )
            .addAction(createCancelAction(taskInfo.gid))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun createOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createPauseAction(gid: String): NotificationCompat.Action {
        val intent = Intent(ACTION_PAUSE).apply {
            putExtra("gid", gid)
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PAUSE + gid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_pause,
            "Pause",
            pendingIntent,
        ).build()
    }

    private fun createResumeAction(gid: String): NotificationCompat.Action {
        val intent = Intent(ACTION_RESUME).apply {
            putExtra("gid", gid)
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_RESUME + gid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_play,
            "Resume",
            pendingIntent,
        ).build()
    }

    private fun createCancelAction(gid: String): NotificationCompat.Action {
        val intent = Intent(ACTION_CANCEL).apply {
            putExtra("gid", gid)
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CANCEL + gid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Cancel",
            pendingIntent,
        ).build()
    }
}
