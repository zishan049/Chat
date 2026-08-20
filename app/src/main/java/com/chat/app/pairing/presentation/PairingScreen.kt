package com.chat.app.pairing.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chat.app.domain.model.Contact
import com.chat.app.pairing.domain.model.PairingResult
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.*

@Composable
fun PairingScreen(
    onNavigateBack: () -> Unit,
    onPairingSuccess: () -> Unit,
    initialTab: Int = 1,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(initialTab) {
        viewModel.selectTab(initialTab)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(state.selectedTab, hasCameraPermission) {
        if (state.selectedTab == 1 && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    androidx.activity.compose.BackHandler(onBack = onNavigateBack)

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
                    text = "Pair Contact",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.size(38.dp))
            }

            // Glass Segmented Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassWhiteUltraLow)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlassFilterChip(
                    text = "My QR Code",
                    isSelected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    modifier = Modifier.weight(1f)
                )
                GlassFilterChip(
                    text = "Scan QR Code",
                    isSelected = state.selectedTab == 1,
                    onClick = {
                        viewModel.selectTab(1)
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.errorMessage != null) {
                GlassSurface(
                    backgroundColor = AccentDestructive.copy(alpha = 0.15f),
                    borderColor = AccentDestructive.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentDestructive, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = state.errorMessage ?: "",
                            style = TextStyle(fontSize = 12.sp, color = TextPrimary)
                        )
                    }
                }
            }

            when (state.selectedTab) {
                0 -> MyQrCodeTab(state = state)
                1 -> ScanQrCodeTab(
                    hasCameraPermission = hasCameraPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onQrScanned = viewModel::onQrScanned
                )
            }
        }
    }

    // Modal 1: Confirmation for New Contact
    state.verifiedContactToConfirm?.let { contact ->
        ConfirmNewContactDialog(
            contact = contact,
            onConfirm = {
                viewModel.confirmAddContact(contact, onComplete = onPairingSuccess)
            },
            onDismiss = viewModel::dismissDialogs
        )
    }

    // Modal 2: Key Mismatch Warning (TOFU)
    state.keyMismatchWarning?.let { warning ->
        KeyMismatchWarningDialog(
            warning = warning,
            onAcceptNewKey = {
                viewModel.confirmKeyOverride(warning, onComplete = onPairingSuccess)
            },
            onReject = viewModel::dismissDialogs
        )
    }
}

@Composable
private fun MyQrCodeTab(state: PairingUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.isLoadingQr || state.selfQrBitmap == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
        } else {
            GlassSurface(
                shape = RoundedCornerShape(28.dp),
                backgroundColor = GlassWhiteLow,
                borderColor = GlassBorder,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = state.selfQrBitmap.asImageBitmap(),
                            contentDescription = "My Pairing QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = state.selfIdentity?.displayName ?: "My Device",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Fingerprint: ${state.selfIdentity?.fingerprint?.take(16)}…",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = AppTextSecondary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppGlassLow)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Auto-rotates every 45s",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppTextSecondary
                            )
                        )
                    }
                }
            }

            Text(
                text = "Show this QR code to a friend to connect securely.",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = AppTextSecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun ScanQrCodeTab(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    if (!hasCameraPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera Permission Required",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Camera access is needed to scan peer QR codes for instant cryptographic pairing.",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
            GlassButton(text = "Grant Permission", isPrimary = true, onClick = onRequestPermission)
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CameraQrScanner(
                onQrCodeDetected = onQrScanned,
                modifier = Modifier.fillMaxSize()
            )

            // Glass Target Overlay Frame
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
            )
        }
    }
}

@Composable
private fun ConfirmNewContactDialog(
    contact: Contact,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = AppTheme.colors.surface,
            borderColor = AppGlassBorderBright
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(
                        name = contact.displayName,
                        avatarUri = contact.avatarUri,
                        isOnline = true,
                        size = 48.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Add Contact",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppTextPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Verified cryptographic signature for ${contact.displayName}.",
                    style = TextStyle(fontSize = 14.sp, color = AppTextSecondary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Fingerprint: ${contact.fingerprint}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = AppTextTertiary
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = AppTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassButton(
                        text = "Add Contact",
                        isPrimary = true,
                        onClick = onConfirm,
                        modifier = Modifier.width(130.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyMismatchWarningDialog(
    warning: PairingResult.KeyMismatchWarning,
    onAcceptNewKey: () -> Unit,
    onReject: () -> Unit
) {
    Dialog(onDismissRequest = onReject) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            backgroundColor = AppTheme.colors.surface,
            borderColor = AccentDestructive.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Security Alert",
                        tint = AccentDestructive,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Security Warning: Key Changed!",
                        style = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentDestructive
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "The identity key for '${warning.existingContact.displayName}' does NOT match the previously verified key.\n\nThis could indicate that the contact reinstalled the app or someone is attempting an interception attack.",
                    style = TextStyle(fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onReject) {
                        Text("Reject", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassButton(
                        text = "Accept Key",
                        isPrimary = true,
                        onClick = onAcceptNewKey,
                        modifier = Modifier.width(130.dp)
                    )
                }
            }
        }
    }
}

