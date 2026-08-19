package com.chat.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chat.app.data.Profile
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.appColors
import java.io.File

@Composable
fun ProfileScreen(
    profile: Profile?,
    isSelf: Boolean,
    isPeerOnline: Boolean = false,
    isSameWifi: Boolean = false,
    peerLastSeenAt: Long? = null,
    onBack: () -> Unit,
    onUpdateProfile: (username: String, avatarUri: String?, age: Int?, description: String?) -> Unit,
    onToggleBlock: (() -> Unit)? = null,
    onDeleteAllChats: (() -> Unit)? = null,
    onDeleteContact: (() -> Unit)? = null,
    onSetNickname: ((nickname: String?) -> Unit)? = null,
) {
    val colors = appColors
    val context = LocalContext.current

    var username by remember(profile) { mutableStateOf(profile?.username ?: "") }
    var ageText by remember(profile) { mutableStateOf(profile?.age?.toString() ?: "") }
    var description by remember(profile) { mutableStateOf(profile?.description ?: "") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isEditing by remember { mutableStateOf(false) }

    fun performSaveProfile(explicitAvatarUri: Uri? = selectedImageUri) {
        val nameToSave = username.trim().ifBlank { "User" }
        val avatarToSave = explicitAvatarUri?.toString() ?: profile?.avatarUri
        onUpdateProfile(
            nameToSave,
            avatarToSave,
            ageText.toIntOrNull(),
            description.trim()
        )
        isEditing = false
    }

    var showNicknameDialog by remember { mutableStateOf(false) }
    var showDeleteChatsConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteContactConfirmDialog by remember { mutableStateOf(false) }

    val isBlocked = profile?.isBlocked == true
    val activeNickname = profile?.nickname?.ifBlank { null }
    val originalUsername = profile?.username?.ifBlank { "User" } ?: "User"
    val targetPeerName = activeNickname ?: originalUsername

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            // ── Autosave immediately upon photo selection ──
            performSaveProfile(uri)
            Toast.makeText(context, "Profile photo updated & saved", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header Bar
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.Back, contentDescription = "Back", tint = colors.txt)
                }

                Text(
                    text = if (isSelf) "Profile" else "User Info",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.txt
                )

                if (isSelf) {
                    IconButton(onClick = {
                        if (isEditing || selectedImageUri != null) {
                            performSaveProfile()
                            Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                        } else {
                            isEditing = true
                        }
                    }) {
                        Icon(
                            if (isEditing || selectedImageUri != null) AppIcons.Check else AppIcons.Edit,
                            contentDescription = if (isEditing || selectedImageUri != null) "Save Profile" else "Edit Profile",
                            tint = colors.txt
                        )
                    }
                } else {
                    IconButton(onClick = { showNicknameDialog = true }) {
                        Icon(
                            AppIcons.Edit,
                            contentDescription = "Edit Nickname",
                            tint = colors.accent
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Main Profile Header (Avatar + Name + Status)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                val avatarPath = profile?.avatarUri
                val avatarModel = remember(avatarPath, selectedImageUri) {
                    if (selectedImageUri != null) {
                        selectedImageUri
                    } else if (!avatarPath.isNullOrBlank()) {
                        if (avatarPath.startsWith("content://") || avatarPath.startsWith("http://") || avatarPath.startsWith("https://") || avatarPath.startsWith("file://")) {
                            avatarPath
                        } else if (avatarPath.startsWith("/")) {
                            val file = File(avatarPath)
                            if (file.exists()) file else null
                        } else {
                            val file = File(context.filesDir, avatarPath)
                            if (file.exists()) file else null
                        }
                    } else null
                }

                if (avatarModel != null) {
                    AsyncImage(
                        model = avatarModel,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(colors.card),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AvatarCircle(
                        name = if (isSelf) username else targetPeerName,
                        avatarUri = null,
                        size = 110.dp
                    )
                }

                if (isSelf) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.dp, colors.divider, CircleShape)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Camera, null, tint = colors.txt, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (isSelf) username.ifBlank { "User" } else targetPeerName,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = colors.txt
            )

            val statusText = if (isSelf) {
                "online"
            } else if (isPeerOnline) {
                "online"
            } else if (peerLastSeenAt != null && peerLastSeenAt > 0) {
                "last seen " + formatLastSeenProfileTime(peerLastSeenAt)
            } else {
                "offline"
            }
            val statusColor = if (isSelf || isPeerOnline) colors.positive else colors.muted
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isSelf || isPeerOnline) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.positive)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )

                if (!isSelf && isSameWifi && isPeerOnline) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.accent.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, colors.accent.copy(alpha = 0.35f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Wifi,
                                contentDescription = "Same Wi-Fi",
                                tint = colors.accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Same Wi-Fi",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Dedicated Save Button Banner for Image / Edits ───────────
        if (isSelf && (selectedImageUri != null || isEditing)) {
            Surface(
                onClick = {
                    performSaveProfile()
                    Toast.makeText(context, "Profile saved successfully", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                color = colors.card,
                border = BorderStroke(1.dp, colors.divider),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(AppIcons.Check, contentDescription = null, tint = colors.txt, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (selectedImageUri != null) "Save Profile Photo" else "Save Profile Changes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.txt
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Quick Action Cards Row
        if (isSelf) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileActionCard(
                    icon = AppIcons.Camera,
                    title = "Change Photo",
                    modifier = Modifier.weight(1f),
                    onClick = { imagePickerLauncher.launch("image/*") }
                )

                ProfileActionCard(
                    icon = if (isEditing) AppIcons.Check else AppIcons.Edit,
                    title = if (isEditing) "Save Info" else "Edit Info",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isEditing) {
                            performSaveProfile()
                            Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                        } else {
                            isEditing = true
                        }
                    }
                )
            }

            Spacer(Modifier.height(20.dp))
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileActionCard(
                    icon = AppIcons.Chats,
                    title = "Message",
                    modifier = Modifier.weight(1f),
                    onClick = { onBack() }
                )

                ProfileActionCard(
                    icon = AppIcons.Edit,
                    title = if (activeNickname != null) "Edit Nickname" else "Set Nickname",
                    iconColor = colors.accent,
                    modifier = Modifier.weight(1f),
                    onClick = { showNicknameDialog = true }
                )

                if (onToggleBlock != null) {
                    ProfileActionCard(
                        icon = if (isBlocked) AppIcons.Check else AppIcons.Lock,
                        title = if (isBlocked) "Unblock" else "Block",
                        iconColor = if (isBlocked) colors.positive else colors.warning,
                        modifier = Modifier.weight(1f),
                        onClick = { onToggleBlock() }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        // Profile Detail Info Container
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isEditing && isSelf) {
                    Text("Display Name", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.muted)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.divider,
                            focusedTextColor = colors.txt,
                            unfocusedTextColor = colors.txt
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(14.dp))

                    Text("Age", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.muted)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it.filter { char -> char.isDigit() } },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.divider,
                            focusedTextColor = colors.txt,
                            unfocusedTextColor = colors.txt
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(14.dp))

                    Text("Bio / Description", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.muted)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.divider,
                            focusedTextColor = colors.txt,
                            unfocusedTextColor = colors.txt
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (username.isNotBlank()) {
                                performSaveProfile()
                                Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.card,
                            contentColor = colors.txt
                        ),
                        border = BorderStroke(1.dp, colors.divider),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(AppIcons.Check, contentDescription = null, tint = colors.txt, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Profile", fontWeight = FontWeight.Bold, color = colors.txt)
                    }
                } else {
                    if (!isSelf) {
                        // Separate Nickname Row
                        ProfileInfoRowWithAction(
                            title = activeNickname ?: "None (Tap to add nickname)",
                            subtitle = "Nickname (Private, only visible to you on this device)",
                            actionIcon = AppIcons.Edit,
                            onClick = { showNicknameDialog = true }
                        )

                        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 12.dp))
                    }

                    ProfileInfoRow(
                        title = "@${(profile?.username ?: "user").lowercase().replace(" ", "")}",
                        subtitle = "Username"
                    )

                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 12.dp))

                    ProfileInfoRow(
                        title = profile?.id ?: "ID: Unknown",
                        subtitle = "User ID (tap to copy)",
                        onClick = {
                            val peerId = profile?.id
                            if (!peerId.isNullOrBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("User ID", peerId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "User ID copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 12.dp))

                    ProfileInfoRow(
                        title = profile?.description?.ifBlank { "No bio added yet" } ?: "No bio added yet",
                        subtitle = "Bio"
                    )
                }
            }
        }

        // Actions / Danger Zone (For Peer Profile)
        if (!isSelf) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = "DANGER ZONE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.danger,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column {
                    // Delete All Chats Button
                    ProfileClickRow(
                        icon = AppIcons.Delete,
                        title = "Delete All Chats",
                        subtitle = "Permanently erase all message logs and chat history with $targetPeerName",
                        titleColor = colors.danger,
                        onClick = { showDeleteChatsConfirmDialog = true }
                    )

                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(start = 56.dp))

                    // Delete Contact Button
                    ProfileClickRow(
                        icon = AppIcons.PersonAdd,
                        title = "Delete Contact",
                        subtitle = "Remove $targetPeerName from your contacts and delete conversation",
                        titleColor = colors.danger,
                        onClick = { showDeleteContactConfirmDialog = true }
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    // Nickname Edit Dialog
    if (showNicknameDialog) {
        var nicknameInput by remember(profile?.nickname) { mutableStateOf(profile?.nickname ?: "") }
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            containerColor = colors.card,
            title = {
                Text(
                    text = "Set Nickname",
                    fontWeight = FontWeight.Bold,
                    color = colors.txt
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Assign a custom nickname for $originalUsername. Stored only in your local database and not shared with anyone.",
                        color = colors.muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = nicknameInput,
                        onValueChange = { nicknameInput = it },
                        singleLine = true,
                        placeholder = { Text("Enter nickname (e.g. Bestie, Alex)", color = colors.muted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.divider,
                            focusedTextColor = colors.txt,
                            unfocusedTextColor = colors.txt
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = nicknameInput.trim().ifBlank { null }
                        onSetNickname?.invoke(trimmed)
                        showNicknameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.card,
                        contentColor = colors.txt
                    ),
                    border = BorderStroke(1.dp, colors.divider)
                ) {
                    Text("Save", color = colors.txt, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    if (!profile?.nickname.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                onSetNickname?.invoke(null)
                                showNicknameDialog = false
                            }
                        ) {
                            Text("Reset", color = colors.danger)
                        }
                    }
                    TextButton(onClick = { showNicknameDialog = false }) {
                        Text("Cancel", color = colors.muted)
                    }
                }
            }
        )
    }

    // Delete All Chats Confirmation Dialog
    if (showDeleteChatsConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteChatsConfirmDialog = false },
            containerColor = colors.card,
            title = {
                Text(
                    text = "Delete All Chats?",
                    fontWeight = FontWeight.Bold,
                    color = colors.txt
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all messages and media history with $targetPeerName. This action cannot be undone.",
                    color = colors.muted,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteChatsConfirmDialog = false
                        onDeleteAllChats?.invoke()
                    }
                ) {
                    Text("Delete All Chats", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatsConfirmDialog = false }) {
                    Text("Cancel", color = colors.muted)
                }
            }
        )
    }

    // Delete Contact Confirmation Dialog
    if (showDeleteContactConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteContactConfirmDialog = false },
            containerColor = colors.card,
            title = {
                Text(
                    text = "Delete Contact?",
                    fontWeight = FontWeight.Bold,
                    color = colors.danger
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove $targetPeerName from your saved contacts? All chats and shared messages will also be permanently deleted.",
                    color = colors.muted,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteContactConfirmDialog = false
                        onDeleteContact?.invoke()
                    }
                ) {
                    Text("Delete Contact", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteContactConfirmDialog = false }) {
                    Text("Cancel", color = colors.muted)
                }
            }
        )
    }
}

@Composable
private fun ProfileActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    iconColor: Color = appColors.accent,
    onClick: () -> Unit
) {
    val colors = appColors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.txt)
        }
    }
}

@Composable
private fun ProfileInfoRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    val colors = appColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.txt)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 12.sp, color = colors.muted)
    }
}

@Composable
private fun ProfileInfoRowWithAction(
    title: String,
    subtitle: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val colors = appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.txt)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = colors.muted)
        }
        Icon(actionIcon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ProfileClickRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color = appColors.txt,
    onClick: () -> Unit
) {
    val colors = appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = titleColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = titleColor)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = colors.muted)
        }
    }
}

private fun formatLastSeenProfileTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    return when {
        diffMs < 60_000L -> "just now"
        diffMs < 3600_000L -> "${diffMs / 60_000L}m ago"
        diffMs < 86400_000L -> "at " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        else -> java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}
