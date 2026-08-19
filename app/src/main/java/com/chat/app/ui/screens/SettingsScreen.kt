package com.chat.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat.app.data.Profile
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.appColors

@Composable

fun SettingsScreen(
    selfProfile: Profile?,
    storageBytes: Long,
    isDarkMode: Boolean,
    isHaptics: Boolean,
    isSound: Boolean = true,
    isAutoDownload: Boolean = true,
    isMessagePreview: Boolean = true,
    onDarkModeToggle: (Boolean) -> Unit,
    onHapticsToggle: (Boolean) -> Unit,
    onSoundToggle: (Boolean) -> Unit = {},
    onAutoDownloadToggle: (Boolean) -> Unit = {},
    onMessagePreviewToggle: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onMediaStorageClick: () -> Unit,
    onDeleteAllChats: () -> Unit,
    onDeleteProfileAndReset: () -> Unit = {},
) {
    val colors = appColors
    var showDeleteChatsConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteProfileConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.Back, contentDescription = "Back", tint = colors.txt)
                }

                Text(
                    text = "Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.txt
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Profile Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onEditProfile() }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCircle(
                    name = selfProfile?.username ?: "Me",
                    avatarUri = selfProfile?.avatarUri,
                    size = 56.dp
                )

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selfProfile?.username ?: "User",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = colors.txt
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Tap to view & edit profile",
                        fontSize = 13.sp,
                        color = colors.accent
                    )
                }

                Icon(AppIcons.Edit, contentDescription = "Edit Profile", tint = colors.accent, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        // APPEARANCE & SOUNDS Section Header
        Text(
            text = "APPEARANCE & SOUNDS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accent,
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
                // Deep Black Mode Toggle
                SettingsToggleRow(
                    icon = AppIcons.Settings,
                    title = "Deep Black Mode",
                    subtitle = "Pure AMOLED dark theme for power efficiency",
                    checked = isDarkMode,
                    onCheckedChange = onDarkModeToggle
                )

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(start = 56.dp))

                // Haptic Feedback Toggle
                SettingsToggleRow(
                    icon = AppIcons.Notifications,
                    title = "Haptic Feedback",
                    subtitle = "Tactile vibration feedback on button presses",
                    checked = isHaptics,
                    onCheckedChange = onHapticsToggle
                )

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(start = 56.dp))

                // Sound Alert Toggle
                SettingsToggleRow(
                    icon = AppIcons.Notifications,
                    title = "Sound Notifications",
                    subtitle = "Audible sound chimes for incoming messages",
                    checked = isSound,
                    onCheckedChange = onSoundToggle
                )

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(start = 56.dp))

                // Message Preview Toggle
                SettingsToggleRow(
                    icon = AppIcons.Chats,
                    title = "Message Previews",
                    subtitle = "Show message text snippets in notification banners",
                    checked = isMessagePreview,
                    onCheckedChange = onMessagePreviewToggle
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // MEDIA & NETWORK DATA Section Header
        Text(
            text = "MEDIA & NETWORK DATA",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accent,
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
                // Auto Download Toggle
                SettingsToggleRow(
                    icon = AppIcons.Attach,
                    title = "Auto-Download Media",
                    subtitle = "Automatically save incoming media files to local storage",
                    checked = isAutoDownload,
                    onCheckedChange = onAutoDownloadToggle
                )

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(start = 56.dp))

                // Storage & Data Usage
                val formattedStorage = remember(storageBytes) { formatStorageSize(storageBytes) }
                SettingsClickRow(
                    icon = AppIcons.Storage,
                    title = "Data and Storage",
                    subtitle = "Media storage breakdown ($formattedStorage)",
                    onClick = onMediaStorageClick
                )
            }
        }



        Spacer(Modifier.height(24.dp))

        // DANGER ZONE Section Header
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
                SettingsClickRow(
                    icon = AppIcons.Delete,
                    title = "Clear All Conversations",
                    subtitle = "Permanently delete all chat history and local messages",
                    titleColor = colors.danger,
                    onClick = { showDeleteChatsConfirmDialog = true }
                )

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(start = 56.dp))

                SettingsClickRow(
                    icon = AppIcons.PersonAdd,
                    title = "Delete Profile & Reset App",
                    subtitle = "Erase profile, contacts, and all app data, then return to onboarding",
                    titleColor = colors.danger,
                    onClick = { showDeleteProfileConfirmDialog = true }
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showDeleteChatsConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteChatsConfirmDialog = false },
            containerColor = colors.card,
            title = { Text("Delete All Chats?", fontWeight = FontWeight.Bold, color = colors.txt) },
            text = {
                Text(
                    "This action will permanently erase all chat logs and local message databases. This cannot be undone.",
                    color = colors.muted,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAllChats()
                    showDeleteChatsConfirmDialog = false
                }) {
                    Text("Clear All", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatsConfirmDialog = false }) {
                    Text("Cancel", color = colors.muted)
                }
            }
        )
    }

    if (showDeleteProfileConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProfileConfirmDialog = false },
            containerColor = colors.card,
            title = { Text("Delete Profile & Reset?", fontWeight = FontWeight.Bold, color = colors.danger) },
            text = {
                Text(
                    "This action will permanently delete your user profile, saved contacts, chat logs, media cache, and app settings. The app will restart at the initial onboarding screen. This cannot be undone.",
                    color = colors.muted,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteProfileConfirmDialog = false
                    onDeleteProfileAndReset()
                }) {
                    Text("Delete Profile", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProfileConfirmDialog = false }) {
                    Text("Cancel", color = colors.muted)
                }
            }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.txt)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = colors.muted)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.bg,
                checkedTrackColor = colors.txt,
                uncheckedThumbColor = colors.muted,
                uncheckedTrackColor = colors.container
            )
        )
    }
}

@Composable
private fun SettingsClickRow(
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

private fun formatStorageSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
