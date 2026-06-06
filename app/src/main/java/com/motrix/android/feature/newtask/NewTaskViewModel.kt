package com.motrix.android.feature.newtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motrix.android.core.common.util.MagnetUriParser
import com.motrix.android.domain.model.DownloadOptions
import com.motrix.android.domain.repository.SettingsRepository
import com.motrix.android.domain.usecase.AddDownloadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class NewTaskUiState(
    val url: String = "",
    val downloadDir: String = "",
    val filename: String = "",
    val split: String = "16",
    val userAgent: String = "",
    val referer: String = "",
    val headers: String = "",
    val isAdvancedExpanded: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val urlType: UrlType = UrlType.UNKNOWN,
)

enum class UrlType {
    HTTP, MAGNET, UNKNOWN
}

@HiltViewModel
class NewTaskViewModel @Inject constructor(
    private val addDownloadUseCase: AddDownloadUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewTaskUiState())
    val uiState: StateFlow<NewTaskUiState> = _uiState.asStateFlow()

    init {
        loadDefaultSettings()
    }

    private fun loadDefaultSettings() {
        viewModelScope.launch {
            val dir = settingsRepository.getDownloadDir().first()
            _uiState.update { it.copy(downloadDir = dir) }
        }
    }

    fun onUrlChanged(url: String) {
        val urlType = detectUrlType(url)
        _uiState.update {
            it.copy(
                url = url,
                urlType = urlType,
                error = null,
            )
        }
    }

    fun onDownloadDirChanged(dir: String) {
        _uiState.update { it.copy(downloadDir = dir) }
    }

    fun onFilenameChanged(filename: String) {
        _uiState.update { it.copy(filename = filename) }
    }

    fun onSplitChanged(split: String) {
        _uiState.update { it.copy(split = split) }
    }

    fun onUserAgentChanged(ua: String) {
        _uiState.update { it.copy(userAgent = ua) }
    }

    fun onRefererChanged(referer: String) {
        _uiState.update { it.copy(referer = referer) }
    }

    fun onHeadersChanged(headers: String) {
        _uiState.update { it.copy(headers = headers) }
    }

    fun onAdvancedExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isAdvancedExpanded = expanded) }
    }

    fun onSubmit() {
        val state = _uiState.value
        val url = state.url.trim()

        if (url.isBlank()) {
            _uiState.update { it.copy(error = "URL cannot be empty") }
            return
        }

        if (state.urlType == UrlType.UNKNOWN) {
            _uiState.update { it.copy(error = "Invalid URL format. Enter a valid HTTP/HTTPS or magnet link.") }
            return
        }

        val splitValue = state.split.toIntOrNull()
        if (splitValue != null && splitValue !in 1..64) {
            _uiState.update { it.copy(error = "Split must be between 1 and 64") }
            return
        }

        val headerList = if (state.headers.isNotBlank()) {
            state.headers.split("\n").filter { it.contains(":") }
        } else null

        val options = DownloadOptions(
            dir = state.downloadDir.ifBlank { null },
            filename = state.filename.ifBlank { null },
            split = splitValue,
            userAgent = state.userAgent.ifBlank { null },
            referer = state.referer.ifBlank { null },
            header = headerList,
        )

        _uiState.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            val result = addDownloadUseCase(url, options)

            result
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
                }
                .onFailure {
                    Timber.e(it, "Add download failed")
                    _uiState.update { state ->
                        state.copy(
                            isSubmitting = false,
                            error = it.message ?: "Failed to add download",
                        )
                    }
                }
        }
    }

    private fun detectUrlType(url: String): UrlType {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("ftp://", ignoreCase = true) -> UrlType.HTTP
            MagnetUriParser.isValidMagnetUri(trimmed) -> UrlType.MAGNET
            else -> UrlType.UNKNOWN
        }
    }
}
