package com.motrix.android.core.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getDefaultDownloadDir(): String {
        val externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val motrixDir = File(externalDir, "Motrix")

        if (!motrixDir.exists()) {
            val created = motrixDir.mkdirs()
            if (created) {
                Timber.i("Created default download directory: ${motrixDir.absolutePath}")
            } else {
                Timber.w("Failed to create default download directory, falling back to internal")
                val internalDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Motrix")
                internalDir.mkdirs()
                return internalDir.absolutePath
            }
        }

        return motrixDir.absolutePath
    }

    fun ensureDownloadDir(path: String): Boolean {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            return true
        }
        val created = dir.mkdirs()
        if (created) {
            Timber.i("Created download directory: $path")
        } else {
            Timber.e("Failed to create download directory: $path")
        }
        return created
    }

    fun getFreeSpace(path: String): Long {
        val dir = File(path)
        val statFs = StatFs(dir.absolutePath)
        return statFs.availableBlocksLong * statFs.blockSizeLong
    }

    fun deleteFile(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) {
            Timber.w("File does not exist: $path")
            return false
        }
        val deleted = file.delete()
        if (deleted) {
            Timber.i("Deleted file: $path")
        } else {
            Timber.e("Failed to delete file: $path")
        }
        return deleted
    }

    fun getFileInfo(path: String): FileInfo? {
        val file = File(path)
        if (!file.exists()) {
            return null
        }
        return FileInfo(
            path = file.absolutePath,
            name = file.name,
            size = file.length(),
            isDirectory = file.isDirectory,
            lastModified = file.lastModified(),
            exists = true
        )
    }

    fun isPathWritable(path: String): Boolean {
        val dir = File(path)
        if (!dir.exists()) {
            return dir.mkdirs() && dir.canWrite()
        }
        return dir.canWrite()
    }

    fun getFileSize(path: String): Long {
        val file = File(path)
        return if (file.exists()) file.length() else 0L
    }

    fun listFiles(path: String): List<FileInfo> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }
        return dir.listFiles()?.map { file ->
            FileInfo(
                path = file.absolutePath,
                name = file.name,
                size = file.length(),
                isDirectory = file.isDirectory,
                lastModified = file.lastModified(),
                exists = true
            )
        } ?: emptyList()
    }

    data class FileInfo(
        val path: String,
        val name: String,
        val size: Long,
        val isDirectory: Boolean,
        val lastModified: Long,
        val exists: Boolean
    )
}
