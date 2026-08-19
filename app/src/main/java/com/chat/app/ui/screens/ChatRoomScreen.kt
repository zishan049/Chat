package com.chat.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.chat.app.data.MediaType
import com.chat.app.data.Message
import com.chat.app.data.MessageStatus
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.appColors
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.ui.layout.ContentScale
import com.chat.app.utils.VoiceRecorder
import com.chat.app.utils.AudioPlayerHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

private val SentBubbleShape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
private val RecvBubbleShape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
private val ImageAttachmentShape = RoundedCornerShape(12.dp)
private val InputBarShape = RoundedCornerShape(26.dp)

@Composable
fun ChatRoomScreen(
    chatName: String,
    chatAvatarUri: String?,
    messages: List<Message>,
    selfId: String,
    isBlocked: Boolean,
    isPeerTyping: Boolean = false,
    isPeerOnline: Boolean = false,
    isSameWifi: Boolean = false,
    peerLastSeenAt: Long? = null,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onSendMedia: (Uri, MediaType, String?) -> Unit,
    onDeleteMessage: (Message) -> Unit,
    onRetryMessage: (Message) -> Unit = {},
    onEditMessage: (Message, String) -> Unit = { _, _ -> },
    onViewProfile: () -> Unit,
    onUnblock: () -> Unit,
) {
    val colors = appColors
    val context = LocalContext.current
    var activeImageViewerUri by remember { mutableStateOf<String?>(null) }
    var longClickedMessage by remember { mutableStateOf<Message?>(null) }
    var messageToEdit by remember { mutableStateOf<Message?>(null) }
    val listState = rememberLazyListState()

    var hasScrolledInitially by remember { mutableStateOf(false) }

    // Instant position on initial load, smooth scroll only for live new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (!hasScrolledInitially) {
                listState.scrollToItem(messages.size - 1)
                hasScrolledInitially = true
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val isNearBottom = lastVisibleIndex >= (messages.size - 4)
                val isLastMsgMine = messages.lastOrNull()?.isMine == true
                if (isNearBottom || isLastMsgMine) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }

    LaunchedEffect(isPeerTyping) {
        if (isPeerTyping && messages.isNotEmpty()) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisibleIndex >= (messages.size - 4)) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Stable callbacks to ensure MessageBubble is completely skippable
    val onBubbleImageClick: (String) -> Unit = remember { { uri -> activeImageViewerUri = uri } }
    val onBubbleLongClick: (Message) -> Unit = remember { { msg -> longClickedMessage = msg } }
    val onBubbleRetryClick: (Message) -> Unit = remember(onRetryMessage) { { msg -> onRetryMessage(msg) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Telegram Top Header ───────────────────────────────────────────
            ChatTopHeader(
                chatName = chatName,
                chatAvatarUri = chatAvatarUri,
                isPeerOnline = isPeerOnline,
                isSameWifi = isSameWifi,
                isPeerTyping = isPeerTyping,
                peerLastSeenAt = peerLastSeenAt,
                onBack = onBack,
                onViewProfile = onViewProfile
            )

            // ── Message List ──────────────────────────────────────────────────
            if (messages.isEmpty() && !isPeerTyping) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages here yet...\nSend a message to start chatting!",
                        color = colors.muted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.id },
                        contentType = { it.mediaType }
                    ) { msg ->
                        MessageBubble(
                            msg = msg,
                            onImageClick = onBubbleImageClick,
                            onLongClick = onBubbleLongClick,
                            onRetryClick = onBubbleRetryClick
                        )
                    }
                }
            }

            // Peer typing indicator bar
            AnimatedVisibility(visible = isPeerTyping) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$chatName is typing...",
                        fontSize = 12.sp,
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Bottom Input Bar (Isolated State) ─────────────────────────────
            ChatBottomInputBar(
                isBlocked = isBlocked,
                onSendText = onSendText,
                onSendMedia = onSendMedia,
                onUnblock = onUnblock
            )
        }

        // Full Screen Image Viewer
        if (activeImageViewerUri != null) {
            Dialog(
                onDismissRequest = { activeImageViewerUri = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { activeImageViewerUri = null },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = activeImageViewerUri,
                        contentDescription = "Image preview",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ── Message Actions Context Dialog (Delete / Edit / Copy / Retry) ─────
        val selectedMsg = longClickedMessage
        if (selectedMsg != null) {
            AlertDialog(
                onDismissRequest = { longClickedMessage = null },
                containerColor = colors.card,
                title = {
                    Text(
                        text = if (selectedMsg.isMine) "Sent Message Options" else "Message Options",
                        fontWeight = FontWeight.Bold,
                        color = colors.txt,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (selectedMsg.text.isNotBlank()) {
                            Text(
                                text = "\"${selectedMsg.text.take(80)}${if (selectedMsg.text.length > 80) "..." else ""}\"",
                                fontSize = 13.sp,
                                color = colors.muted
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                        // Option 1: Copy Text
                        if (selectedMsg.text.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Message Text", selectedMsg.text)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "Message copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                    longClickedMessage = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(AppIcons.Copy, contentDescription = "Copy", tint = colors.txt, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("Copy Text", color = colors.txt, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                            }
                        }

                        // Option 2: Edit Message
                        if (selectedMsg.isMine && selectedMsg.mediaType == MediaType.NONE && selectedMsg.text.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    messageToEdit = selectedMsg
                                    longClickedMessage = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(AppIcons.Edit, contentDescription = "Edit", tint = colors.txt, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("Edit Message", color = colors.txt, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                            }
                        }

                        // Option 3: Retry
                        if (selectedMsg.status == MessageStatus.FAILED) {
                            TextButton(
                                onClick = {
                                    onRetryMessage(selectedMsg)
                                    longClickedMessage = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(AppIcons.Send, contentDescription = "Retry", tint = colors.warning, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("Retry Sending", color = colors.warning, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                            }
                        }

                        // Option 4: Delete Message
                        TextButton(
                            onClick = {
                                onDeleteMessage(selectedMsg)
                                longClickedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(AppIcons.Delete, contentDescription = "Delete", tint = colors.danger, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Delete Message", color = colors.danger, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { longClickedMessage = null }) {
                        Text("Cancel", color = colors.muted)
                    }
                }
            )
        }

        // ── Edit Message Dialog ────────────────────────────────────────────────
        val editingMsg = messageToEdit
        if (editingMsg != null) {
            var editedText by remember(editingMsg) { mutableStateOf(editingMsg.text) }
            AlertDialog(
                onDismissRequest = { messageToEdit = null },
                containerColor = colors.card,
                title = { Text("Edit Message", fontWeight = FontWeight.Bold, color = colors.txt) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editedText,
                            onValueChange = { editedText = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.divider,
                                focusedTextColor = colors.txt,
                                unfocusedTextColor = colors.txt
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (editedText.isNotBlank() && editedText.trim() != editingMsg.text) {
                                onEditMessage(editingMsg, editedText.trim())
                            }
                            messageToEdit = null
                        }
                    ) {
                        Text("Save", color = colors.accent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { messageToEdit = null }) {
                        Text("Cancel", color = colors.muted)
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatTopHeader(
    chatName: String,
    chatAvatarUri: String?,
    isPeerOnline: Boolean,
    isSameWifi: Boolean = false,
    isPeerTyping: Boolean,
    peerLastSeenAt: Long?,
    onBack: () -> Unit,
    onViewProfile: () -> Unit
) {
    val colors = appColors
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onViewProfile() }
                    .padding(vertical = 2.dp)
            ) {
                AvatarCircle(
                    name = chatName,
                    avatarUri = chatAvatarUri,
                    size = 40.dp,
                    showOnlineStatus = true,
                    isOnline = isPeerOnline
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = chatName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.txt
                    )
                    val subtitleText = when {
                        isPeerTyping -> "typing..."
                        isPeerOnline -> "online"
                        peerLastSeenAt != null && peerLastSeenAt > 0 -> "last seen ${formatLastSeenTime(peerLastSeenAt)}"
                        else -> "offline"
                    }
                    val subtitleColor = when {
                        isPeerTyping -> colors.accent
                        isPeerOnline -> colors.positive
                        else -> colors.muted
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPeerOnline && !isPeerTyping) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(colors.positive)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = subtitleText,
                            fontSize = 12.sp,
                            color = subtitleColor
                        )
                        if (isSameWifi && isPeerOnline && !isPeerTyping) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = AppIcons.Wifi,
                                contentDescription = "Same Wi-Fi Network",
                                tint = colors.accent,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onViewProfile) {
                Icon(AppIcons.More, contentDescription = "More", tint = colors.muted)
            }
        }
    }
}

@Composable
private fun VoiceMessagePlayer(
    msg: Message,
    isMine: Boolean
) {
    val context = LocalContext.current
    val colors = appColors
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgressMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf(0) }

    val filePath = msg.localMediaUri ?: ""

    DisposableEffect(msg.id) {
        onDispose {
            if (AudioPlayerHelper.isPlaying(msg.id)) {
                AudioPlayerHelper.stop()
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (AudioPlayerHelper.isPlaying(msg.id)) {
                    AudioPlayerHelper.stop()
                    isPlaying = false
                } else {
                    if (filePath.isNotBlank()) {
                        isPlaying = true
                        AudioPlayerHelper.play(
                            context = context,
                            messageId = msg.id,
                            mediaPathOrUri = filePath,
                            onProgress = { cur, tot ->
                                currentProgressMs = cur
                                totalDurationMs = tot
                                isPlaying = true
                            },
                            onComplete = {
                                isPlaying = false
                                currentProgressMs = 0
                            }
                        )
                    }
                }
            },
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isMine) colors.card else colors.container)
                .border(1.dp, colors.divider, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = colors.txt,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            val progressFraction = if (totalDurationMs > 0) {
                (currentProgressMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = colors.txt,
                trackColor = colors.divider
            )

            Spacer(Modifier.height(4.dp))

            val displayMs = if (isPlaying) currentProgressMs else totalDurationMs
            val displaySec = (displayMs / 1000).coerceAtLeast(0)
            val durationText = String.format(Locale.getDefault(), "%d:%02d", displaySec / 60, displaySec % 60)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Voice Note",
                    fontSize = 11.sp,
                    color = colors.muted,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (displaySec > 0) durationText else "Audio",
                    fontSize = 11.sp,
                    color = colors.muted
                )
            }
        }
    }
}

@Composable
private fun ChatBottomInputBar(
    isBlocked: Boolean,
    onSendText: (String) -> Unit,
    onSendMedia: (Uri, MediaType, String?) -> Unit,
    onUnblock: () -> Unit
) {
    val colors = appColors
    val context = LocalContext.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var inputText by remember { mutableStateOf("") }
    var showAttachmentSelector by remember { mutableStateOf(false) }
    var showAudioRecordingModal by remember { mutableStateOf(false) }

    // Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showAudioRecordingModal = true
        } else {
            Toast.makeText(context, "Microphone permission is required to record audio", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val tempUri = saveBitmapToTempUri(context, bitmap)
            if (tempUri != null) {
                onSendMedia(tempUri, MediaType.IMAGE, "camera_${System.currentTimeMillis()}.jpg")
            }
        }
    }

    if (isBlocked) {
        Surface(
            color = colors.danger.copy(alpha = 0.15f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUnblock() }
                .padding(16.dp)
        ) {
            Text(
                text = "Contact is blocked. Tap to unblock.",
                color = colors.danger,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Surface(
            color = colors.bg,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Pill Container (Input, Attachment, Camera) ─────────────
                Surface(
                    shape = InputBarShape,
                    color = colors.container,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Text Field (starts directly from the left)
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = colors.txt,
                                fontSize = 16.sp
                            ),
                            cursorBrush = SolidColor(colors.txt),
                            maxLines = 5,
                            decorationBox = { inner ->
                                if (inputText.isEmpty()) {
                                    Text("Message", color = colors.muted, fontSize = 16.sp)
                                }
                                inner()
                            }
                        )

                        // Attachment (Paperclip) Icon Button
                        IconButton(
                            onClick = {
                                keyboardController?.hide()
                                showAttachmentSelector = true
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Attach,
                                contentDescription = "Attach",
                                tint = colors.muted,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer(rotationZ = -45f)
                            )
                        }

                        // Camera Icon Button
                        IconButton(
                            onClick = {
                                keyboardController?.hide()
                                cameraLauncher.launch(null as Void?)
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Camera,
                                contentDescription = "Camera",
                                tint = colors.muted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                // ── Separated Circular Action Button (Mic / Send - Grayscale) ─
                Surface(
                    onClick = {
                        val trimmed = inputText.trim()
                        if (trimmed.isNotBlank()) {
                            onSendText(trimmed)
                            inputText = ""
                        } else {
                            // Check microphone permission and open recording modal
                            val hasAudioPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasAudioPermission) {
                                showAudioRecordingModal = true
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    shape = CircleShape,
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.divider),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = inputText.isNotBlank(),
                            label = "SendOrMic"
                        ) { hasText ->
                            if (hasText) {
                                Icon(
                                    imageVector = AppIcons.Send,
                                    contentDescription = "Send",
                                    tint = colors.txt,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = AppIcons.Mic,
                                    contentDescription = "Record Audio Note",
                                    tint = colors.txt,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Attachment Bottom Sheet Modal ─────────────────────────────────────────
    if (showAttachmentSelector) {
        MediaAttachmentSelector(
            onDismiss = { showAttachmentSelector = false },
            onMediaSelected = { uri, type, fileName ->
                onSendMedia(uri, type, fileName)
            }
        )
    }

    // ── Audio Recording Modal ────────────────────────────────────────────────
    if (showAudioRecordingModal) {
        AudioRecordingModal(
            onDismiss = { showAudioRecordingModal = false },
            onSendAudio = { audioUri, fileName ->
                onSendMedia(audioUri, MediaType.AUDIO, fileName)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: Message,
    onImageClick: (String) -> Unit,
    onLongClick: (Message) -> Unit,
    onRetryClick: (Message) -> Unit
) {
    val colors = appColors
    val context = LocalContext.current
    val isMine = msg.isMine
    val bubbleColor = if (isMine) colors.bubbleSent else colors.bubbleRecv
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isMine) SentBubbleShape else RecvBubbleShape

    val textColor = if (isMine) Color.White else colors.txt
    val timestampColor = if (isMine) Color.White.copy(alpha = 0.75f) else colors.muted

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
                onLongClick = { onLongClick(msg) }
            ),
        contentAlignment = alignment
    ) {
        Surface(
            shape = shape,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // ── Image Attachment (Only Photo preview, no redundant text label) ──
                if (msg.mediaType == MediaType.IMAGE && (!msg.localMediaUri.isNullOrBlank() || !msg.thumbnailUri.isNullOrBlank())) {
                    val fullImageUri = msg.localMediaUri ?: msg.thumbnailUri
                    val previewImageModel = msg.thumbnailUri ?: msg.localMediaUri
                    AsyncImage(
                        model = previewImageModel,
                        contentDescription = "Attached photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(ImageAttachmentShape)
                            .clickable { fullImageUri?.let { onImageClick(it) } },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // ── Video Attachment (Thumbnail + Play Icon overlay) ─────────
                if (msg.mediaType == MediaType.VIDEO && !msg.localMediaUri.isNullOrBlank()) {
                    val videoUri = msg.localMediaUri
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(ImageAttachmentShape)
                            .background(Color.Black)
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(videoUri), "video/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = videoUri,
                            contentDescription = "Attached video",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Play,
                                contentDescription = "Play Video",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // ── Voice / Audio Note Attachment Player ─────────────────────
                if (msg.mediaType == MediaType.AUDIO && !msg.localMediaUri.isNullOrBlank()) {
                    VoiceMessagePlayer(msg = msg, isMine = isMine)
                    Spacer(Modifier.height(4.dp))
                }

                // ── File / Document Attachment ───────────────────────────────
                if (msg.mediaType == MediaType.FILE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppIcons.Document,
                            contentDescription = "Document",
                            tint = colors.txt,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = msg.fileName ?: "Document",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // ── Message Text (Omit redundant "Photo" / "Video" / "Audio" labels) ──
                val shouldShowText = when {
                    msg.mediaType == MediaType.NONE -> msg.text.isNotBlank()
                    else -> msg.text.isNotBlank() &&
                            msg.text != "📷 Photo" &&
                            msg.text != "🎥 Video" &&
                            msg.text != "🎵 Audio" &&
                            !msg.text.startsWith("📎")
                }

                if (shouldShowText) {
                    Text(
                        text = msg.text,
                        fontSize = 15.sp,
                        color = textColor
                    )
                    Spacer(Modifier.height(2.dp))
                }

                val formattedTime = remember(msg.timestamp) { formatMessageTime(msg.timestamp) }
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = timestampColor
                    )

                    if (isMine) {
                        Spacer(Modifier.width(4.dp))
                        when (msg.status) {
                            MessageStatus.SENDING -> {
                                Icon(
                                    imageVector = AppIcons.Clock,
                                    contentDescription = "Sending",
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            MessageStatus.SENT -> {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = "Sent",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageStatus.DELIVERED -> {
                                Icon(
                                    imageVector = AppIcons.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageStatus.READ -> {
                                Icon(
                                    imageVector = AppIcons.DoneAll,
                                    contentDescription = "Read",
                                    tint = Color(0xFF40C4FF), // Telegram cyan
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageStatus.FAILED -> {
                                Icon(
                                    imageVector = AppIcons.ErrorOutline,
                                    contentDescription = "Failed to send - Tap to retry",
                                    tint = colors.danger,
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clickable { onRetryClick(msg) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun determineMediaType(context: android.content.Context, uri: Uri): MediaType {
    val mimeType = context.contentResolver.getType(uri) ?: ""
    return when {
        mimeType.startsWith("video/") -> MediaType.VIDEO
        mimeType.startsWith("audio/") -> MediaType.AUDIO
        mimeType.startsWith("image/") -> MediaType.IMAGE
        else -> MediaType.FILE
    }
}

private val messageTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

private fun formatMessageTime(timestamp: Long): String {
    return try {
        messageTimeFormatter.format(Instant.ofEpochMilli(timestamp))
    } catch (_: Exception) {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
}

private val lastSeenHourMinuteFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val lastSeenMonthDayFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

private fun formatLastSeenTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    return when {
        diffMs < 60_000L -> "just now"
        diffMs < 3600_000L -> "${diffMs / 60_000L}m ago"
        diffMs < 86400_000L -> synchronized(lastSeenHourMinuteFormat) { "at " + lastSeenHourMinuteFormat.format(Date(timestamp)) }
        else -> synchronized(lastSeenMonthDayFormat) { lastSeenMonthDayFormat.format(Date(timestamp)) }
    }
}

private fun saveBitmapToTempUri(context: android.content.Context, bitmap: android.graphics.Bitmap): Uri? {
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
