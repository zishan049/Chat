package com.chat.app.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.appColors
import java.io.File
import java.io.FileOutputStream

@Composable
fun OnboardingScreen(
    onCompleteOnboarding: (username: String, avatarUri: Uri?, bio: String?, age: Int?) -> Unit
) {
    val context = LocalContext.current
    val colors = appColors
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentStep by remember { mutableIntStateOf(1) }

    LaunchedEffect(currentStep) {
        com.chat.app.telemetry.AppTelemetry.logScreenTransition(
            "OnboardingScreen (Step $currentStep)",
            mapOf("step" to currentStep.toString())
        )
    }

    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("Hey there! I am using Chat.") }
    var ageText by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }
    var showAvatarPickerSheet by remember { mutableStateOf(false) }

    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var notificationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            } else true
        )
    }
    var cameraGranted by remember {
        mutableStateOf(checkPermission(android.Manifest.permission.CAMERA))
    }
    var mediaGranted by remember {
        mutableStateOf(checkPermission(mediaPermission))
    }

    // Lifecycle-aware observer to sync permissions if user returns from System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                } else true
                cameraGranted = checkPermission(android.Manifest.permission.CAMERA)
                mediaGranted = checkPermission(mediaPermission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        notificationGranted = isGranted
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        cameraGranted = isGranted
    }
    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        mediaGranted = isGranted
    }

    // Modern PickVisualMedia launcher with fallback
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            avatarUri = uri
        }
    }

    // Camera Selfie Capture launcher
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val tempFile = File(context.cacheDir, "avatar_selfie_${System.currentTimeMillis()}.jpg")
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                avatarUri = Uri.fromFile(tempFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentStep = 1
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.container)
                    ) {
                        Icon(
                            imageVector = AppIcons.Back,
                            contentDescription = "Back",
                            tint = colors.txt
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Welcome to Chat",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.txt
                    )
                    Text(
                        text = "Step $currentStep of 2",
                        fontSize = 13.sp,
                        color = colors.muted
                    )
                }
            }

            // Progress Bar Animation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.divider)
            ) {
                val animatedProgress by animateFloatAsState(
                    targetValue = if (currentStep == 1) 0.5f else 1.0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "onboardingProgress"
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.accent)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Animated Step Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { width -> width } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> -width } + fadeOut()
                        } else {
                            slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> width } + fadeOut()
                        }
                    },
                    label = "stepAnimation"
                ) { step ->
                    if (step == 1) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "App Permissions",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.txt
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Grant permissions below to enable messaging, QR scanning, and notifications.",
                                fontSize = 14.sp,
                                color = colors.muted
                            )

                            Spacer(Modifier.height(24.dp))

                            PermissionRowItem(
                                icon = AppIcons.Notifications,
                                title = "Push Notifications",
                                description = "Receive instant message alerts and notifications.",
                                isGranted = notificationGranted,
                                onRequest = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        notificationGranted = true
                                    }
                                },
                                onOpenSettings = ::openAppSettings
                            )

                            Spacer(Modifier.height(14.dp))

                            PermissionRowItem(
                                icon = AppIcons.Camera,
                                title = "Camera Access",
                                description = "Take photos directly in chats and scan contact QR codes.",
                                isGranted = cameraGranted,
                                onRequest = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    cameraLauncher.launch(android.Manifest.permission.CAMERA)
                                },
                                onOpenSettings = ::openAppSettings
                            )

                            Spacer(Modifier.height(14.dp))

                            PermissionRowItem(
                                icon = AppIcons.Attach,
                                title = "Media & Storage",
                                description = "Select photos, videos, and files from your device to share.",
                                isGranted = mediaGranted,
                                onRequest = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    mediaLauncher.launch(mediaPermission)
                                },
                                onOpenSettings = ::openAppSettings
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Set Up Profile",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.txt
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Choose a display name and avatar so friends recognize you.",
                                fontSize = 14.sp,
                                color = colors.muted
                            )

                            Spacer(Modifier.height(24.dp))

                            // Avatar Selection Circle with Camera badge
                            Box(
                                modifier = Modifier
                                    .size(108.dp)
                                    .clip(CircleShape)
                                    .background(colors.card)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showAvatarPickerSheet = true
                                    },
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                if (avatarUri != null) {
                                    AsyncImage(
                                        model = avatarUri,
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AvatarCircle(
                                            name = username.ifBlank { "User" },
                                            avatarUri = null,
                                            size = 108.dp
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.card)
                                        .border(1.dp, colors.divider, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        AppIcons.Camera,
                                        contentDescription = "Add photo",
                                        tint = colors.txt,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            // Display Name with Character Counter & Next Action
                            OutlinedTextField(
                                value = username,
                                onValueChange = {
                                    if (it.length <= 30) {
                                        username = it
                                        usernameError = null
                                    }
                                },
                                label = { Text("Display Name *") },
                                supportingText = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (usernameError != null) {
                                            Text(
                                                text = usernameError!!,
                                                color = colors.danger,
                                                fontSize = 12.sp
                                            )
                                        } else {
                                            Text("", fontSize = 12.sp)
                                        }
                                        Text(
                                            text = "${username.length}/30",
                                            color = colors.muted,
                                            fontSize = 12.sp
                                        )
                                    }
                                },
                                singleLine = true,
                                isError = usernameError != null,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.divider,
                                    focusedTextColor = colors.txt,
                                    unfocusedTextColor = colors.txt,
                                    errorBorderColor = colors.danger
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(10.dp))

                            // Age Input with digit filter, max 3 digits & Sanity check
                            OutlinedTextField(
                                value = ageText,
                                onValueChange = {
                                    val digitsOnly = it.filter { char -> char.isDigit() }.take(3)
                                    ageText = digitsOnly
                                    val ageNum = digitsOnly.toIntOrNull()
                                    if (ageNum != null && (ageNum < 10 || ageNum > 120)) {
                                        ageError = "Please enter a valid age (10-120)"
                                    } else {
                                        ageError = null
                                    }
                                },
                                label = { Text("Age (Optional)") },
                                supportingText = {
                                    if (ageError != null) {
                                        Text(
                                            text = ageError!!,
                                            color = colors.danger,
                                            fontSize = 12.sp
                                        )
                                    }
                                },
                                singleLine = true,
                                isError = ageError != null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.divider,
                                    focusedTextColor = colors.txt,
                                    unfocusedTextColor = colors.txt,
                                    errorBorderColor = colors.danger
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(10.dp))

                            // Bio / Status with Character Counter & Done Action
                            OutlinedTextField(
                                value = bio,
                                onValueChange = {
                                    if (it.length <= 120) {
                                        bio = it
                                    }
                                },
                                label = { Text("Bio / Status") },
                                supportingText = {
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                        Text(
                                            text = "${bio.length}/120",
                                            color = colors.muted,
                                            fontSize = 12.sp
                                        )
                                    }
                                },
                                maxLines = 3,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent,
                                    unfocusedBorderColor = colors.divider,
                                    focusedTextColor = colors.txt,
                                    unfocusedTextColor = colors.txt
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (currentStep == 1) {
                        currentStep = 2
                    } else {
                        val trimmedName = username.trim()
                        if (trimmedName.length < 2) {
                            usernameError = "Display Name must be at least 2 characters."
                        } else if (ageError != null) {
                            // Block if age is invalid
                        } else {
                            onCompleteOnboarding(
                                trimmedName,
                                avatarUri,
                                bio.trim().ifEmpty { null },
                                ageText.toIntOrNull()
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.card,
                    contentColor = colors.txt
                ),
                border = BorderStroke(1.dp, colors.divider)
            ) {
                Text(
                    text = if (currentStep == 1) "Continue" else "Get Started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.txt
                )
            }
        }

        // Avatar Source Picker Dialog / Modal
        if (showAvatarPickerSheet) {
            AlertDialog(
                onDismissRequest = { showAvatarPickerSheet = false },
                containerColor = colors.card,
                title = {
                    Text(
                        text = "Choose Avatar",
                        fontWeight = FontWeight.Bold,
                        color = colors.txt,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.container,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAvatarPickerSheet = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(AppIcons.Gallery, contentDescription = "Gallery", tint = colors.accent)
                                Spacer(Modifier.width(12.dp))
                                Text("Choose from Gallery", color = colors.txt, fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.container,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAvatarPickerSheet = false
                                    cameraCaptureLauncher.launch(null)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(AppIcons.Camera, contentDescription = "Camera", tint = colors.accent)
                                Spacer(Modifier.width(12.dp))
                                Text("Take Selfie Photo", color = colors.txt, fontWeight = FontWeight.Medium)
                            }
                        }

                        if (avatarUri != null) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.container,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAvatarPickerSheet = false
                                        avatarUri = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(AppIcons.Delete, contentDescription = "Remove", tint = colors.danger)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Remove Custom Avatar", color = colors.danger, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAvatarPickerSheet = false }) {
                        Text("Cancel", color = colors.muted)
                    }
                }
            )
        }
    }
}

@Composable
private fun PermissionRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = appColors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = colors.accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.txt)
                Spacer(Modifier.height(2.dp))
                Text(description, fontSize = 12.sp, color = colors.muted)
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = {
                    if (isGranted) {
                        onOpenSettings()
                    } else {
                        onRequest()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) colors.positive else colors.accent
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isGranted) "Granted ✓" else "Enable", fontSize = 12.sp, color = Color.White)
            }
        }
    }
}
