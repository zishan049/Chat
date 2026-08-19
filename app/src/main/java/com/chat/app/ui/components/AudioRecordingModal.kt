package com.chat.app.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat.app.ui.theme.appColors
import com.chat.app.utils.VoiceRecorder
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecordingModal(
    onDismiss: () -> Unit,
    onSendAudio: (Uri, String) -> Unit
) {
    val colors = appColors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val voiceRecorder = remember { VoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var durationSeconds by remember { mutableIntStateOf(0) }
    var amplitudes by remember { mutableStateOf<List<Float>>(emptyList()) }

    // Start recording immediately when modal opens
    LaunchedEffect(Unit) {
        val started = voiceRecorder.start()
        if (started) {
            isRecording = true
            isPaused = false
            durationSeconds = 0
        }
    }

    // Periodic amplitude sampling & duration timer (ticks every 100ms for smooth waveform)
    LaunchedEffect(isRecording, isPaused) {
        if (isRecording) {
            var tickCount = 0
            while (isRecording) {
                if (!isPaused) {
                    val maxAmp = voiceRecorder.getMaxAmplitude()
                    val normalized = (maxAmp / 32767f).coerceIn(0.12f, 1f)
                    amplitudes = (amplitudes + normalized).takeLast(24)

                    tickCount++
                    if (tickCount % 10 == 0) {
                        durationSeconds++
                    }
                }
                delay(100L)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.cancel()
        }
    }

    // Pulsing animation for active recording
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording && !isPaused) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPaused) 1f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    ModalBottomSheet(
        onDismissRequest = {
            voiceRecorder.cancel()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = colors.bg,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.muted.copy(alpha = 0.35f))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Header ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Audio Recording",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.txt
                )

                IconButton(
                    onClick = {
                        voiceRecorder.cancel()
                        onDismiss()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "Cancel & Close",
                        tint = colors.muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Center Pulsing Mic Emblem ────────────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(130.dp)
            ) {
                // Outer glowing halo
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (isPaused) colors.card.copy(alpha = 0.3f)
                            else colors.card.copy(alpha = 0.6f)
                        )
                )

                // Inner graphite container
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(colors.card)
                        .border(
                            1.5.dp,
                            if (isPaused) Color(0xFFF59E0B) else colors.divider,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Mic,
                        contentDescription = "Microphone",
                        tint = if (isPaused) Color(0xFFF59E0B) else colors.txt,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Recording Duration Timer ─────────────────────────────────────
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            Text(
                text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = colors.txt,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(6.dp))

            // ── Status Indicator (Recording / Paused) ─────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPaused) Color(0xFFF59E0B)
                            else Color(0xFFEF4444).copy(alpha = dotAlpha)
                        )
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isPaused) "PAUSED" else "RECORDING",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPaused) Color(0xFFF59E0B) else colors.muted,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Dynamic Multi-Bar Waveform ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.container)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bars = if (amplitudes.isEmpty()) {
                    List(24) { 0.2f }
                } else {
                    amplitudes.takeLast(24)
                }
                bars.forEach { amp ->
                    val heightFraction = amp.coerceIn(0.18f, 1f)
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height((34 * heightFraction).dp)
                            .clip(CircleShape)
                            .background(
                                if (isPaused) colors.muted.copy(alpha = 0.4f)
                                else colors.txt
                            )
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Controls: Delete / Pause-Resume / Stop & Send ────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Delete / Discard Button
                Surface(
                    onClick = {
                        voiceRecorder.cancel()
                        onDismiss()
                    },
                    shape = CircleShape,
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.4f)),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppIcons.Delete,
                            contentDescription = "Discard Recording",
                            tint = colors.danger,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 2. Pause / Resume Button
                Surface(
                    onClick = {
                        if (isPaused) {
                            voiceRecorder.resume()
                            isPaused = false
                        } else {
                            voiceRecorder.pause()
                            isPaused = true
                        }
                    },
                    shape = CircleShape,
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPaused) AppIcons.Play else AppIcons.Pause,
                            contentDescription = if (isPaused) "Resume Recording" else "Pause Recording",
                            tint = colors.txt,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 3. Stop & Send Button
                Surface(
                    onClick = {
                        val result = voiceRecorder.stopAndGetUri()
                        if (result != null) {
                            onSendAudio(result.first, "voice_${System.currentTimeMillis()}.m4a")
                        }
                        onDismiss()
                    },
                    shape = CircleShape,
                    color = colors.txt,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppIcons.Send,
                            contentDescription = "Stop & Send Audio",
                            tint = colors.bg,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
