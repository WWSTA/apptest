package com.motrix.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motrix.android.domain.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onUpdateDownloadDir = viewModel::updateDownloadDir,
        onUpdateMaxConcurrentDownloads = viewModel::updateMaxConcurrentDownloads,
        onUpdateSplit = viewModel::updateSplit,
        onUpdateMaxOverallDownloadLimit = viewModel::updateMaxOverallDownloadLimit,
        onUpdateMaxDownloadLimit = viewModel::updateMaxDownloadLimit,
        onUpdateContinueDownload = viewModel::updateContinueDownload,
        onUpdateEnableDht = viewModel::updateEnableDht,
        onUpdateBtEnableLpd = viewModel::updateBtEnableLpd,
        onUpdateBtEnablePeerExchange = viewModel::updateBtEnablePeerExchange,
        onUpdateBtListenPort = viewModel::updateBtListenPort,
        onUpdateEnableAutoUpdateTracker = viewModel::updateEnableAutoUpdateTracker,
        onUpdateThemeMode = viewModel::updateThemeMode,
        onUpdateNotificationSound = viewModel::updateNotificationSound,
        onUpdateNotificationVibrate = viewModel::updateNotificationVibrate,
        onUpdateUserAgent = viewModel::updateUserAgent,
        onUpdateRpcPort = viewModel::updateRpcPort,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onUpdateDownloadDir: (String) -> Unit,
    onUpdateMaxConcurrentDownloads: (Int) -> Unit,
    onUpdateSplit: (Int) -> Unit,
    onUpdateMaxOverallDownloadLimit: (Long) -> Unit,
    onUpdateMaxDownloadLimit: (Long) -> Unit,
    onUpdateContinueDownload: (Boolean) -> Unit,
    onUpdateEnableDht: (Boolean) -> Unit,
    onUpdateBtEnableLpd: (Boolean) -> Unit,
    onUpdateBtEnablePeerExchange: (Boolean) -> Unit,
    onUpdateBtListenPort: (Int) -> Unit,
    onUpdateEnableAutoUpdateTracker: (Boolean) -> Unit,
    onUpdateThemeMode: (ThemeMode) -> Unit,
    onUpdateNotificationSound: (Boolean) -> Unit,
    onUpdateNotificationVibrate: (Boolean) -> Unit,
    onUpdateUserAgent: (String) -> Unit,
    onUpdateRpcPort: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(32.dp),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Basic Settings
            SettingsSectionHeader(title = "Basic")
            SettingsDivider()

            var downloadDir by remember(uiState.downloadDir) {
                mutableStateOf(uiState.downloadDir)
            }
            OutlinedTextField(
                value = downloadDir,
                onValueChange = {
                    downloadDir = it
                    onUpdateDownloadDir(it)
                },
                label = { Text("Download Directory") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Browse",
                    )
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Max Concurrent Downloads: ${uiState.maxConcurrentDownloads}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = uiState.maxConcurrentDownloads.toFloat(),
                onValueChange = { onUpdateMaxConcurrentDownloads(it.toInt()) },
                valueRange = 1f..10f,
                steps = 9,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Split (Connections per Server): ${uiState.split}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = uiState.split.toFloat(),
                onValueChange = { onUpdateSplit(it.toInt()) },
                valueRange = 1f..64f,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            var overallLimit by remember(uiState.maxOverallDownloadLimit) {
                mutableStateOf(uiState.maxOverallDownloadLimit.toString())
            }
            OutlinedTextField(
                value = overallLimit,
                onValueChange = {
                    overallLimit = it
                    it.toLongOrNull()?.let { limit -> onUpdateMaxOverallDownloadLimit(limit) }
                },
                label = { Text("Overall Speed Limit (KB/s, 0=unlimited)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Spacer(modifier = Modifier.height(12.dp))

            var downloadLimit by remember(uiState.maxDownloadLimit) {
                mutableStateOf(uiState.maxDownloadLimit.toString())
            }
            OutlinedTextField(
                value = downloadLimit,
                onValueChange = {
                    downloadLimit = it
                    it.toLongOrNull()?.let { limit -> onUpdateMaxDownloadLimit(limit) }
                },
                label = { Text("Per-Task Speed Limit (KB/s, 0=unlimited)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitchRow(
                title = "Continue Download (Resume)",
                checked = uiState.continueDownload,
                onCheckedChange = onUpdateContinueDownload,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // BT Settings
            SettingsSectionHeader(title = "BitTorrent")
            SettingsDivider()

            SettingsSwitchRow(
                title = "Enable DHT",
                checked = uiState.enableDht,
                onCheckedChange = onUpdateEnableDht,
            )

            SettingsSwitchRow(
                title = "Enable LPD",
                checked = uiState.btEnableLpd,
                onCheckedChange = onUpdateBtEnableLpd,
            )

            SettingsSwitchRow(
                title = "Enable Peer Exchange",
                checked = uiState.btEnablePeerExchange,
                onCheckedChange = onUpdateBtEnablePeerExchange,
            )

            Spacer(modifier = Modifier.height(8.dp))

            var listenPort by remember(uiState.btListenPort) {
                mutableStateOf(uiState.btListenPort.toString())
            }
            OutlinedTextField(
                value = listenPort,
                onValueChange = {
                    listenPort = it
                    it.toIntOrNull()?.let { port -> onUpdateBtListenPort(port) }
                },
                label = { Text("Listen Port") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitchRow(
                title = "Auto-Update Tracker List",
                checked = uiState.enableAutoUpdateTracker,
                onCheckedChange = onUpdateEnableAutoUpdateTracker,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // UI Settings
            SettingsSectionHeader(title = "UI")
            SettingsDivider()

            Text(
                text = "Theme Mode",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.themeMode == mode,
                        onClick = { onUpdateThemeMode(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                },
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSwitchRow(
                title = "Notification Sound",
                checked = uiState.notificationSound,
                onCheckedChange = onUpdateNotificationSound,
            )

            SettingsSwitchRow(
                title = "Notification Vibrate",
                checked = uiState.notificationVibrate,
                onCheckedChange = onUpdateNotificationVibrate,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Advanced Settings
            SettingsSectionHeader(title = "Advanced")
            SettingsDivider()

            var userAgent by remember(uiState.userAgent) {
                mutableStateOf(uiState.userAgent)
            }
            OutlinedTextField(
                value = userAgent,
                onValueChange = {
                    userAgent = it
                    onUpdateUserAgent(it)
                },
                label = { Text("User-Agent (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            var rpcPort by remember(uiState.rpcPort) {
                mutableStateOf(uiState.rpcPort.toString())
            }
            OutlinedTextField(
                value = rpcPort,
                onValueChange = {
                    rpcPort = it
                    it.toIntOrNull()?.let { port -> onUpdateRpcPort(port) }
                },
                label = { Text("RPC Port") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsDivider() {
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
