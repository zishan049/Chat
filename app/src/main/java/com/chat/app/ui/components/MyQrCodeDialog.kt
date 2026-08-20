package com.chat.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chat.app.domain.model.Identity
import com.chat.app.pairing.presentation.QrCodeGenerator
import com.chat.app.ui.theme.AccentCyan
import com.chat.app.ui.theme.AccentGreen
import com.chat.app.ui.theme.AppGlassBorderBright
import com.chat.app.ui.theme.AppGlassBorderSubtle
import com.chat.app.ui.theme.AppGlassLow
import com.chat.app.ui.theme.AppTextPrimary
import com.chat.app.ui.theme.AppTextSecondary
import com.chat.app.ui.theme.AppTextTertiary
import com.chat.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MyQrCodeDialog(
    identity: Identity?,
    activePort: Int = 47832,
    onDismiss: () -> Unit
) {
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var secondsRemaining by remember { mutableIntStateOf(45) }
    var isCopied by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(identity) {
        if (identity == null) return@LaunchedEffect

        suspend fun updateQr() {
            val localIp = withContext(Dispatchers.IO) {
                try {
                    val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
                    var foundIp: String? = null
                    for (intf in interfaces) {
                        if (intf.isLoopback || !intf.isUp) continue
                        val addrs = java.util.Collections.list(intf.inetAddresses)
                        for (addr in addrs) {
                            if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                                val host = addr.hostAddress
                                if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                                    foundIp = host
                                    break
                                }
                            }
                        }
                        if (foundIp != null) break
                    }
                    foundIp
                } catch (_: Exception) {
                    null
                }
            }

            val payload = com.chat.app.pairing.domain.model.QrPayload(
                version = 1,
                id = identity.id,
                displayName = identity.displayName,
                publicKeyBase64 = identity.publicKeyBase64,
                fingerprint = identity.fingerprint,
                lanIp = localIp,
                port = activePort,
                timestamp = System.currentTimeMillis()
            )
            val qrString = payload.toJson()
            val bmp = withContext(Dispatchers.Default) {
                QrCodeGenerator.generateQrBitmap(qrString, size = 512)
            }
            qrBitmap = bmp
        }

        updateQr()

        while (isActive) {
            delay(1000L)
            if (secondsRemaining > 1) {
                secondsRemaining--
            } else {
                secondsRemaining = 45
                updateQr()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(26.dp),
            backgroundColor = AppTheme.colors.surface,
            borderColor = AppGlassBorderBright,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppGlassLow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QrCode2,
                                contentDescription = null,
                                tint = AppTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "My QR Code",
                            style = TextStyle(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTextPrimary
                            )
                        )
                    }

                    GlassIconButton(
                        icon = Icons.Filled.Close,
                        onClick = onDismiss,
                        size = 32.dp,
                        iconSize = 16.dp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Identity pill
                if (identity != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppGlassLow)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        UserAvatar(
                            name = identity.displayName,
                            avatarUri = identity.avatarUri,
                            size = 32.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = identity.displayName,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTextPrimary
                                )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AccentGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Zero-Knowledge Enclave",
                                    style = TextStyle(fontSize = 10.sp, color = AppTextSecondary)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // QR Code Frame
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "My Pairing QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Auto-refresh countdown indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppGlassLow)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Auto-refreshes in ${secondsRemaining}s",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppTextSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fingerprint Card (Click to Copy)
                if (identity != null) {
                    GlassCard(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(identity.fingerprint))
                            isCopied = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = AppGlassLow,
                        borderColor = AppGlassBorderSubtle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isCopied) "Fingerprint Copied!" else "Fingerprint",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isCopied) AccentGreen else AppTextSecondary
                                    )
                                )
                                Text(
                                    text = identity.fingerprint,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = AppTextPrimary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy",
                                tint = AppTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                Text(
                    text = "Scan this code with another device to establish an end-to-end encrypted connection.",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = AppTextTertiary,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                GlassButton(
                    text = "Done",
                    isPrimary = true,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
