package com.chat.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chat.app.data.Profile
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.appColors
import com.chat.app.utils.ProfileQrManager
import com.chat.app.utils.ScannedProfileData
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.UUID
import java.util.concurrent.Executors

@Composable
fun AddContactScreen(
    selfProfile: Profile?,
    initialTab: Int = 0,
    onBack: () -> Unit,
    onAddContact: (id: String, name: String, age: Int?, description: String?) -> Unit,
    onQrScanned: (ScannedProfileData) -> Unit = {}
) {
    val colors = appColors
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Top Header Bar
        Surface(
            color = colors.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = "Back", tint = colors.txt)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Invite & Add Contact",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.txt
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Segmented Pill Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.container)
                        .padding(4.dp)
                ) {
                    val tabs = listOf("My QR Code", "Scan QR", "Add by ID")
                    tabs.forEachIndexed { index, title ->
                        val selected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) colors.card else Color.Transparent
                                )
                                .then(
                                    if (selected) Modifier.border(1.dp, colors.divider, RoundedCornerShape(10.dp))
                                    else Modifier
                                )
                                .clickable { selectedTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (selected) colors.txt else colors.muted
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> MyQrCodeTab(selfProfile = selfProfile, context = context)
                1 -> ScanQrTab(onQrScanned = onQrScanned)
                2 -> AddByIdTab(onAddContact = onAddContact, selfProfile = selfProfile, onBack = onBack)
            }
        }
    }
}

@Composable
private fun MyQrCodeTab(
    selfProfile: Profile?,
    context: Context
) {
    val colors = appColors
    var currentTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var secondsRemaining by remember { mutableIntStateOf(30) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000L)
            if (secondsRemaining > 1) {
                secondsRemaining--
            } else {
                secondsRemaining = 30
                currentTimestamp = System.currentTimeMillis()
            }
        }
    }

    // Resolve best IP (local LAN) asynchronously so the QR works cross-network.
    val localIp = remember { ProfileQrManager.getLocalIpAddress() ?: "" }

    val qrPayload = remember(selfProfile, currentTimestamp, localIp) {
        val port = com.chat.app.utils.P2PQrExchangeManager.getActivePort()
        ProfileQrManager.buildProfileQrPayload(
            profile = selfProfile,
            timestamp = currentTimestamp,
            ip = localIp,
            port = port
        )
    }

    val qrBitmap by produceState<Bitmap?>(initialValue = null, key1 = qrPayload) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            if (qrPayload.isNotEmpty()) {
                try {
                    ProfileQrManager.generateQRCodeBitmap(qrPayload, 512)
                } catch (e: Exception) {
                    null
                }
            } else null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // User Profile Header
        AvatarCircle(
            name = selfProfile?.username ?: "Me",
            avatarUri = selfProfile?.avatarUri,
            size = 76.dp
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = selfProfile?.username ?: "User",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = colors.txt
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = ProfileQrManager.getDeviceInfo(),
            fontSize = 12.sp,
            color = colors.muted
        )

        Spacer(Modifier.height(14.dp))

        // Security & Refresh Badges Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.accent.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Refreshes in ${secondsRemaining}s",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.positive.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, colors.positive.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.positive,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "ECDSA Signed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.positive
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Large QR Code Box Frame
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .size(240.dp)
                .border(
                    BorderStroke(2.dp, colors.accent.copy(alpha = 0.3f)),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val currentBmp = qrBitmap
                if (currentBmp != null) {
                    Image(
                        bitmap = currentBmp.asImageBitmap(),
                        contentDescription = "My QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Payload Code Box & Action Buttons
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Your QR Link Payload",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.muted
                    )
                    Text(
                        "V4 Signed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.container)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = qrPayload,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.txt,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Contact Code", qrPayload)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Contact code copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.txt),
                        border = BorderStroke(1.dp, colors.divider)
                    ) {
                        Icon(AppIcons.Copy, contentDescription = null, tint = colors.txt, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Code", fontSize = 12.sp, color = colors.txt, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val shareUrl = ProfileQrManager.buildShareableContactUrl(selfProfile)
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "Connect with me on Chat App:\n$shareUrl\n\nOr paste my contact code:\n$qrPayload"
                                )
                                type = "text/plain"
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Contact Payload")
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.card,
                            contentColor = colors.txt
                        ),
                        border = BorderStroke(1.dp, colors.divider),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = colors.txt, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share Link", fontSize = 12.sp, color = colors.txt, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanQrTab(
    onQrScanned: (ScannedProfileData) -> Unit
) {
    val colors = appColors
    val context = LocalContext.current
    var inputPayload by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Modern Camera Scanner with Torch, Gallery Import & Animated Laser
        LiveCameraScanner(onQrScanned = onQrScanned)

        Spacer(Modifier.height(20.dp))

        // Manual Paste Section
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Paste QR String / Deep Link",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = colors.txt
                    )

                    // Quick Paste from Clipboard button
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (clipText.isNotBlank()) {
                                inputPayload = clipText.trim()
                                parseError = false
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = colors.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Paste", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = inputPayload,
                    onValueChange = {
                        inputPayload = it
                        parseError = false
                    },
                    placeholder = {
                        Text(
                            "Paste CHATCONTACT_V4... or chat:// link",
                            color = colors.muted,
                            fontSize = 12.sp
                        )
                    },
                    isError = parseError,
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.divider,
                        focusedTextColor = colors.txt,
                        unfocusedTextColor = colors.txt,
                        focusedContainerColor = colors.container,
                        unfocusedContainerColor = colors.container
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (parseError) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Invalid contact format. Please check the code or deep link.",
                        color = colors.danger,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        val parsed = ProfileQrManager.parseProfileQrPayload(inputPayload.trim())
                        if (parsed != null) {
                            onQrScanned(parsed)
                        } else {
                            parseError = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.card,
                        contentColor = colors.txt
                    ),
                    border = BorderStroke(1.dp, colors.divider)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = colors.txt,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Process & Add Contact", fontWeight = FontWeight.Bold, color = colors.txt, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun LiveCameraScanner(
    onQrScanned: (ScannedProfileData) -> Unit
) {
    val colors = appColors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasScanned by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(hasScanned) {
        if (hasScanned) {
            delay(3000L)
            hasScanned = false
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Gallery Picker to scan QR codes from screenshots / photos
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    val source = RGBLuminanceSource(width, height, pixels)
                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                    val reader = MultiFormatReader().apply {
                        val hints = mapOf(
                            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                            DecodeHintType.TRY_HARDER to true
                        )
                        setHints(hints)
                    }
                    val result = reader.decode(binaryBitmap)
                    if (!result.text.isNullOrBlank()) {
                        val parsed = ProfileQrManager.parseProfileQrPayload(result.text)
                        if (parsed != null) {
                            hasScanned = true
                            onQrScanned(parsed)
                        } else {
                            Toast.makeText(context, "QR code recognized, but not a valid contact payload", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Could not open image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "No readable QR code found in photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Unbind camera when composable is disposed
    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                val provider = cameraProviderFuture.get()
                provider?.unbindAll()
            } catch (_: Exception) {}
        }
    }

    if (!hasCameraPermission) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AppIcons.Camera,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Camera Permission Needed",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.txt
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Point your camera at a contact QR code to automatically establish an end-to-end encrypted connection.",
                    fontSize = 12.sp,
                    color = colors.muted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.card,
                        contentColor = colors.txt
                    ),
                    border = BorderStroke(1.dp, colors.divider),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("Enable Camera", fontWeight = FontWeight.Bold, color = colors.txt)
                }
            }
        }
    } else {
        // Active Camera Scanner Container
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    BorderStroke(
                        2.dp,
                        Brush.verticalGradient(
                            listOf(colors.accent, colors.positive.copy(alpha = 0.5f), colors.accentDark)
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Live Camera View
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val executor = Executors.newSingleThreadExecutor()

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(executor, QrCodeAnalyzer { scannedRaw ->
                                if (!hasScanned) {
                                    val parsed = ProfileQrManager.parseProfileQrPayload(scannedRaw, context)
                                    if (parsed != null) {
                                        // Compose state must be mutated on the main thread —
                                        // the analyzer runs on a background executor, so post back.
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            if (!hasScanned) {
                                                hasScanned = true
                                                onQrScanned(parsed)
                                            }
                                        }
                                    }
                                }
                            })

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                val cam = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                                activeCamera = cam
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Animated Laser Scanner Overlay
                val infiniteTransition = rememberInfiniteTransition(label = "laser")
                val laserProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2200, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "laser_anim"
                )

                // High-Tech Viewfinder Overlay with Reticle & Laser
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    ) {
                        // Animated Scanning Line
                        if (!hasScanned) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.012f)
                                    .offset(y = (240 * laserProgress).dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                colors.accent,
                                                colors.positive,
                                                colors.accent,
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .shadow(8.dp, spotColor = colors.positive)
                            )
                        }
                    }
                }

                // Top Scanner Header Floating Pills (Torch & Gallery buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Floating Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.positive)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Scanning Ready",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Floating Action Controls: Torch & Gallery Import
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Flashlight / Torch Toggle
                        IconButton(
                            onClick = {
                                val nextTorch = !isTorchOn
                                isTorchOn = nextTorch
                                activeCamera?.cameraControl?.enableTorch(nextTorch)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Toggle Torch",
                                tint = if (isTorchOn) colors.accent else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Pick QR from Photos / Gallery
                        IconButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Collections,
                                contentDescription = "Scan from Gallery",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Scanned Success Overlay
                if (hasScanned) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = colors.positive,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "QR Code Scanned!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        setHints(hints)
    }

    private var lastScannedTime = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScannedTime < 500L) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null && mediaImage.format == android.graphics.ImageFormat.YUV_420_888) {
            val yPlane = imageProxy.planes[0]
            val buffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val source = PlanarYUVLuminanceSource(
                bytes,
                rowStride,
                imageProxy.height,
                0,
                0,
                imageProxy.width,
                imageProxy.height,
                false
            )

            var scannedText: String? = null

            // Strategy 1: Standard Hybrid Binarizer
            try {
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                val result = reader.decodeWithState(binaryBitmap)
                if (!result.text.isNullOrBlank()) {
                    scannedText = result.text
                }
            } catch (_: Exception) {
                // Try fallback binarizer
            } finally {
                reader.reset()
            }

            // Strategy 2: Global Histogram Binarizer fallback (for high contrast / screen reflections)
            if (scannedText.isNullOrBlank()) {
                try {
                    val binaryBitmap = BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(source))
                    val result = reader.decodeWithState(binaryBitmap)
                    if (!result.text.isNullOrBlank()) {
                        scannedText = result.text
                    }
                } catch (_: Exception) {
                    // Try inverted binarizer fallback
                } finally {
                    reader.reset()
                }
            }

            // Strategy 3: Inverted Hybrid Binarizer (dark mode QR on OLED screens)
            if (scannedText.isNullOrBlank()) {
                try {
                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source.invert()))
                    val result = reader.decodeWithState(binaryBitmap)
                    if (!result.text.isNullOrBlank()) {
                        scannedText = result.text
                    }
                } catch (_: Exception) {
                    // No QR found in this frame
                } finally {
                    reader.reset()
                }
            }

            if (!scannedText.isNullOrBlank()) {
                lastScannedTime = currentTime
                onQrCodeScanned(scannedText)
            }
        }
        imageProxy.close()
    }
}

@Composable
private fun AddByIdTab(
    onAddContact: (id: String, name: String, age: Int?, description: String?) -> Unit,
    selfProfile: Profile?,
    onBack: () -> Unit
) {
    val colors = appColors
    var contactIdInput by remember { mutableStateOf("") }
    var contactNameInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Add Contact Manually",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.txt
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Enter your contact's Display Name and Peer / Contact ID",
                    fontSize = 12.sp,
                    color = colors.muted
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = contactNameInput,
                    onValueChange = { contactNameInput = it },
                    label = { Text("Display Name *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.divider,
                        focusedTextColor = colors.txt,
                        unfocusedTextColor = colors.txt,
                        focusedContainerColor = colors.container,
                        unfocusedContainerColor = colors.container
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = contactIdInput,
                    onValueChange = { contactIdInput = it },
                    label = { Text("Contact / Peer ID (Optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.divider,
                        focusedTextColor = colors.txt,
                        unfocusedTextColor = colors.txt,
                        focusedContainerColor = colors.container,
                        unfocusedContainerColor = colors.container
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (contactNameInput.isNotBlank()) {
                            val id = contactIdInput.trim().ifBlank { UUID.randomUUID().toString() }
                            onAddContact(id, contactNameInput.trim(), null, null)
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.card,
                        contentColor = colors.txt
                    ),
                    border = BorderStroke(1.dp, colors.divider)
                ) {
                    Icon(AppIcons.PersonAdd, contentDescription = null, tint = colors.txt)
                    Spacer(Modifier.width(8.dp))
                    Text("Add to Contacts", fontWeight = FontWeight.Bold, color = colors.txt)
                }
            }
        }
    }
}
