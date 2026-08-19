package com.chat.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chat.app.ui.theme.appColors
import com.chat.app.utils.ScannedProfileData
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScannedProfileModal(
    scannedData: ScannedProfileData,
    onStartChat: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = appColors
    val context = LocalContext.current

    // Generate formatted cryptographic safety fingerprint from public key or contact ID
    val safetyFingerprint = remember(scannedData.publicKey, scannedData.id) {
        val sourceKey = scannedData.publicKey ?: scannedData.id
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(sourceKey.toByteArray(Charsets.UTF_8))
            val hex = hash.joinToString("") { "%02X".format(it) }
            // Format into 4 chunks of 4 hex chars: "XXXX • XXXX • XXXX • XXXX"
            listOf(
                hex.substring(0, 4),
                hex.substring(4, 8),
                hex.substring(8, 12),
                hex.substring(12, 16)
            ).joinToString(" • ")
        } catch (_: Exception) {
            scannedData.id.take(16).uppercase().chunked(4).joinToString(" • ")
        }
    }

    val timeStr = remember(scannedData.timestamp) {
        if (scannedData.timestamp != null && scannedData.timestamp > 0) {
            SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault()).format(Date(scannedData.timestamp))
        } else "Just now"
    }

    val copyToClipboard: (String, String) -> Unit = { text, label ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.verticalGradient(
                                listOf(
                                    colors.accent.copy(alpha = 0.6f),
                                    colors.container.copy(alpha = 0.2f),
                                    colors.positive.copy(alpha = 0.3f)
                                )
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .shadow(16.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Header Bar ───────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.accent.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "✨ Contact Discovered",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .background(colors.card, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = colors.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // ── Avatar with Glowing Multi-tone Ring ───────────────────
                    Box(
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .border(
                                    BorderStroke(
                                        3.dp,
                                        Brush.sweepGradient(
                                            listOf(
                                                colors.accent,
                                                colors.positive,
                                                colors.accentDark,
                                                colors.accent
                                            )
                                        )
                                    ),
                                    CircleShape
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarCircle(
                                name = scannedData.name,
                                avatarUri = scannedData.avatarUri,
                                size = 92.dp
                            )
                        }

                        // Verified or Security Badge attached to Avatar
                        if (scannedData.isVerified) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(colors.positive)
                                    .border(2.dp, colors.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Verified",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // ── Display Name & Age ────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = scannedData.name.ifBlank { "Unknown User" },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.txt,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (scannedData.age != null && scannedData.age > 0) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.card
                            ) {
                                Text(
                                    text = "${scannedData.age} yrs",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.muted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // ── Clickable Contact ID Chip ────────────────────────────
                    val shortenedId = if (scannedData.id.length > 18) {
                        "${scannedData.id.take(8)}...${scannedData.id.takeLast(6)}"
                    } else scannedData.id

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.card.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, colors.divider),
                        modifier = Modifier.clickable {
                            copyToClipboard(scannedData.id, "Contact ID")
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ID: @$shortenedId",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.muted
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy ID",
                                tint = colors.muted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // ── Bio / Status Quotation Box ───────────────────────────
                    if (!scannedData.description.isNullOrBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.card)
                                .border(1.dp, colors.divider.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "“${scannedData.description}”",
                                fontSize = 13.sp,
                                color = colors.txt.copy(alpha = 0.9f),
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // ── Cryptographic Security & Verification Card ────────────
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (scannedData.isVerified) colors.positive.copy(alpha = 0.12f)
                                else if (!scannedData.publicKey.isNullOrBlank()) colors.accent.copy(alpha = 0.12f)
                                else colors.card,
                        border = BorderStroke(
                            1.dp,
                            if (scannedData.isVerified) colors.positive.copy(alpha = 0.3f)
                            else if (!scannedData.publicKey.isNullOrBlank()) colors.accent.copy(alpha = 0.3f)
                            else colors.divider
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (scannedData.isVerified) Icons.Default.Security
                                                  else if (!scannedData.publicKey.isNullOrBlank()) Icons.Default.VpnKey
                                                  else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (scannedData.isVerified) colors.positive
                                           else if (!scannedData.publicKey.isNullOrBlank()) colors.accent
                                           else colors.muted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (scannedData.isVerified) "Cryptographically Verified (ECDSA P-256)"
                                           else if (!scannedData.publicKey.isNullOrBlank()) "E2EE Mutual Key Handshake Ready"
                                           else "Direct Contact Payload",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (scannedData.isVerified) colors.positive
                                           else if (!scannedData.publicKey.isNullOrBlank()) colors.accent
                                           else colors.txt
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = if (scannedData.isVerified) "Mutual ECDH shared secret derived with zero MITM risk."
                                       else if (!scannedData.publicKey.isNullOrBlank()) "AES-256-GCM symmetric session encryption ready."
                                       else "Contact identity stored securely in your local address book.",
                                fontSize = 11.sp,
                                color = colors.muted
                            )

                            // Key safety fingerprint
                            if (!scannedData.publicKey.isNullOrBlank() || scannedData.isVerified) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.surface.copy(alpha = 0.6f),
                                    border = BorderStroke(0.8.dp, colors.divider),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            copyToClipboard(scannedData.publicKey ?: safetyFingerprint, "Safety Key")
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Safety: $safetyFingerprint",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.txt.copy(alpha = 0.8f)
                                        )
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy Fingerprint",
                                            tint = colors.muted,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── P2P Direct Connectivity & Device Info ─────────────────
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.card,
                        border = BorderStroke(1.dp, colors.divider.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Direct IP & Port Row (if available)
                            if (!scannedData.ip.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = colors.positive,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("P2P Direct Network", fontSize = 11.sp, color = colors.muted)
                                    }
                                    Text(
                                        text = "${scannedData.ip}:${scannedData.port ?: 8888}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = colors.txt
                                    )
                                }
                            }

                            // Device Info Row
                            if (!scannedData.deviceInfo.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = colors.muted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Device Model", fontSize = 11.sp, color = colors.muted)
                                    }
                                    Text(
                                        text = scannedData.deviceInfo,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.txt
                                    )
                                }
                            }

                            // Scanned Timestamp Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = colors.positive,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Added to Contacts", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.positive)
                                }
                                Text(
                                    text = timeStr,
                                    fontSize = 11.sp,
                                    color = colors.muted
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // ── Primary & Secondary Action Buttons ────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.divider),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.txt)
                        ) {
                            Text("Dismiss", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }

                        Button(
                            onClick = {
                                onStartChat(scannedData.id)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.card,
                                contentColor = colors.txt
                            ),
                            border = BorderStroke(1.dp, colors.divider),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(Icons.Default.Message, contentDescription = null, tint = colors.txt, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start Chat", fontWeight = FontWeight.Bold, color = colors.txt, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
