package com.chat.app.ui.components

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.chat.app.data.MediaType
import com.chat.app.ui.theme.appColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mediaType: MediaType,
    val mimeType: String,
    val durationMs: Long = 0L,
    val dateModified: Long = 0L,
    val size: Long = 0L
)

enum class MediaFilterCategory(val label: String) {
    RECENTS("Recents"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    ALL_FILES("Documents & Files")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaAttachmentSelector(
    onDismiss: () -> Unit,
    onMediaSelected: (Uri, MediaType, String?) -> Unit
) {
    val colors = appColors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var isHdEnabled by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(MediaFilterCategory.RECENTS) }
    var isCategoryDropdownOpen by remember { mutableStateOf(false) }

    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasPermission by remember {
        mutableStateOf(checkMediaPermissions(context))
    }

    // Permission launcher for Media / Storage
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        hasPermission = granted
    }

    // System File / Document Picker Launcher (FAB)
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val type = determineMediaType(context, it)
            onMediaSelected(it, type, null)
            onDismiss()
        }
    }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val tempUri = saveBitmapToTempUri(context, bitmap)
            if (tempUri != null) {
                onMediaSelected(tempUri, MediaType.IMAGE, "photo_${System.currentTimeMillis()}.jpg")
                onDismiss()
            }
        }
    }

    // Query device media on launch or when permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            mediaList = loadRecentMedia(context)
            isLoading = false
        } else {
            isLoading = false
            requestMediaPermissions(permissionLauncher)
        }
    }

    // Filter media by category
    val filteredMedia = remember(mediaList, selectedCategory) {
        when (selectedCategory) {
            MediaFilterCategory.RECENTS -> mediaList
            MediaFilterCategory.PHOTOS -> mediaList.filter { it.mediaType == MediaType.IMAGE }
            MediaFilterCategory.VIDEOS -> mediaList.filter { it.mediaType == MediaType.VIDEO }
            MediaFilterCategory.ALL_FILES -> mediaList
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.muted.copy(alpha = 0.35f))
            )
        },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Top Header Bar ───────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Close 'X' Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = "Close",
                            tint = colors.txt,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Centered "Recents ▼" Dropdown Title
                    Box(contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { isCategoryDropdownOpen = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = selectedCategory.label,
                                color = colors.txt,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = AppIcons.ArrowDropDown,
                                contentDescription = "Select Category",
                                tint = colors.txt,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isCategoryDropdownOpen,
                            onDismissRequest = { isCategoryDropdownOpen = false },
                            modifier = Modifier.background(colors.card)
                        ) {
                            MediaFilterCategory.values().forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = cat.label,
                                            color = if (selectedCategory == cat) colors.txt else colors.muted,
                                            fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedCategory = cat
                                        isCategoryDropdownOpen = false
                                        if (cat == MediaFilterCategory.ALL_FILES) {
                                            docPickerLauncher.launch("*/*")
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // HD Quality Toggle Badge Button (Monochrome Grayscale)
                    Surface(
                        onClick = { isHdEnabled = !isHdEnabled },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isHdEnabled) colors.card else colors.container,
                        border = BorderStroke(
                            1.dp,
                            if (isHdEnabled) colors.txt.copy(alpha = 0.6f) else colors.divider
                        ),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "HD",
                                color = if (isHdEnabled) colors.txt else colors.muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isHdEnabled) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = "HD Active",
                                    tint = colors.txt,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ── Media Content Grid / Permission View ─────────────────────────
                if (!hasPermission) {
                    // Permission Banner & Fallback Actions
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.PhotoLibrary,
                                contentDescription = null,
                                tint = colors.muted,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "Media Access Needed",
                                color = colors.txt,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Allow access to your device media to browse and send recent photos and videos directly.",
                                color = colors.muted,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = { requestMediaPermissions(permissionLauncher) },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.card),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Grant Permission", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { docPickerLauncher.launch("*/*") },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.divider)
                            ) {
                                Text("Browse System Files", color = colors.txt)
                            }
                        }
                    }
                } else if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colors.txt, modifier = Modifier.size(36.dp))
                    }
                } else if (filteredMedia.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "No media found in ${selectedCategory.label}",
                                color = colors.muted,
                                fontSize = 15.sp
                            )
                            Button(
                                onClick = { docPickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.container),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Choose from Documents", color = colors.txt)
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp) // Space for bottom FAB
                    ) {
                        items(
                            items = filteredMedia,
                            key = { it.id }
                        ) { item ->
                            MediaGridTile(
                                item = item,
                                onClick = {
                                    onMediaSelected(item.uri, item.mediaType, item.displayName)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }

            // ── Floating Action Button (System Files / Gallery) ──────────────
            Surface(
                onClick = { docPickerLauncher.launch("*/*") },
                shape = RoundedCornerShape(16.dp),
                color = colors.card,
                border = BorderStroke(1.dp, colors.divider),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.Layers,
                        contentDescription = "All Files & Storage",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaGridTile(
    item: MediaItem,
    onClick: () -> Unit
) {
    val colors = appColors
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(colors.card)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video Duration Indicator Overlay (e.g. 0:10)
        if (item.mediaType == MediaType.VIDEO) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Video,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formatDuration(item.durationMs),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers: Media Querying, Permissions & Utilities
// ─────────────────────────────────────────────────────────────────────────────

private fun checkMediaPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val imagesGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        val videoGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
        imagesGranted || videoGranted
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requestMediaPermissions(
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        launcher.launch(
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        )
    } else {
        launcher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
    }
}

private suspend fun loadRecentMedia(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
    val items = mutableListOf<MediaItem>()
    val contentResolver = context.contentResolver

    // 1. Query Images
    try {
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE
        )
        val imageSortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            null,
            null,
            imageSortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            var count = 0
            while (cursor.moveToNext() && count < 100) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "image_$id.jpg"
                val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                val date = cursor.getLong(dateCol)
                val size = cursor.getLong(sizeCol)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                items.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        displayName = name,
                        mediaType = MediaType.IMAGE,
                        mimeType = mime,
                        dateModified = date,
                        size = size
                    )
                )
                count++
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // 2. Query Videos
    try {
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE
        )
        val videoSortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null,
            null,
            videoSortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            var count = 0
            while (cursor.moveToNext() && count < 50) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "video_$id.mp4"
                val mime = cursor.getString(mimeCol) ?: "video/mp4"
                val dur = cursor.getLong(durCol)
                val date = cursor.getLong(dateCol)
                val size = cursor.getLong(sizeCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                items.add(
                    MediaItem(
                        id = id + 1_000_000_000L, // Unique ID offset
                        uri = uri,
                        displayName = name,
                        mediaType = MediaType.VIDEO,
                        mimeType = mime,
                        durationMs = dur,
                        dateModified = date,
                        size = size
                    )
                )
                count++
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    items.sortedByDescending { it.dateModified }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

private fun determineMediaType(context: Context, uri: Uri): MediaType {
    val mimeType = context.contentResolver.getType(uri) ?: ""
    return when {
        mimeType.startsWith("video/") -> MediaType.VIDEO
        mimeType.startsWith("audio/") -> MediaType.AUDIO
        mimeType.startsWith("image/") -> MediaType.IMAGE
        else -> MediaType.FILE
    }
}

private fun saveBitmapToTempUri(context: Context, bitmap: android.graphics.Bitmap): Uri? {
    return try {
        val file = java.io.File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
