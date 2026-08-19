package com.chat.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat.app.data.Profile
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.appColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    contacts: List<Profile>,
    peerPresence: Map<String, com.chat.app.utils.PeerPresence> = emptyMap(),
    selfProfile: Profile? = null,
    onContactClick: (Profile) -> Unit,
    onDeleteContact: (Profile) -> Unit,
    onNavigateToAddContact: () -> Unit,
    onProfileClick: () -> Unit = {},
    onSetNickname: ((profileId: String, nickname: String?) -> Unit)? = null,
) {
    val colors = appColors
    var query by remember { mutableStateOf("") }
    var selectedContactForMenu by remember { mutableStateOf<Profile?>(null) }
    var contactForNicknameDialog by remember { mutableStateOf<Profile?>(null) }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter { 
            it.displayName.contains(query, ignoreCase = true) || 
            it.username.contains(query, ignoreCase = true) 
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Contacts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = colors.txt
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateToAddContact) {
                                Icon(
                                    AppIcons.PersonAdd,
                                    contentDescription = "Invite / Add Contact",
                                    tint = colors.accent
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            // Profile Avatar Icon Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { onProfileClick() }
                            ) {
                                AvatarCircle(
                                    name = selfProfile?.username ?: "Me",
                                    avatarUri = selfProfile?.avatarUri,
                                    size = 36.dp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Telegram Search Bar
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.container,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                AppIcons.Search,
                                contentDescription = null,
                                tint = colors.muted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(color = colors.txt, fontSize = 14.sp),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) Text("Search Contacts", color = colors.muted, fontSize = 14.sp)
                                    inner()
                                },
                                singleLine = true
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                // Quick Action Cards (Invite Friends)
                item {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToAddContact() }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colors.card)
                                    .border(1.dp, colors.divider, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(AppIcons.PersonAdd, null, tint = colors.txt, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Invite Friends", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.txt)
                                Text("Scan QR code or share contact ID", fontSize = 12.sp, color = colors.muted)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Sorted by last seen time",
                        color = colors.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }

                if (filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (query.isBlank()) "No contacts added yet." else "No matching contacts found.",
                                color = colors.muted,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(
                        items = filtered,
                        key = { it.id },
                        contentType = { "contact_item" }
                    ) { contact ->
                        val isPeerOnline = peerPresence[contact.id]?.isOnline == true
                        val isSameWifi = peerPresence[contact.id]?.isSameWifi == true
                        val lastSeenAt = peerPresence[contact.id]?.lastSeenAt ?: contact.lastSeenAt
                        ContactListItem(
                            contact = contact,
                            isPeerOnline = isPeerOnline,
                            isSameWifi = isSameWifi,
                            lastSeenAt = lastSeenAt,
                            onClick = { onContactClick(contact) },
                            onLongClick = { selectedContactForMenu = contact }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp),
                            color = colors.divider,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }

    // Long press context menu dialog
    if (selectedContactForMenu != null) {
        val contact = selectedContactForMenu!!
        val isPeerOnline = peerPresence[contact.id]?.isOnline == true
        val isSameWifi = peerPresence[contact.id]?.isSameWifi == true
        val hasCustomNickname = !contact.nickname.isNullOrBlank()

        AlertDialog(
            onDismissRequest = { selectedContactForMenu = null },
            containerColor = colors.card,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AvatarCircle(
                        name = contact.displayName,
                        avatarUri = contact.avatarUri,
                        size = 44.dp,
                        showOnlineStatus = true,
                        isOnline = isPeerOnline,
                        isSameWifi = isSameWifi
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = colors.txt,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasCustomNickname) {
                            Text(
                                text = "@${contact.username}",
                                fontSize = 12.sp,
                                color = colors.muted
                            )
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(bottom = 8.dp))

                    TextButton(
                        onClick = {
                            onContactClick(contact)
                            selectedContactForMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Chats, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Start Conversation", color = colors.txt, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    TextButton(
                        onClick = {
                            val selected = contact
                            selectedContactForMenu = null
                            contactForNicknameDialog = selected
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Edit, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(if (hasCustomNickname) "Edit Nickname" else "Set Nickname", color = colors.txt, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    TextButton(
                        onClick = {
                            onDeleteContact(contact)
                            selectedContactForMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Delete, null, tint = colors.danger, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Delete Contact", color = colors.danger, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedContactForMenu = null }) {
                    Text("Cancel", color = colors.muted)
                }
            }
        )
    }

    // Nickname Edit Dialog
    if (contactForNicknameDialog != null) {
        val contact = contactForNicknameDialog!!
        var nicknameInput by remember(contactForNicknameDialog) { mutableStateOf(contact.nickname ?: "") }

        AlertDialog(
            onDismissRequest = { contactForNicknameDialog = null },
            containerColor = colors.card,
            title = { Text("Contact Nickname", fontWeight = FontWeight.Bold, color = colors.txt) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Assign a private nickname for ${contact.username}. Stored locally in your DB and never shared.",
                        fontSize = 13.sp,
                        color = colors.muted
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = nicknameInput,
                        onValueChange = { nicknameInput = it },
                        singleLine = true,
                        placeholder = { Text("Enter nickname (e.g. Best Friend)", color = colors.muted) },
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
                        onSetNickname?.invoke(contact.id, trimmed)
                        contactForNicknameDialog = null
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
                    if (!contact.nickname.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                onSetNickname?.invoke(contact.id, null)
                                contactForNicknameDialog = null
                            }
                        ) {
                            Text("Reset", color = colors.danger)
                        }
                    }
                    TextButton(onClick = { contactForNicknameDialog = null }) {
                        Text("Cancel", color = colors.muted)
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactListItem(
    contact: Profile,
    isPeerOnline: Boolean = false,
    isSameWifi: Boolean = false,
    lastSeenAt: Long? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = appColors
    val hasCustomNickname = !contact.nickname.isNullOrBlank()

    val presenceText = when {
        isPeerOnline -> "online"
        lastSeenAt != null && lastSeenAt > 0 -> "last seen ${formatLastSeenContactTime(lastSeenAt)}"
        !contact.description.isNullOrBlank() -> contact.description
        else -> "offline"
    }
    val subtitleText = if (hasCustomNickname) {
        "@${contact.username} • $presenceText"
    } else {
        presenceText
    }
    val subtitleColor = if (isPeerOnline) colors.positive else colors.muted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle(
            name = contact.displayName,
            avatarUri = contact.avatarUri,
            size = 48.dp,
            showOnlineStatus = true,
            isOnline = isPeerOnline,
            isSameWifi = isSameWifi
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colors.txt,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = subtitleText,
                fontSize = 13.sp,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val contactTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())
private val contactDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
    .withZone(ZoneId.systemDefault())

private fun formatLastSeenContactTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    val instant = Instant.ofEpochMilli(timestamp)
    return when {
        diffMs < 60_000L -> "just now"
        diffMs < 3600_000L -> "${diffMs / 60_000L}m ago"
        diffMs < 86400_000L -> "at " + contactTimeFormat.format(instant)
        else -> contactDateFormat.format(instant)
    }
}
