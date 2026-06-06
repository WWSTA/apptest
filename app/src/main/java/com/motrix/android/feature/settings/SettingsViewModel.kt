package com.motrix.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motrix.android.domain.model.ThemeMode
import com.motrix.android.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SettingsUiState(
    val downloadDir: String = "",
    val maxConcurrentDownloads: Int = 5,
    val split: Int = 16,
    val maxOverallDownloadLimit: Long = 0,
    val maxDownloadLimit: Long = 0,
    val continueDownload: Boolean = true,
    val enableDht: Boolean = true,
    val btEnableLpd: Boolean = true,
    val btEnablePeerExchange: Boolean = true,
    val btListenPort: Int = 6881,
    val enableAutoUpdateTracker: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationSound: Boolean = true,
    val notificationVibrate: Boolean = false,
    val userAgent: String = "",
    val rpcPort: Int = 6800,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val dir = settingsRepository.getDownloadDir().first()
                val maxConcurrent = settingsRepository.getIntSetting(
                    SettingsRepository.KEY_MAX_CONCURRENT_DOWNLOADS,
                    SettingsRepository.DEFAULT_MAX_CONCURRENT_DOWNLOADS,
                ).first()
                val split = settingsRepository.getIntSetting(
                    SettingsRepository.KEY_SPLIT,
                    SettingsRepository.DEFAULT_SPLIT,
                ).first()
                val maxOverallLimit = settingsRepository.getStringSetting(
                    SettingsRepository.KEY_MAX_OVERALL_DOWNLOAD_LIMIT,
                    SettingsRepository.DEFAULT_MAX_OVERALL_DOWNLOAD_LIMIT,
                ).first().toLongOrNull() ?: 0L
                val maxLimit = settingsRepository.getStringSetting(
                    SettingsRepository.KEY_MAX_DOWNLOAD_LIMIT,
                    SettingsRepository.DEFAULT_MAX_DOWNLOAD_LIMIT,
                ).first().toLongOrNull() ?: 0L
                val continueDl = settingsRepository.getBooleanSetting(
                    SettingsRepository.KEY_CONTINUE_DOWNLOAD,
                    SettingsRepository.DEFAULT_CONTINUE_DOWNLOAD,
                ).first()
                val dht = settingsRepository.getBooleanSetting(
                    SettingsRepository.KEY_ENABLE_DHT,
                    SettingsRepository.DEFAULT_ENABLE_DHT,
                ).first()
                val listenPort = settingsRepository.getIntSetting(
                    SettingsRepository.KEY_BT_LISTEN_PORT,
                    SettingsRepository.DEFAULT_BT_LISTEN_PORT,
                ).first()
                val autoTracker = settingsRepository.getBooleanSetting(
                    SettingsRepository.KEY_ENABLE_AUTO_UPDATE_TRACKER,
                    SettingsRepository.DEFAULT_ENABLE_AUTO_UPDATE_TRACKER,
                ).first()
                val theme = settingsRepository.getThemeMode().first()
                val notifSound = settingsRepository.getBooleanSetting(
                    SettingsRepository.KEY_NOTIFICATION_SOUND,
                    SettingsRepository.DEFAULT_NOTIFICATION_SOUND,
                ).first()
                val notifVibrate = settingsRepository.getBooleanSetting(
                    SettingsRepository.KEY_NOTIFICATION_VIBRATE,
                    SettingsRepository.DEFAULT_NOTIFICATION_VIBRATE,
                ).first()

                _uiState.update {
                    it.copy(
                        downloadDir = dir,
                        maxConcurrentDownloads = maxConcurrent,
                        split = split,
                        maxOverallDownloadLimit = maxOverallLimit,
                        maxDownloadLimit = maxLimit,
                        continueDownload = continueDl,
                        enableDht = dht,
                        btListenPort = listenPort,
                        enableAutoUpdateTracker = autoTracker,
                        themeMode = theme,
                        notificationSound = notifSound,
                        notificationVibrate = notifVibrate,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load settings")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateDownloadDir(dir: String) {
        _uiState.update { it.copy(downloadDir = dir) }
        viewModelScope.launch {
            settingsRepository.setDownloadDir(dir)
        }
    }

    fun updateMaxConcurrentDownloads(max: Int) {
        _uiState.update { it.copy(maxConcurrentDownloads = max) }
        viewModelScope.launch {
            settingsRepository.setSetting(
                SettingsRepository.KEY_MAX_CONCURRENT_DOWNLOADS,
                max.toString(),
            )
        }
    }

    fun updateSplit(split: Int) {
        _uiState.update { it.copy(split = split) }
        viewModelScope.launch {
            settingsRepository.setSetting(
                SettingsRepository.KEY_SPLIT,
                split.toString(),
            )
        }
    }

    fun updateMaxOverallDownloadLimit(limit: Long) {
        _uiState.update { it.copy(maxOverallDownloadLimit = limit) }
        viewModelScope.launch {
            settingsRepository.setSetting(
                SettingsRepository.KEY_MAX_OVERALL_DOWNLOAD_LIMIT,
                limit.toString(),
            )
        }
    }

    fun updateMaxDownloadLimit(limit: Long) {
        _uiState.update { it.copy(maxDownloadLimit = limit) }
        viewModelScope.launch {
            settingsRepository.setSetting(
                SettingsRepository.KEY_MAX_DOWNLOAD_LIMIT,
                limit.toString(),
            )
        }
    }

    fun updateContinueDownload(enabled: Boolean) {
        _uiState.update { it.copy(continueDownload = enabled) }
        viewModelScope.launch {
            settingsRepository.setSetting(
                SettingsRepository.KEY_CONTINUE_DOWNLOAD,
                enabled.toString(),
            )
        }
    }

    fun updateEnableDht(enabled: Boolean) {
        _uiState.update { it.copy(enableDht = enabled) }
        viewModelScope.launch {
            settingsRepository.setSetting(
                SettingsRepository.KEY_ENABLE_DHT,
                enabled.toString(),
            )
        }
    }

    fun updateBtEnableLpd(enabled: Boolean) {
        _uiState.update { it.copy(btEnableLpd = enabled) }
        viewModelScope.launch {
            settingsRepository.setSetting("bt_enable_lpd", enabled.toString())
        }
    }

    fun updateBtEnablePeerExchange(enabled: Boolean) {
        _uiState.update { it.copy(btEnablePeerExchange = enabled) }
        viewModelScope.launch {
            settingsRepository.setSetting("bt_enable_peer_exchange", enabled.toString())
        }
    }

    fun updateBtListenPort(port: Int) {
        _uiState.update { it.copy(btListenPort = port) }
        viewModelScope.launch {
            settingsRepository.setSetting(
                SettingsRepository.KEY_BT_LISTEN_PORT,
                port.toString(),
            )
        }
    }

    fun updateEnableAutoUpdateTracker(enabled: Boolean) {
        _uiState.update { it.copy(enableAutoUpdateTracker = enabled) }
        viewModelScope.launch {
            settingsRepository.setSetting(
                SettingsRepository.KEY_ENABLE_AUTO_UPDATE_TRACKER,
                enabled.toString(),
            )
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun updateNotificationSound(enabled: Boolean) {
        _uiState.update { it.copy(notificationSound = enabled) }
        viewModelScope.launch {
            settingsRepository.setSetting(
                SettingsRepository.KEY_NOTIFICATION_SOUND,
                enabled.toString(),
            )
        }
    }

    fun updateNotificationVibrate(enabled: Boolean) {
        _uiState.update { it.copy(notificationVibrate = enabled) }
        viewModelScope.launch {
            settingsRepository.setSetting(
                SettingsRepository.KEY_NOTIFICATION_VIBRATE,
                enabled.toString(),
            )
        }
    }

    fun updateUserAgent(ua: String) {
        _uiState.update { it.copy(userAgent = ua) }
        viewModelScope.launch {
            settingsRepository.setSetting("user_agent", ua)
        }
    }

    fun updateRpcPort(port: Int) {
        _uiState.update { it.copy(rpcPort = port) }
        viewModelScope.launch {
            settingsRepository.setSetting("rpc_port", port.toString())
        }
    }
}
