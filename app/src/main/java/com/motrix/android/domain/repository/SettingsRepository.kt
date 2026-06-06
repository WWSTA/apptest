package com.motrix.android.domain.repository

import com.motrix.android.core.engine.model.EngineConfig
import com.motrix.android.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    fun getSetting(key: String): Flow<String?>

    fun getStringSetting(key: String, defaultValue: String): Flow<String>

    fun getIntSetting(key: String, defaultValue: Int): Flow<Int>

    fun getBooleanSetting(key: String, defaultValue: Boolean): Flow<Boolean>

    suspend fun setSetting(key: String, value: String)

    fun getThemeMode(): Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    fun getDownloadDir(): Flow<String>

    suspend fun setDownloadDir(dir: String)

    fun getEngineConfig(): Flow<EngineConfig>

    companion object {
        const val KEY_DOWNLOAD_DIR = "download_dir"
        const val KEY_MAX_CONCURRENT_DOWNLOADS = "max_concurrent_downloads"
        const val KEY_MAX_CONNECTION_PER_SERVER = "max_connection_per_server"
        const val KEY_SPLIT = "split"
        const val KEY_MAX_OVERALL_DOWNLOAD_LIMIT = "max_overall_download_limit"
        const val KEY_MAX_DOWNLOAD_LIMIT = "max_download_limit"
        const val KEY_CONTINUE_DOWNLOAD = "continue_download"
        const val KEY_ENABLE_DHT = "enable_dht"
        const val KEY_BT_LISTEN_PORT = "bt_listen_port"
        const val KEY_ENABLE_AUTO_UPDATE_TRACKER = "enable_auto_update_tracker"
        const val KEY_TRACKER_LIST_URL = "tracker_list_url"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_NOTIFICATION_SOUND = "notification_sound"
        const val KEY_NOTIFICATION_VIBRATE = "notification_vibrate"
        const val KEY_DELETE_WITH_FILE = "delete_with_file"

        const val DEFAULT_DOWNLOAD_DIR = "/sdcard/Download/Motrix"
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 5
        const val DEFAULT_MAX_CONNECTION_PER_SERVER = 16
        const val DEFAULT_SPLIT = 16
        const val DEFAULT_MAX_OVERALL_DOWNLOAD_LIMIT = "0"
        const val DEFAULT_MAX_DOWNLOAD_LIMIT = "0"
        const val DEFAULT_CONTINUE_DOWNLOAD = true
        const val DEFAULT_ENABLE_DHT = true
        const val DEFAULT_BT_LISTEN_PORT = 6881
        const val DEFAULT_ENABLE_AUTO_UPDATE_TRACKER = true
        const val DEFAULT_TRACKER_LIST_URL = ""
        const val DEFAULT_THEME_MODE = "system"
        const val DEFAULT_NOTIFICATION_SOUND = true
        const val DEFAULT_NOTIFICATION_VIBRATE = false
        const val DEFAULT_DELETE_WITH_FILE = false
    }
}
