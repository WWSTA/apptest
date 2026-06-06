package com.motrix.android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.motrix.android.core.database.dao.SettingsDao
import com.motrix.android.core.database.entity.SettingsEntity
import com.motrix.android.core.engine.model.EngineConfig
import com.motrix.android.domain.model.ThemeMode
import com.motrix.android.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDao: SettingsDao,
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override fun getSetting(key: String): Flow<String?> {
        return settingsDao.getSetting(key).map { it?.value }
    }

    override fun getStringSetting(key: String, defaultValue: String): Flow<String> {
        return settingsDao.getSetting(key).map { entity ->
            entity?.value ?: defaultValue
        }
    }

    override fun getIntSetting(key: String, defaultValue: Int): Flow<Int> {
        return settingsDao.getSetting(key).map { entity ->
            entity?.value?.toIntOrNull() ?: defaultValue
        }
    }

    override fun getBooleanSetting(key: String, defaultValue: Boolean): Flow<Boolean> {
        return settingsDao.getSetting(key).map { entity ->
            entity?.value?.toBooleanStrictOrNull() ?: defaultValue
        }
    }

    override suspend fun setSetting(key: String, value: String) {
        runCatching {
            val entity = SettingsEntity(
                key = key,
                value = value,
                updatedAt = System.currentTimeMillis()
            )
            settingsDao.upsertSetting(entity)
            updateDataStoreCache(key, value)
            Timber.d("Setting updated: $key = $value")
        }.onFailure { e ->
            Timber.e(e, "Failed to set setting: $key")
        }
    }

    override fun getThemeMode(): Flow<ThemeMode> {
        return getStringSetting(KEY_THEME_MODE, DEFAULT_THEME_MODE).map { value ->
            when (value) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        val value = when (mode) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.SYSTEM -> "system"
        }
        setSetting(KEY_THEME_MODE, value)
    }

    override fun getDownloadDir(): Flow<String> {
        return getStringSetting(KEY_DOWNLOAD_DIR, DEFAULT_DOWNLOAD_DIR)
    }

    override suspend fun setDownloadDir(dir: String) {
        setSetting(KEY_DOWNLOAD_DIR, dir)
    }

    override fun getEngineConfig(): Flow<EngineConfig> {
        val downloadDirFlow = getStringSetting(KEY_DOWNLOAD_DIR, DEFAULT_DOWNLOAD_DIR)
        val maxConcurrentFlow = getIntSetting(KEY_MAX_CONCURRENT_DOWNLOADS, DEFAULT_MAX_CONCURRENT_DOWNLOADS)
        val maxConnectionFlow = getIntSetting(KEY_MAX_CONNECTION_PER_SERVER, DEFAULT_MAX_CONNECTION_PER_SERVER)
        val splitFlow = getIntSetting(KEY_SPLIT, DEFAULT_SPLIT)
        val overallLimitFlow = getStringSetting(KEY_MAX_OVERALL_DOWNLOAD_LIMIT, DEFAULT_MAX_OVERALL_DOWNLOAD_LIMIT)
        val downloadLimitFlow = getStringSetting(KEY_MAX_DOWNLOAD_LIMIT, DEFAULT_MAX_DOWNLOAD_LIMIT)
        val continueFlow = getBooleanSetting(KEY_CONTINUE_DOWNLOAD, DEFAULT_CONTINUE_DOWNLOAD)
        val dhtFlow = getBooleanSetting(KEY_ENABLE_DHT, DEFAULT_ENABLE_DHT)
        val portFlow = getIntSetting(KEY_BT_LISTEN_PORT, DEFAULT_BT_LISTEN_PORT)

        // Combine in two stages since kotlinx.coroutines combine supports up to 5 flows
        val stage1 = combine(
            downloadDirFlow,
            maxConcurrentFlow,
            maxConnectionFlow,
            splitFlow,
            overallLimitFlow
        ) { downloadDir, maxConcurrent, maxConnection, split, overallLimit ->
            Stage1Result(downloadDir, maxConcurrent, maxConnection, split, overallLimit)
        }

        val stage2 = combine(
            downloadLimitFlow,
            continueFlow,
            dhtFlow,
            portFlow
        ) { downloadLimit, continueDl, enableDht, listenPort ->
            Stage2Result(downloadLimit, continueDl, enableDht, listenPort)
        }

        return combine(stage1, stage2) { s1, s2 ->
            EngineConfig(
                downloadDir = s1.downloadDir,
                maxConcurrentDownloads = s1.maxConcurrent,
                maxConnectionPerServer = s1.maxConnection,
                split = s1.split,
                maxOverallDownloadLimit = s1.overallLimit,
                maxDownloadLimit = s2.downloadLimit,
                continueDownload = s2.continueDl,
                enableDht = s2.enableDht,
                listenPort = s2.listenPort
            )
        }
    }

    private data class Stage1Result(
        val downloadDir: String,
        val maxConcurrent: Int,
        val maxConnection: Int,
        val split: Int,
        val overallLimit: String
    )

    private data class Stage2Result(
        val downloadLimit: String,
        val continueDl: Boolean,
        val enableDht: Boolean,
        val listenPort: Int
    )

    private suspend fun updateDataStoreCache(key: String, value: String) {
        runCatching {
            dataStore.edit { preferences ->
                when {
                    value == "true" || value == "false" -> {
                        preferences[booleanPreferencesKey(key)] = value.toBoolean()
                    }

                    value.toIntOrNull() != null -> {
                        preferences[intPreferencesKey(key)] = value.toInt()
                    }

                    else -> {
                        preferences[stringPreferencesKey(key)] = value
                    }
                }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to update DataStore cache for key: $key")
        }
    }

    private suspend fun ensureDefaults() {
        val defaults = mapOf(
            KEY_DOWNLOAD_DIR to DEFAULT_DOWNLOAD_DIR,
            KEY_MAX_CONCURRENT_DOWNLOADS to DEFAULT_MAX_CONCURRENT_DOWNLOADS.toString(),
            KEY_MAX_CONNECTION_PER_SERVER to DEFAULT_MAX_CONNECTION_PER_SERVER.toString(),
            KEY_SPLIT to DEFAULT_SPLIT.toString(),
            KEY_MAX_OVERALL_DOWNLOAD_LIMIT to DEFAULT_MAX_OVERALL_DOWNLOAD_LIMIT,
            KEY_MAX_DOWNLOAD_LIMIT to DEFAULT_MAX_DOWNLOAD_LIMIT,
            KEY_CONTINUE_DOWNLOAD to DEFAULT_CONTINUE_DOWNLOAD.toString(),
            KEY_ENABLE_DHT to DEFAULT_ENABLE_DHT.toString(),
            KEY_BT_LISTEN_PORT to DEFAULT_BT_LISTEN_PORT.toString(),
            KEY_ENABLE_AUTO_UPDATE_TRACKER to DEFAULT_ENABLE_AUTO_UPDATE_TRACKER.toString(),
            KEY_TRACKER_LIST_URL to DEFAULT_TRACKER_LIST_URL,
            KEY_THEME_MODE to DEFAULT_THEME_MODE,
            KEY_NOTIFICATION_SOUND to DEFAULT_NOTIFICATION_SOUND.toString(),
            KEY_NOTIFICATION_VIBRATE to DEFAULT_NOTIFICATION_VIBRATE.toString(),
            KEY_DELETE_WITH_FILE to DEFAULT_DELETE_WITH_FILE.toString()
        )

        for ((key, defaultValue) in defaults) {
            val existing = settingsDao.getSetting(key).first()
            if (existing == null) {
                setSetting(key, defaultValue)
                Timber.d("Initialized default setting: $key = $defaultValue")
            }
        }
    }

    suspend fun initializeDefaults() {
        ensureDefaults()
    }
}
