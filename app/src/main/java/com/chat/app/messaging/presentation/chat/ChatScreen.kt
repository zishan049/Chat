package com.chat.app.messaging.presentation.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chat.app.domain.model.Message
import com.chat.app.domain.model.MessageStatus
import com.chat.app.media.presentation.AudioRecordingModal
import com.chat.app.media.presentation.MediaAttachmentSelector
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var showAttachmentSelector by remember { mutableStateOf(false) }
    var showAudioModal by remember { mutableStateOf(false) }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.onAction(ChatAction.OnTextChanged("[Image: ${it.lastPathSegment ?: "photo.jpg"}]"))
            viewModel.onAction(ChatAction.SendMessage)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.onAction(ChatAction.OnTextChanged("[Document: ${it.lastPathSegment ?: "file"}]"))
            viewModel.onAction(ChatAction.SendMessage)
        }
    }

    androidx.activity.compose.BackHandler {
        if (showAudioModal) {
            showAudioModal = false
        } else if (showAttachmentSelector) {
            showAttachmentSelector = false
        } else {
            onNavigateBack()
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
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Glass Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onNavigateBack,
                        size = 38.dp,
                        iconSize = 18.dp,
                        contentDescription = "Back"
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    UserAvatar(
                        name = state.contact?.displayName ?: "Chat",
                        avatarUri = state.contact?.avatarUri,
                        isOnline = state.isOnline,
                        size = 40.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = state.contact?.effectiveName ?: "Chat",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTextPrimary
                            )
                        )
                        Text(
                            text = if (state.isOnline) "Online" else "Offline",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = if (state.isOnline) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (state.isOnline) AccentGreen else AppTextTertiary
                            )
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(
                        icon = Icons.Outlined.Call,
                        onClick = {},
                        size = 38.dp,
                        iconSize = 18.dp,
                        contentDescription = "Call"
                    )
                    GlassIconButton(
                        icon = Icons.Outlined.MoreVert,
                        onClick = {},
                        size = 38.dp,
                        iconSize = 18.dp,
                        contentDescription = "More"
                    )
                }
            }

            // Security Notice Banner
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = AppGlassLow,
                borderColor = AppGlassBorderSubtle
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = AppTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Messages are end-to-end encrypted.\nOnly you and ${state.contact?.displayName ?: "contact"} can read them.",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = AppTextSecondary,
                            lineHeight = 15.sp
                        )
                    )
                }
            }

            // Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    GlassMessageBubble(
                        message = message,
                        onRetry = { viewModel.onAction(ChatAction.RetryMessage(message.id)) }
                    )
                }
            }

            // Bottom Glass Message Composer
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(30.dp),
                backgroundColor = AppTheme.colors.surface,
                borderColor = AppGlassBorderBright
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment button (+)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AppSurfaceElevated)
                            .clickable { showAttachmentSelector = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach",
                            tint = AppTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Text Input
                    Box(modifier = Modifier.weight(1f)) {
                        if (state.textInput.isEmpty()) {
                            Text(
                                text = "Type a message...",
                                style = TextStyle(fontSize = 15.sp, color = AppTextTertiary)
                            )
                        }
                        BasicTextField(
                            value = state.textInput,
                            onValueChange = { viewModel.onAction(ChatAction.OnTextChanged(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 15.sp, color = AppTextPrimary),
                            cursorBrush = SolidColor(AppTextPrimary),
                            maxLines = 4
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send or Mic button
                    if (state.textInput.isBlank()) {
                        GlassIconButton(
                            icon = Icons.Outlined.Mic,
                            onClick = { showAudioModal = true },
                            size = 38.dp,
                            iconSize = 18.dp,
                            backgroundColor = AppGlassLow,
                            contentDescription = "Voice note"
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.textPrimary)
                                .clickable { viewModel.onAction(ChatAction.SendMessage) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = AppTheme.colors.background,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAttachmentSelector) {
        MediaAttachmentSelector(
            onDismiss = { showAttachmentSelector = false },
            onSelectGallery = { galleryPickerLauncher.launch("image/*") },
            onSelectDocument = { documentPickerLauncher.launch("*/*") },
            onSelectAudio = { showAudioModal = true }
        )
    }

    if (showAudioModal) {
        AudioRecordingModal(
            onDismiss = { showAudioModal = false },
            onSendRecording = { duration ->
                viewModel.onAction(ChatAction.OnTextChanged("🎙 Voice message (${duration}s)"))
                viewModel.onAction(ChatAction.SendMessage)
            }
        )
    }
}

@Composable
private fun GlassMessageBubble(
    message: Message,
    onRetry: () -> Unit
) {
    val isMine = message.isOutgoing
    val alignment = if (isMine) Alignment.End else Alignment.Start
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }

    val isVoiceMessage = message.text.contains("Voice message", ignoreCase = true) || message.text.contains("Voice note", ignoreCase = true)
    val isImageAttachment = message.text.startsWith("[Image:", ignoreCase = true)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        GlassSurface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isMine) 20.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 20.dp
            ),
            backgroundColor = if (isMine) Color(0x32FFFFFF) else Color(0x12FFFFFF),
            borderColor = if (isMine) GlassBorderBright else GlassBorderSubtle,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (isVoiceMessage) {
                    // Glass Voice Message Player Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isMine) Color.White else GlassWhiteHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = if (isMine) BackgroundBlack else TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Waveform visualizer bars
                        val waveformColor = if (isMine) AppTheme.colors.background else AppTextSecondary
                        Canvas(modifier = Modifier.size(width = 110.dp, height = 24.dp)) {
                            val barWidth = 3.dp.toPx()
                            val gap = 3.dp.toPx()
                            val heights = listOf(0.4f, 0.7f, 0.3f, 0.9f, 0.6f, 1f, 0.8f, 0.5f, 0.7f, 0.3f, 0.6f, 0.4f)
                            heights.forEachIndexed { index, ratio ->
                                val x = index * (barWidth + gap)
                                val barH = size.height * ratio
                                val y = (size.height - barH) / 2f
                                drawRoundRect(
                                    color = waveformColor,
                                    topLeft = Offset(x, y),
                                    size = androidx.compose.ui.geometry.Size(barWidth, barH),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "00:08",
                            style = TextStyle(fontSize = 11.sp, color = TextSecondary)
                        )
                    }
                } else if (isImageAttachment) {
                    // Image attachment card
                    GlassSurface(
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = Color.Black.copy(alpha = 0.4f),
                        borderColor = GlassBorderSubtle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "Image",
                                tint = TextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    Text(
                        text = message.text,
                        style = TextStyle(
                            fontSize = 15.sp,
                            color = TextPrimary,
                            lineHeight = 21.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = TextTertiary
                        )
                    )

                    if (isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        MessageStatusIcon(status = message.status, onRetry = onRetry)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageStatusIcon(
    status: MessageStatus,
    onRetry: () -> Unit
) {
    when (status) {
        MessageStatus.QUEUED -> {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Queued",
                tint = TextTertiary,
                modifier = Modifier.size(12.dp)
            )
        }
        MessageStatus.SENDING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                color = AppTextSecondary,
                strokeWidth = 1.5.dp
            )
        }
        MessageStatus.SENT -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sent",
                tint = AppTextSecondary,
                modifier = Modifier.size(12.dp)
            )
        }
        MessageStatus.DELIVERED -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                tint = AppTextSecondary,
                modifier = Modifier.size(13.dp)
            )
        }
        MessageStatus.READ -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Read",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(13.dp)
            )
        }
        MessageStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Failed. Tap to retry",
                tint = AccentDestructive,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onRetry)
            )
        }
    }
}

