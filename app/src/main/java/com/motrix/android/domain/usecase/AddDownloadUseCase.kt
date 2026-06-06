package com.motrix.android.domain.usecase

import com.motrix.android.domain.model.DownloadOptions
import com.motrix.android.domain.repository.SettingsRepository
import com.motrix.android.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class AddDownloadUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository
) {

    suspend operator fun invoke(url: String, options: DownloadOptions? = null): Result<String> {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("URL cannot be empty"))
        }

        val downloadOptions = options ?: DownloadOptions()
        val mergedOptions = mergeWithDefaults(downloadOptions)

        return when {
            isMagnetUri(trimmedUrl) -> {
                Timber.d("Detected magnet URI: $trimmedUrl")
                taskRepository.addMagnetDownload(trimmedUrl, mergedOptions)
            }

            isTorrentFile(trimmedUrl) -> {
                Timber.d("Detected torrent file: $trimmedUrl")
                taskRepository.addTorrentDownload(trimmedUrl, mergedOptions)
            }

            isHttpUrl(trimmedUrl) -> {
                Timber.d("Detected HTTP/FTP URL: $trimmedUrl")
                taskRepository.addHttpDownload(trimmedUrl, mergedOptions)
            }

            else -> {
                Timber.w("Unrecognized URL format: $trimmedUrl")
                Result.failure(IllegalArgumentException("Unsupported URL format: $trimmedUrl"))
            }
        }
    }

    private suspend fun mergeWithDefaults(options: DownloadOptions): DownloadOptions {
        val defaultDir = settingsRepository.getDownloadDir().first()
        return options.copy(
            dir = options.dir ?: defaultDir,
            split = options.split ?: SettingsRepository.DEFAULT_SPLIT,
            maxConnectionPerServer = options.maxConnectionPerServer
                ?: SettingsRepository.DEFAULT_MAX_CONNECTION_PER_SERVER
        )
    }

    private fun isMagnetUri(url: String): Boolean {
        return url.startsWith("magnet:?", ignoreCase = true)
    }

    private fun isTorrentFile(url: String): Boolean {
        return url.endsWith(".torrent", ignoreCase = true) ||
                (url.startsWith("/") && File(url).exists() && url.endsWith(".torrent", ignoreCase = true))
    }

    private fun isHttpUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("ftp://", ignoreCase = true)
    }
}
