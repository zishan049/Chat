package com.chat.app.media.presentation

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chat.app.data.local.media.MediaCategory
import com.chat.app.data.local.media.MediaItem
import com.chat.app.ui.components.GlassButton
import com.chat.app.ui.components.GlassCard
import com.chat.app.ui.components.GlassFilterChip
import com.chat.app.ui.components.GlassIconButton
import com.chat.app.ui.components.GlassSurface
import com.chat.app.ui.components.GlowingCubeLogo
import com.chat.app.ui.theme.AccentDestructive
import com.chat.app.ui.theme.AccentGreen
import com.chat.app.ui.theme.AccentWarning
import com.chat.app.ui.theme.AppBackground
import com.chat.app.ui.theme.AppGlassBorder
import com.chat.app.ui.theme.AppGlassBorderSubtle
import com.chat.app.ui.theme.AppGlassLow
import com.chat.app.ui.theme.AppGlassMedium
import com.chat.app.ui.theme.AppSurfaceElevated
import com.chat.app.ui.theme.AppTextMuted
import com.chat.app.ui.theme.AppTextPrimary
import com.chat.app.ui.theme.AppTextSecondary
import com.chat.app.ui.theme.AppTextTertiary
import com.chat.app.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MediaStorageScreen(
    onBack: () -> Unit,
    viewModel: MediaStorageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    androidx.activity.compose.BackHandler {
        if (state.previewItem != null) {
            viewModel.setPreviewItem(null)
        } else if (state.itemToDelete != null) {
            viewModel.requestDeleteItem(null)
        } else if (state.showClearConfirmation) {
            viewModel.showClearDialog(false)
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                        size = 40.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Media & Storage",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTextPrimary
                        )
                    )
                }

                if (state.mediaItems.isNotEmpty()) {
                    GlassIconButton(
                        icon = Icons.Outlined.CleaningServices,
                        onClick = { viewModel.showClearDialog(true) },
                        size = 40.dp,
                        contentDescription = "Clear Media"
                    )
                }
            }

            // Storage Overview Card
            StorageOverviewCard(
                totalBytes = state.storageBreakdown.totalBytes,
                imagesBytes = state.storageBreakdown.imagesBytes,
                videosBytes = state.storageBreakdown.videosBytes,
                audioBytes = state.storageBreakdown.audioBytes,
                filesBytes = state.storageBreakdown.filesBytes,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            // Category Filter Tabs
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MediaCategory.entries) { category ->
                    GlassFilterChip(
                        text = category.label,
                        isSelected = state.selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) }
                    )
                }
            }

            // Media Grid or List Content
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AppTextPrimary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            } else if (state.mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlowingCubeLogo(size = 64.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No media files found",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sent and received files in this category will appear here.",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = AppTextSecondary
                            )
                        )
                    }
                }
            } else {
                when (state.selectedCategory) {
                    MediaCategory.IMAGES, MediaCategory.VIDEOS -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(state.mediaItems, key = { it.path }) { item ->
                                VisualMediaGridItem(
                                    item = item,
                                    onClick = { viewModel.setPreviewItem(item) },
                                    onDelete = { viewModel.requestDeleteItem(item) }
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(state.mediaItems, key = { it.path }) { item ->
                                FileMediaListItem(
                                    item = item,
                                    onClick = { viewModel.setPreviewItem(item) },
                                    onDelete = { viewModel.requestDeleteItem(item) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full Screen Preview / Details Dialog
        state.previewItem?.let { previewItem ->
            MediaPreviewDialog(
                item = previewItem,
                onDismiss = { viewModel.setPreviewItem(null) },
                onDelete = {
                    viewModel.setPreviewItem(null)
                    viewModel.requestDeleteItem(previewItem)
                },
                onShare = {
                    shareMediaFile(context, previewItem)
                }
            )
        }

        // Delete Confirmation Dialog
        state.itemToDelete?.let { item ->
            DeleteConfirmationDialog(
                title = "Delete File?",
                message = "Are you sure you want to delete '${item.name}' (${formatBytes(item.sizeBytes)})? This cannot be undone.",
                onConfirm = viewModel::confirmDeleteItem,
                onDismiss = { viewModel.requestDeleteItem(null) }
            )
        }

        // Clear All Confirmation Dialog
        if (state.showClearConfirmation) {
            DeleteConfirmationDialog(
                title = "Clear ${state.selectedCategory.label}?",
                message = "This will permanently delete all stored media files in this category to free up storage.",
                onConfirm = viewModel::confirmClearCategory,
                onDismiss = { viewModel.showClearDialog(false) }
            )
        }
    }
}

@Composable
private fun StorageOverviewCard(
    totalBytes: Long,
    imagesBytes: Long,
    videosBytes: Long,
    audioBytes: Long,
    filesBytes: Long,
    modifier: Modifier = Modifier
) {
    GlassCard(
        onClick = {},
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Space Used",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppTextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatBytes(totalBytes),
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Segmented Storage Bar
            val total = if (totalBytes > 0) totalBytes.toFloat() else 1f
            val imgRatio = imagesBytes / total
            val vidRatio = videosBytes / total
            val audRatio = audioBytes / total
            val fileRatio = filesBytes / total

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppGlassMedium)
            ) {
                if (imgRatio > 0) {
                    Box(
                        modifier = Modifier
                            .weight(imgRatio.coerceAtLeast(0.01f))
                            .fillMaxSize()
                            .background(Color(0xFF38BDF8))
                    )
                }
                if (vidRatio > 0) {
                    Box(
                        modifier = Modifier
                            .weight(vidRatio.coerceAtLeast(0.01f))
                            .fillMaxSize()
                            .background(AccentWarning)
                    )
                }
                if (audRatio > 0) {
                    Box(
                        modifier = Modifier
                            .weight(audRatio.coerceAtLeast(0.01f))
                            .fillMaxSize()
                            .background(AccentGreen)
                    )
                }
                if (fileRatio > 0) {
                    Box(
                        modifier = Modifier
                            .weight(fileRatio.coerceAtLeast(0.01f))
                            .fillMaxSize()
                            .background(Color(0xFFA855F7))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Breakdown legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StorageLegendItem(color = Color(0xFF38BDF8), label = "Images", size = formatBytes(imagesBytes))
                StorageLegendItem(color = AccentWarning, label = "Videos", size = formatBytes(videosBytes))
                StorageLegendItem(color = AccentGreen, label = "Audio", size = formatBytes(audioBytes))
                StorageLegendItem(color = Color(0xFFA855F7), label = "Files", size = formatBytes(filesBytes))
            }
        }
    }
}

@Composable
private fun StorageLegendItem(
    color: Color,
    label: String,
    size: String
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = TextStyle(fontSize = 11.sp, color = AppTextSecondary)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = size,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTextPrimary,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}

@Composable
private fun VisualMediaGridItem(
    item: MediaItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    GlassSurface(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.file)
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay badges
        if (item.category == MediaCategory.VIDEOS) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play Video",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Size badge at bottom right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = formatBytes(item.sizeBytes),
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

@Composable
private fun FileMediaListItem(
    item: MediaItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(item.lastModified))
    val icon = when (item.category) {
        MediaCategory.AUDIO -> Icons.Filled.Audiotrack
        MediaCategory.VIDEOS -> Icons.Filled.Movie
        MediaCategory.IMAGES -> Icons.Filled.Image
        else -> Icons.Filled.Description
    }

    GlassCard(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppTextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatBytes(item.sizeBytes),
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "•",
                        style = TextStyle(fontSize = 11.sp, color = AppTextMuted)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dateStr,
                        style = TextStyle(fontSize = 11.sp, color = AppTextSecondary)
                    )
                }
            }

            GlassIconButton(
                icon = Icons.Outlined.Delete,
                onClick = onDelete,
                size = 36.dp,
                iconSize = 18.dp,
                tint = AccentDestructive
            )
        }
    }
}

@Composable
private fun MediaPreviewDialog(
    item: MediaItem,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = SimpleDateFormat("MMMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(item.lastModified))

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(24.dp),
            backgroundColor = AppTheme.colors.surface,
            borderColor = AppGlassBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "File Details",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppTextPrimary)
                    )
                    GlassIconButton(
                        icon = Icons.Filled.Close,
                        onClick = onDismiss,
                        size = 32.dp,
                        iconSize = 16.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Preview Thumbnail if image
                if (item.category == MediaCategory.IMAGES || item.category == MediaCategory.VIDEOS) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.file)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // File metadata details
                GlassCard(
                    onClick = {},
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        DetailRow(label = "File Name", value = item.name)
                        DetailRow(label = "Size", value = formatBytes(item.sizeBytes))
                        DetailRow(label = "Category", value = item.category.label)
                        DetailRow(label = "Saved", value = dateStr)
                        DetailRow(label = "Location", value = item.path, isMonospace = true)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = "Share",
                        icon = Icons.Outlined.Share,
                        isPrimary = false,
                        onClick = onShare,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Delete",
                        icon = Icons.Outlined.Delete,
                        isPrimary = true,
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isMonospace: Boolean = false
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = TextStyle(fontSize = 11.sp, color = AppTextSecondary))
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = value,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AppTextPrimary,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(24.dp),
            backgroundColor = AppTheme.colors.surface,
            borderColor = AppGlassBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AccentDestructive.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = AccentDestructive,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppTextPrimary)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    style = TextStyle(fontSize = 13.sp, color = AppTextSecondary),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = "Cancel",
                        isPrimary = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Delete",
                        isPrimary = true,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

private fun shareMediaFile(context: android.content.Context, item: MediaItem) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            item.file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${item.name}"))
    } catch (e: Exception) {
        // Fallback
    }
}
