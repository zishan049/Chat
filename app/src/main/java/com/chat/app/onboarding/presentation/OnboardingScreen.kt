package com.chat.app.onboarding.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.*

@Composable
fun OnboardingScreen(
    onNavigateToMain: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.onAvatarUriChanged(uri?.toString())
    }

    LaunchedEffect(state.hasExistingIdentity, state.isCheckingExisting) {
        if (!state.isCheckingExisting && state.hasExistingIdentity) {
            onNavigateToMain()
        }
    }

    if (state.isCheckingExisting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundBlack),
            contentAlignment = Alignment.Center
        ) {
            GlowingCubeLogo(size = 80.dp)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Main Screen Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar with subtle Settings Gear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (onNavigateToSettings != null) {
                    GlassIconButton(
                        icon = Icons.Outlined.Settings,
                        onClick = onNavigateToSettings,
                        size = 40.dp,
                        iconSize = 18.dp,
                        contentDescription = "Settings"
                    )
                }
            }

            // Center Visual & Welcome Copy
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                GlowingCubeLogo(size = 140.dp)

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Welcome to",
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                )

                Text(
                    text = "Chat",
                    style = TextStyle(
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Private. Secure. Peer-to-Peer.\nNo accounts. No tracking.\nJust real conversations.",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 3 Page Dots Indicator
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(GlassWhiteHigh)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(GlassWhiteHigh)
                    )
                }
            }

            // Bottom Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassButton(
                    text = "Create Identity",
                    isPrimary = true,
                    onClick = viewModel::openCreateSheet
                )

                Spacer(modifier = Modifier.height(12.dp))

                GlassButton(
                    text = "Restore Identity",
                    isPrimary = false,
                    onClick = viewModel::openCreateSheet
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Your data stays on your device.",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = TextTertiary,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }

        // Create Identity Dialog / Bottom Sheet
        if (state.showCreateSheet) {
            Dialog(onDismissRequest = viewModel::closeCreateSheet) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
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
                                text = "Create Identity",
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTextPrimary
                                )
                            )
                            GlassIconButton(
                                icon = Icons.Default.Close,
                                onClick = viewModel::closeCreateSheet,
                                size = 32.dp,
                                iconSize = 16.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Avatar Picker
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            UserAvatar(
                                name = state.displayName.ifBlank { "Me" },
                                avatarUri = state.avatarUri,
                                size = 88.dp,
                                showCubeFallback = state.avatarUri == null && state.displayName.isBlank()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CameraAlt,
                                    contentDescription = "Change Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        GlassTextField(
                            value = state.displayName,
                            onValueChange = viewModel::onDisplayNameChanged,
                            placeholder = "e.g. Alice or Zishan",
                            label = "Display Name *",
                            singleLine = true,
                            leadingIcon = Icons.Outlined.Person,
                            errorText = state.error,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        GlassTextField(
                            value = state.age,
                            onValueChange = viewModel::onAgeChanged,
                            placeholder = "e.g. 24",
                            label = "Age (Optional)",
                            singleLine = true,
                            leadingIcon = Icons.Outlined.Cake,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        GlassTextField(
                            value = state.bio,
                            onValueChange = viewModel::onBioChanged,
                            placeholder = "A brief bio or status...",
                            label = "Description / Bio (Optional)",
                            singleLine = false,
                            leadingIcon = Icons.Outlined.Description,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.createIdentity(onSuccess = onNavigateToMain)
                                }
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        GlassButton(
                            text = "Create & Enter",
                            isPrimary = true,
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.createIdentity(onSuccess = onNavigateToMain)
                            },
                            isLoading = state.isLoading,
                            enabled = state.displayName.isNotBlank()
                        )
                    }
                }
            }
        }
    }
}

