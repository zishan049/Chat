package com.chat.app.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chat.app.domain.model.Contact
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.*
import java.util.Locale

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToMediaStorage: (() -> Unit)? = null,
    onAccountDeleted: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var showClearConversationsDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

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
                    text = "Settings",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTextPrimary
                    )
                )

                Spacer(modifier = Modifier.size(38.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Identity Card
                state.identity?.let { identity ->
                    GlassCard(
                        onClick = onNavigateToProfile,
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = AppGlassLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                name = identity.displayName,
                                avatarUri = identity.avatarUri,
                                isOnline = true,
                                size = 52.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = identity.displayName,
                                    style = TextStyle(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTextPrimary
                                    )
                                )
                                Text(
                                    text = "Tap to view full identity & key",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = AppTextSecondary
                                    )
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = AppTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Section 1: Appearance
                SettingsGroup(title = "Appearance") {
                    SettingToggleRow(
                        icon = Icons.Outlined.DarkMode,
                        title = "Dark Theme",
                        subtitle = "Switch between Obsidian Dark and Frosted Light",
                        checked = state.isDarkMode,
                        onCheckedChange = viewModel::toggleDarkMode
                    )
                    HorizontalDivider(color = AppGlassBorderSubtle)
                    SettingToggleRow(
                        icon = Icons.Outlined.Vibration,
                        title = "Haptic Feedback",
                        subtitle = "Tactile response on interactions",
                        checked = state.isHapticsEnabled,
                        onCheckedChange = viewModel::toggleHaptics
                    )
                }

                // Section 2: Privacy & Security
                SettingsGroup(title = "Privacy & Security") {
                    SettingNavRow(
                        icon = Icons.Outlined.Badge,
                        title = "Identity & Cryptographic Key",
                        subtitle = "View, backup and export public key",
                        onClick = onNavigateToProfile
                    )
                    HorizontalDivider(color = AppGlassBorderSubtle)
                    SettingNavRow(
                        icon = Icons.Outlined.Block,
                        title = "Blocked Contacts",
                        subtitle = "Manage restricted peers (${state.blockedContacts.size})",
                        onClick = { viewModel.openBlockedContactsDialog() }
                    )
                }

                // Section 3: Data & Storage
                SettingsGroup(title = "Data & Storage") {
                    GlassCard(
                        onClick = { onNavigateToMediaStorage?.invoke() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Storage,
                                    contentDescription = null,
                                    tint = AppTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Media Storage",
                                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppTextPrimary)
                                    )
                                    Text(
                                        text = "Manage photos, videos, voice & files",
                                        style = TextStyle(fontSize = 11.sp, color = AppTextSecondary)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatBytes(state.storageBreakdown.totalBytes),
                                    style = TextStyle(fontSize = 13.sp, color = AppTextSecondary, fontFamily = FontFamily.Monospace)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = AppTextTertiary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // Section 4: Data Management & Danger Zone
                SettingsGroup(title = "Data Management") {
                    SettingActionRow(
                        icon = Icons.Outlined.CleaningServices,
                        title = "Clear All Conversations",
                        subtitle = "Erase message history & conversation logs",
                        onClick = { showClearConversationsDialog = true }
                    )
                    HorizontalDivider(color = AppGlassBorderSubtle)
                    SettingActionRow(
                        icon = Icons.Outlined.PersonOff,
                        title = "Delete Account",
                        subtitle = "Permanently wipe keys, all chats & data",
                        isDestructive = true,
                        onClick = { showDeleteAccountDialog = true }
                    )
                }

                // Generous bottom spacing ensuring full visibility above floating navigation pill
                Spacer(modifier = Modifier.height(140.dp))
            }
        }

        // Dialog: Blocked Contacts
        if (state.showBlockedContactsDialog) {
            BlockedContactsDialog(
                contacts = state.blockedContacts,
                onDismiss = viewModel::closeBlockedContactsDialog,
                onUnblock = viewModel::unblockContact
            )
        }

        // Dialog: Clear All Conversations
        if (showClearConversationsDialog) {
            ClearConversationsDialog(
                isClearing = isClearing,
                onDismiss = { showClearConversationsDialog = false },
                onConfirm = {
                    isClearing = true
                    viewModel.clearAllConversations(
                        onComplete = {
                            isClearing = false
                            showClearConversationsDialog = false
                        }
                    )
                }
            )
        }

        // Dialog: Permanently Delete Account
        if (showDeleteAccountDialog) {
            DeleteAccountDialog(
                isDeleting = isDeleting,
                onDismiss = { showDeleteAccountDialog = false },
                onConfirm = {
                    isDeleting = true
                    viewModel.deleteAccount(
                        onSuccess = {
                            isDeleting = false
                            showDeleteAccountDialog = false
                            onAccountDeleted()
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun ClearConversationsDialog(
    isClearing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = { if (!isClearing) onDismiss() }) {
        GlassSurface(
            shape = RoundedCornerShape(28.dp),
            backgroundColor = AppTheme.colors.surface,
            borderColor = AccentWarning.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glowing Icon Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AccentWarning.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(AccentWarning.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CleaningServices,
                            contentDescription = null,
                            tint = AccentWarning,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Clear Conversations",
                    style = TextStyle(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "This will wipe message logs across all chat threads.",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = AppTextSecondary,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Breakdown Card
                GlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = AppGlassLow,
                    borderColor = AppGlassBorderSubtle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DialogItemRow(
                            icon = Icons.Outlined.DeleteOutline,
                            iconTint = AccentWarning,
                            text = "All chat histories & messages will be cleared"
                        )
                        DialogItemRow(
                            icon = Icons.Outlined.Lock,
                            iconTint = AccentGreen,
                            text = "Cryptographic identity keys remain safe"
                        )
                        DialogItemRow(
                            icon = Icons.Outlined.PeopleOutline,
                            iconTint = AccentGreen,
                            text = "Your contacts list is preserved"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassButton(
                        text = "Cancel",
                        isPrimary = false,
                        onClick = onDismiss,
                        enabled = !isClearing,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = if (isClearing) "Clearing..." else "Clear All",
                        isPrimary = true,
                        isLoading = isClearing,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = { if (!isDeleting) onDismiss() }) {
        GlassSurface(
            shape = RoundedCornerShape(28.dp),
            backgroundColor = AppTheme.colors.surface,
            borderColor = AccentDestructive.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glowing Destructive Icon Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AccentDestructive.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(AccentDestructive.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            tint = AccentDestructive,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Delete Account",
                    style = TextStyle(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentDestructive,
                        letterSpacing = (-0.3).sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Permanent hardware enclave cryptographic wipe.",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = AppTextSecondary,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Breakdown Card
                GlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = AccentDestructive.copy(alpha = 0.08f),
                    borderColor = AccentDestructive.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DialogItemRow(
                            icon = Icons.Outlined.VpnKey,
                            iconTint = AccentDestructive,
                            text = "Hardware Keystore private keys destroyed"
                        )
                        DialogItemRow(
                            icon = Icons.Outlined.FolderDelete,
                            iconTint = AccentDestructive,
                            text = "All media, photos, audio & files purged"
                        )
                        DialogItemRow(
                            icon = Icons.Outlined.PersonRemove,
                            iconTint = AccentDestructive,
                            text = "All contacts & pairing links invalidated"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassButton(
                        text = "Cancel",
                        isPrimary = false,
                        onClick = onDismiss,
                        enabled = !isDeleting,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = if (isDeleting) "Deleting..." else "Delete",
                        isPrimary = true,
                        isLoading = isDeleting,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogItemRow(
    icon: ImageVector,
    iconTint: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = TextStyle(
                fontSize = 12.sp,
                color = AppTextPrimary,
                lineHeight = 16.sp
            )
        )
    }
}

@Composable
private fun BlockedContactsDialog(
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onUnblock: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(24.dp),
            backgroundColor = AppTheme.colors.surface,
            borderColor = AppGlassBorderBright,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Blocked Contacts",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTextPrimary
                        )
                    )
                    GlassIconButton(
                        icon = Icons.Filled.Close,
                        onClick = onDismiss,
                        size = 32.dp,
                        iconSize = 16.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (contacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No blocked contacts",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = AppTextSecondary
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(contacts, key = { it.id }) { contact ->
                            GlassCard(
                                onClick = {},
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        UserAvatar(
                                            name = contact.displayName,
                                            avatarUri = contact.avatarUri,
                                            size = 36.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = contact.displayName,
                                                style = TextStyle(
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = AppTextPrimary
                                                )
                                            )
                                            Text(
                                                text = "Blocked",
                                                style = TextStyle(
                                                    fontSize = 11.sp,
                                                    color = AccentDestructive
                                                )
                                            )
                                        }
                                    }

                                    GlassButton(
                                        text = "Unblock",
                                        isPrimary = false,
                                        onClick = { onUnblock(contact.id) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GlassButton(
                    text = "Close",
                    isPrimary = true,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextTertiary,
                letterSpacing = 1.2.sp
            ),
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        GlassSurface(
            shape = RoundedCornerShape(18.dp),
            backgroundColor = AppGlassLow,
            borderColor = AppGlassBorderSubtle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppTextPrimary
                    )
                )
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = AppTextSecondary
                    )
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppTheme.colors.background,
                checkedTrackColor = AppTheme.colors.textPrimary,
                uncheckedThumbColor = AppTextTertiary,
                uncheckedTrackColor = AppGlassMedium,
                uncheckedBorderColor = AppGlassBorder
            )
        )
    }
}

@Composable
private fun SettingNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppTextPrimary
                    )
                )
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = AppTextSecondary
                    )
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = AppTextTertiary,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun SettingActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) AccentDestructive else AppTextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDestructive) AccentDestructive else AppTextPrimary
                    )
                )
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = AppTextSecondary
                    )
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = if (isDestructive) AccentDestructive.copy(alpha = 0.6f) else AppTextTertiary,
            modifier = Modifier.size(14.dp)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
