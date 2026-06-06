package com.motrix.android.feature.newtask

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskRoute(
    initialUrl: String? = null,
    onDismiss: () -> Unit,
    viewModel: NewTaskViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            viewModel.onUrlChanged(initialUrl)
        }
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        NewTaskContent(
            uiState = uiState,
            onUrlChanged = viewModel::onUrlChanged,
            onDownloadDirChanged = viewModel::onDownloadDirChanged,
            onFilenameChanged = viewModel::onFilenameChanged,
            onSplitChanged = viewModel::onSplitChanged,
            onUserAgentChanged = viewModel::onUserAgentChanged,
            onRefererChanged = viewModel::onRefererChanged,
            onHeadersChanged = viewModel::onHeadersChanged,
            onAdvancedExpanded = viewModel::onAdvancedExpanded,
            onSubmit = viewModel::onSubmit,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun NewTaskContent(
    uiState: NewTaskUiState,
    onUrlChanged: (String) -> Unit,
    onDownloadDirChanged: (String) -> Unit,
    onFilenameChanged: (String) -> Unit,
    onSplitChanged: (String) -> Unit,
    onUserAgentChanged: (String) -> Unit,
    onRefererChanged: (String) -> Unit,
    onHeadersChanged: (String) -> Unit,
    onAdvancedExpanded: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var dirPath by remember(uiState.downloadDir) { mutableStateOf(uiState.downloadDir) }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            dirPath = it.path ?: it.toString()
            onDownloadDirChanged(dirPath)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(
            text = "New Download",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // URL input
        OutlinedTextField(
            value = uiState.url,
            onValueChange = onUrlChanged,
            label = { Text("URL / Magnet Link") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 3,
            isError = uiState.error != null,
            trailingIcon = {
                IconButton(onClick = {
                    val clipText = clipboardManager.getText()?.text
                    if (!clipText.isNullOrBlank()) {
                        onUrlChanged(clipText)
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                    )
                }
            },
            supportingText = {
                if (uiState.error != null) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                } else if (uiState.urlType != UrlType.UNKNOWN && uiState.url.isNotBlank()) {
                    Text(
                        when (uiState.urlType) {
                            UrlType.HTTP -> "HTTP/FTP download detected"
                            UrlType.MAGNET -> "Magnet link detected"
                            UrlType.UNKNOWN -> ""
                        },
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Download directory
        OutlinedTextField(
            value = dirPath,
            onValueChange = {
                dirPath = it
                onDownloadDirChanged(it)
            },
            label = { Text("Save to") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    dirPickerLauncher.launch(null)
                }) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Browse",
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Optional filename
        OutlinedTextField(
            value = uiState.filename,
            onValueChange = onFilenameChanged,
            label = { Text("Filename (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Advanced options
        TextButton(
            onClick = { onAdvancedExpanded(!uiState.isAdvancedExpanded) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Advanced Options")
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.isAdvancedExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 380f,
                ),
            ),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 380f,
                ),
            ),
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.split,
                    onValueChange = onSplitChanged,
                    label = { Text("Split (connections per server)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.userAgent,
                    onValueChange = onUserAgentChanged,
                    label = { Text("User-Agent (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.referer,
                    onValueChange = onRefererChanged,
                    label = { Text("Referer (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.headers,
                    onValueChange = onHeadersChanged,
                    label = { Text("Headers (one per line, Key: Value)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Submit button
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !uiState.isSubmitting && uiState.url.isNotBlank(),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            }
            Text("Submit Download")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
