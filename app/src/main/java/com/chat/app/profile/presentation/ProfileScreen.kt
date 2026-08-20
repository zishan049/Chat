package com.chat.app.profile.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.*

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.onAvatarUriChanged(uri?.toString())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
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
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onNavigateBack,
                    size = 38.dp,
                    iconSize = 18.dp,
                    contentDescription = "Back"
                )

                Text(
                    text = "My Identity",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                if (onNavigateToSettings != null) {
                    GlassIconButton(
                        icon = Icons.Outlined.Settings,
                        onClick = onNavigateToSettings,
                        size = 38.dp,
                        iconSize = 18.dp,
                        contentDescription = "Settings"
                    )
                } else {
                    Spacer(modifier = Modifier.size(38.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                state.identity?.let { identity ->
                    // Center 3D Glass Hero Card
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        backgroundColor = GlassWhiteLow,
                        borderColor = GlassBorder
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Large 3D Glowing Cube / Avatar
                            if (!identity.avatarUri.isNullOrBlank()) {
                                UserAvatar(
                                    name = identity.displayName,
                                    avatarUri = identity.avatarUri,
                                    size = 120.dp
                                )
                            } else {
                                GlowingCubeLogo(size = 130.dp)
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = identity.displayName,
                                style = TextStyle(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    letterSpacing = (-0.3).sp
                                )
                            )

                            if (identity.age != null || !identity.bio.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (identity.age != null) {
                                        Text(
                                            text = "Age: ${identity.age}",
                                            style = TextStyle(fontSize = 13.sp, color = TextSecondary)
                                        )
                                        if (!identity.bio.isNullOrBlank()) {
                                            Text(
                                                text = " • ",
                                                style = TextStyle(fontSize = 13.sp, color = TextTertiary)
                                            )
                                        }
                                    }
                                    if (!identity.bio.isNullOrBlank()) {
                                        Text(
                                            text = identity.bio,
                                            style = TextStyle(fontSize = 13.sp, color = TextSecondary),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Peer ID pill with copy action
                            GlassCard(
                                onClick = { copyToClipboard(context, "Peer ID", identity.id) },
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = GlassWhiteMedium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Peer ID",
                                            style = TextStyle(fontSize = 10.sp, color = TextTertiary)
                                        )
                                        Text(
                                            text = identity.id.take(16) + "…",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextPrimary
                                            )
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy Peer ID",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Fingerprint block
                            Text(
                                text = "Fingerprint",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = TextTertiary,
                                    fontWeight = FontWeight.Medium
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            val fp = identity.fingerprint.replace(" ", "").uppercase()
                            val chunkedFp = fp.chunked(2).chunked(4).map { it.joinToString(" ") }
                            val line1 = chunkedFp.take(4).joinToString("  ")
                            val line2 = chunkedFp.drop(4).take(4).joinToString("  ")

                            Text(
                                text = if (line2.isNotEmpty()) "$line1\n$line2" else line1.ifBlank { identity.fingerprint },
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Action Buttons
                    GlassButton(
                        text = "Show QR Code",
                        icon = Icons.Outlined.QrCodeScanner,
                        isPrimary = true,
                        onClick = viewModel::openQrDialog
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassButton(
                        text = "Share Invite Link",
                        icon = Icons.Outlined.Share,
                        isPrimary = false,
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Connect with me on Chat:\nPeer ID: ${identity.id}\nFingerprint: ${identity.fingerprint}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Invite"))
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassButton(
                        text = "Edit Profile",
                        icon = Icons.Outlined.Edit,
                        isPrimary = false,
                        onClick = viewModel::openEditDialog
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Security Details (Tucked away)
                    GlassCard(
                        onClick = viewModel::toggleSecurityDetails,
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = GlassWhiteUltraLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Security Details",
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextPrimary
                                        )
                                    )
                                }
                                Icon(
                                    imageVector = if (state.showSecurityDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedVisibility(visible = state.showSecurityDetails) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    HorizontalDivider(color = GlassBorderSubtle)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "Key Algorithm: EC secp256r1",
                                        style = TextStyle(fontSize = 12.sp, color = TextSecondary)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Public Key: ${identity.publicKeyBase64.take(32)}…",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextTertiary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { copyToClipboard(context, "Public Key", identity.publicKeyBase64) },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Copy Full Key", color = TextPrimary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }

        // Modal 1: Show QR Code Dialog
        if (state.showQrDialog) {
            Dialog(onDismissRequest = viewModel::closeQrDialog) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    backgroundColor = AppTheme.colors.surface,
                    borderColor = AppGlassBorderBright
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "My QR Code",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTextPrimary
                                )
                            )
                            GlassIconButton(
                                icon = Icons.Default.Close,
                                onClick = viewModel::closeQrDialog,
                                size = 32.dp,
                                iconSize = 16.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // QR Code Canvas / Image
                        if (state.qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = state.qrBitmap!!.asImageBitmap(),
                                    contentDescription = "My QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            CircularProgressIndicator(color = AppTextPrimary, modifier = Modifier.size(36.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Scan this code to connect instantly.",
                            style = TextStyle(fontSize = 13.sp, color = AppTextSecondary, textAlign = TextAlign.Center)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        GlassButton(
                            text = "Done",
                            isPrimary = true,
                            onClick = viewModel::closeQrDialog
                        )
                    }
                }
            }
        }

        // Modal 2: Edit Profile Dialog
        if (state.showEditDialog) {
            Dialog(onDismissRequest = viewModel::closeEditDialog) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    backgroundColor = AppTheme.colors.surface,
                    borderColor = AppGlassBorderBright
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Edit Profile",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            GlassIconButton(
                                icon = Icons.Default.Close,
                                onClick = viewModel::closeEditDialog,
                                size = 32.dp,
                                iconSize = 16.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Avatar Picker
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            UserAvatar(
                                name = state.displayNameInput,
                                avatarUri = state.avatarUriInput,
                                size = 80.dp
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CameraAlt,
                                    contentDescription = "Change Avatar",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        GlassTextField(
                            value = state.displayNameInput,
                            onValueChange = viewModel::onDisplayNameChanged,
                            label = "Display Name *",
                            placeholder = "Your name",
                            singleLine = true,
                            errorText = state.errorMessage,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        GlassTextField(
                            value = state.ageInput,
                            onValueChange = viewModel::onAgeChanged,
                            label = "Age",
                            placeholder = "e.g. 24",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        GlassTextField(
                            value = state.bioInput,
                            onValueChange = viewModel::onBioChanged,
                            label = "Description / Bio",
                            placeholder = "About yourself...",
                            singleLine = false,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { viewModel.saveProfile() })
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        GlassButton(
                            text = "Save Changes",
                            isPrimary = true,
                            onClick = { viewModel.saveProfile() },
                            isLoading = state.isSaving,
                            enabled = state.displayNameInput.isNotBlank()
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}

