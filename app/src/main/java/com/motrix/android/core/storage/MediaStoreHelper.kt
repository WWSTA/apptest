package com.motrix.android.core.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun addDownloadToMediaStore(filePath: String, mimeType: String): Uri? {
        val file = File(filePath)
        if (!file.exists()) {
            Timber.w("File does not exist: $filePath")
            return null
        }

        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.SIZE, file.length())
                put(MediaStore.Downloads.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Downloads.DATE_MODIFIED, System.currentTimeMillis() / 1000)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }

            Timber.i("Added file to MediaStore: $filePath -> $uri")
            uri
        } catch (e: Exception) {
            Timber.e(e, "Failed to add file to MediaStore: $filePath")
            null
        }
    }

    fun queryDownloadFile(fileName: String): Uri? {
        return try {
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME
            )
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)

            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val id = cursor.getLong(idColumnIndex)
                    Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to query download file: $fileName")
            null
        }
    }

    fun deleteFromMediaStore(contentUri: Uri): Boolean {
        return try {
            val deleted = context.contentResolver.delete(contentUri, null, null) > 0
            if (deleted) {
                Timber.i("Deleted from MediaStore: $contentUri")
            } else {
                Timber.w("No rows deleted from MediaStore: $contentUri")
            }
            deleted
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete from MediaStore: $contentUri")
            false
        }
    }

    fun notifyMediaScanner(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE
                )
                intent.data = Uri.fromFile(file)
                context.sendBroadcast(intent)
                Timber.d("Notified media scanner for: $filePath")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to notify media scanner for: $filePath")
        }
    }
}
