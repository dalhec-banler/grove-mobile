package io.nativeplanet.grove.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nativeplanet.grove.data.ConnectionState
import io.nativeplanet.grove.domain.model.GroveFile
import io.nativeplanet.grove.domain.model.GroveView
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onFileClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showConnectDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionIndicator(uiState.connectionState)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Grove")
                            if (uiState.shipName != null) {
                                Text(
                                    text = uiState.shipName ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (uiState.pendingUploads > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("${uiState.pendingUploads}")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (uiState.isConnected) {
                FloatingActionButton(
                    onClick = { /* TODO: Upload picker */ }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Upload")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!uiState.isConnected) {
                ConnectBanner(onConnect = { showConnectDialog = true })
            } else {
                ConnectionIndicator(isConnected = true)
            }

            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (uiState.views.isNotEmpty()) {
                ViewChips(
                    views = uiState.views,
                    selectedView = uiState.selectedView,
                    onViewSelect = { viewModel.selectView(it) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.files.isEmpty()) {
                EmptyState(
                    isConnected = uiState.isConnected,
                    onConnect = { showConnectDialog = true }
                )
            } else {
                FileList(
                    files = uiState.files,
                    onFileClick = onFileClick,
                    onStarClick = { viewModel.toggleStar(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showConnectDialog) {
        ConnectDialog(
            onDismiss = { showConnectDialog = false },
            onConnect = { code ->
                viewModel.connect(code)
                showConnectDialog = false
            }
        )
    }

    uiState.error?.let { error ->
        Snackbar(
            action = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("Dismiss")
                }
            }
        ) {
            Text(error)
        }
    }
}

@Composable
private fun ConnectBanner(onConnect: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .clickable { onConnect() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Not connected",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Tap to connect to your Urbit ship",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ConnectionIndicator(state: ConnectionState) {
    val (color, pulse) = when (state) {
        ConnectionState.CONNECTED -> Color(0xFF4CAF50) to false
        ConnectionState.CONNECTING -> Color(0xFFFF9800) to true
        ConnectionState.RECONNECTING -> Color(0xFFFF9800) to true
        ConnectionState.DISCONNECTED -> Color(0xFF9E9E9E) to false
        ConnectionState.ERROR -> Color(0xFFEF5350) to false
    }

    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search files...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun ViewChips(
    views: List<GroveView>,
    selectedView: String?,
    onViewSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedView == null,
                onClick = { onViewSelect(null) },
                label = { Text("All") }
            )
        }
        items(views) { view ->
            FilterChip(
                selected = selectedView == view.name,
                onClick = { onViewSelect(view.name) },
                label = { Text(view.name) }
            )
        }
    }
}

@Composable
private fun FileList(
    files: List<GroveFile>,
    onFileClick: (String) -> Unit,
    onStarClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files, key = { it.id }) { file ->
            FileItem(
                file = file,
                onClick = { onFileClick(file.id) },
                onStarClick = { onStarClick(file.id) }
            )
        }
    }
}

@Composable
private fun FileItem(
    file: GroveFile,
    onClick: () -> Unit,
    onStarClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileIcon(fileMark = file.fileMark)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (file.tags.isNotEmpty()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = file.tags.take(2).joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            IconButton(onClick = onStarClick) {
                Icon(
                    imageVector = if (file.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (file.starred) "Unstar" else "Star",
                    tint = if (file.starred) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FileIcon(fileMark: String) {
    val icon = when (fileMark.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp" -> Icons.Default.Image
        "pdf" -> Icons.Default.PictureAsPdf
        "mp3", "wav", "ogg" -> Icons.Default.AudioFile
        "mp4", "mov", "webm" -> Icons.Default.VideoFile
        "txt", "md" -> Icons.Default.Description
        "zip", "tar", "gz" -> Icons.Default.FolderZip
        else -> Icons.Default.InsertDriveFile
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun EmptyState(
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Default.Folder else Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isConnected) "No files yet" else "Not connected",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isConnected) {
                "Upload files using the + button or share from other apps"
            } else {
                "Connect to your Urbit ship to access your files"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!isConnected) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onConnect) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun ConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to Ship") },
        text = {
            Column {
                Text("Enter your ship's +code to connect")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("+code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(code) },
                enabled = code.isNotBlank()
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
