package com.chat.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chat.app.data.Message
import com.chat.app.data.MediaType
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.appColors
import java.io.File

@Composable
fun MediaStorageScreen(
    mediaMessages: List<Message>,
    storageBreakdown: com.chat.app.data.MediaStorageBreakdown = com.chat.app.data.MediaStorageBreakdown(),
    onBack: () -> Unit,
    onDeleteMessage: (Message) -> Unit,
    onCleanOrphans: () -> Unit = {},
    onClearCategory: (String) -> Unit = {},
) {
    val colors = appColors
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) }

    val (mediaList, fileList) = remember(mediaMessages) {
        mediaMessages.partition { it.mediaType == MediaType.IMAGE || it.mediaType == MediaType.VIDEO }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Header
        Surface(
            color = colors.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.Back, contentDescription = "Back", tint = colors.txt)
                }

                Text(
                    text = "Storage & Media",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.txt
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Storage Overview Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Local Backend Storage",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.muted
                        )
                        Text(
                            formatFileSize(storageBreakdown.totalBytes),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.txt
                        )
                    }

                    if (storageBreakdown.orphanCount > 0) {
                        Button(
                            onClick = onCleanOrphans,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.card,
                                contentColor = colors.txt
                            ),
                            border = BorderStroke(1.dp, colors.divider),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clean Orphans (${storageBreakdown.orphanCount})", fontSize = 12.sp, color = colors.txt)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryChip(
                        label = "Photos/Videos",
                        size = storageBreakdown.imagesBytes + storageBreakdown.videosBytes,
                        modifier = Modifier.weight(1f)
                    )
                    CategoryChip(
                        label = "Files",
                        size = storageBreakdown.filesBytes,
                        modifier = Modifier.weight(1f)
                    )
                    CategoryChip(
                        label = "Avatars",
                        size = storageBreakdown.avatarsBytes,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .padding(4.dp)
        ) {
            val tabTitles = listOf("Photos & Videos", "Documents & Files")
            tabTitles.forEachIndexed { index, title ->
                val selected = activeTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.card else Color.Transparent)
                        .then(
                            if (selected) Modifier.border(1.dp, colors.divider, RoundedCornerShape(10.dp))
                            else Modifier
                        )
                        .clickable { activeTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (selected) colors.txt else colors.muted
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (activeTab == 0) {
            if (mediaList.isEmpty()) {
                EmptyMediaState(message = "No photos or videos yet")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = mediaList,
                        key = { it.id },
                        contentType = { "media_grid_item" }
                    ) { msg ->
                        val localPath = msg.localMediaUri
                        val file = if (!localPath.isNullOrEmpty()) {
                            File(localPath)
                        } else null

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.container)
                        ) {
                            if (file != null) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = "Media thumb",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { onDeleteMessage(msg) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(AppIcons.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        } else {
            if (fileList.isEmpty()) {
                EmptyMediaState(message = "No documents or files yet")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = fileList,
                        key = { it.id },
                        contentType = { "file_list_item" }
                    ) { msg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(AppIcons.Attach, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        msg.fileName ?: "File",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = colors.txt,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        formatFileSize(msg.fileSize),
                                        fontSize = 11.sp,
                                        color = colors.muted
                                    )
                                }
                                IconButton(onClick = { onDeleteMessage(msg) }) {
                                    Icon(AppIcons.Delete, contentDescription = "Delete", tint = colors.danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMediaState(message: String) {
    val colors = appColors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                AppIcons.Storage,
                contentDescription = null,
                tint = colors.muted.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(message, color = colors.muted, fontSize = 14.sp)
        }
    }
}

private val FILE_SIZE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")
private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
    return String.format(java.util.Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), FILE_SIZE_UNITS[digitGroups])
}

@Composable
private fun CategoryChip(
    label: String,
    size: Long,
    modifier: Modifier = Modifier
) {
    val colors = appColors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.container)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = colors.muted, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(formatFileSize(size), fontSize = 11.sp, color = colors.txt, fontWeight = FontWeight.Bold)
        }
    }
}
