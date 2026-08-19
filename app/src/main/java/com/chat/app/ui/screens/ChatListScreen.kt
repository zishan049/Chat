package com.chat.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.chat.app.data.Chat
import com.chat.app.data.Profile
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.appColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    chats: List<Chat>,
    contacts: List<Profile> = emptyList(),
    peerPresence: Map<String, com.chat.app.utils.PeerPresence> = emptyMap(),
    selfProfile: Profile? = null,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onChatClick: (String) -> Unit,
    onContactClick: (Profile) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onQrClick: () -> Unit = {},
    onDeleteChat: (String) -> Unit,
    onRenameChat: (String, String) -> Unit,
    onToggleBlockChat: (String) -> Unit,
    onSetNickname: ((String, String?) -> Unit)? = null,
    onViewProfile: ((String) -> Unit)? = null,
) {
    val colors = appColors

    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats
        else chats.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredContacts = remember(contacts, searchQuery, filteredChats) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val existingChatNames = HashSet<String>(filteredChats.size).apply {
                filteredChats.forEach { add(it.name.lowercase()) }
            }
            contacts.filter { 
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.username.contains(searchQuery, ignoreCase = true) && 
                !existingChatNames.contains(it.username.lowercase()) 
            }
        }
    }

    var longPressedChat by remember { mutableStateOf<Chat?>(null) }
    var chatForNicknameDialog by remember { mutableStateOf<Chat?>(null) }
    var chatForDeleteDialog by remember { mutableStateOf<Chat?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Header ("Chats", Search, QR Code, Profile Avatar)
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
                            text = "Chats",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = colors.txt
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isSearching = !isSearching }) {
                                Icon(
                                    AppIcons.Search,
                                    contentDescription = "Search",
                                    tint = if (isSearching) colors.accent else colors.muted
                                )
                            }

                            IconButton(onClick = onQrClick) {
                                Icon(
                                    AppIcons.QrCode,
                                    contentDescription = "My QR Code",
                                    tint = colors.accent
                                )
                            }

                            Spacer(Modifier.width(4.dp))

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

                    // Search Bar
                    AnimatedVisibility(
                        visible = isSearching || searchQuery.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = onSearchChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        )
                    }
                }
            }

            // Message List / Empty State / Search Results
            if (filteredChats.isEmpty() && filteredContacts.isEmpty()) {
                EmptyChatState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    if (filteredChats.isNotEmpty()) {
                        items(
                            items = filteredChats,
                            key = { it.id },
                            contentType = { "chat_item" }
                        ) { chat ->
                            val isPeerOnline = peerPresence[chat.id]?.isOnline == true
                            val isSameWifi = peerPresence[chat.id]?.isSameWifi == true
                            ChatListItem(
                                chat = chat,
                                isPeerOnline = isPeerOnline,
                                isSameWifi = isSameWifi,
                                onClick = { onChatClick(chat.id) },
                                onLongClick = { longPressedChat = chat }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 80.dp),
                                color = colors.divider,
                                thickness = 0.5.dp
                            )
                        }
                    }

                    // Contacts in Search
                    if (filteredContacts.isNotEmpty()) {
                        item {
                            Text(
                                text = "CONTACTS",
                                color = colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
                            )
                        }

                        items(
                            items = filteredContacts,
                            key = { "contact_${it.id}" },
                            contentType = { "contact_search_item" }
                        ) { contact ->
                            val isPeerOnline = peerPresence[contact.id]?.isOnline == true
                            val isSameWifi = peerPresence[contact.id]?.isSameWifi == true
                            ContactSearchItem(
                                contact = contact,
                                isPeerOnline = isPeerOnline,
                                isSameWifi = isSameWifi,
                                onClick = { onContactClick(contact) }
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
    }

    // Modernized Long Press Context Menu Modal
    if (longPressedChat != null) {
        val chat = longPressedChat!!
        val matchingContact = remember(contacts, chat.id) {
            contacts.firstOrNull { it.id == chat.id || it.username.equals(chat.name, ignoreCase = true) }
        }
        val isPeerOnline = peerPresence[chat.id]?.isOnline == true
        val hasCustomNickname = !matchingContact?.nickname.isNullOrBlank()

        AlertDialog(
            onDismissRequest = { longPressedChat = null },
            containerColor = colors.card,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isSameWifi = peerPresence[chat.id]?.isSameWifi == true
                    AvatarCircle(
                        name = chat.name,
                        avatarUri = chat.avatarUri,
                        size = 44.dp,
                        showOnlineStatus = true,
                        isOnline = isPeerOnline,
                        isSameWifi = isSameWifi
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chat.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = colors.txt,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val originalName = matchingContact?.username
                        if (hasCustomNickname && originalName != null) {
                            Text(
                                text = "@$originalName (Original)",
                                fontSize = 12.sp,
                                color = colors.muted
                            )
                        } else {
                            Text(
                                text = if (isPeerOnline) "Online" else "Chat Options",
                                fontSize = 12.sp,
                                color = if (isPeerOnline) colors.positive else colors.muted
                            )
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(bottom = 8.dp))

                    // Option 1: Edit / Set Nickname
                    TextButton(
                        onClick = {
                            val selected = chat
                            longPressedChat = null
                            chatForNicknameDialog = selected
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Edit, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasCustomNickname) "Edit Nickname" else "Set Nickname",
                                    color = colors.txt,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (hasCustomNickname) "Current: ${matchingContact?.nickname}" else "Private local nickname for this contact",
                                    color = colors.muted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Option 2: View Profile / User Info
                    TextButton(
                        onClick = {
                            val selectedId = chat.id
                            longPressedChat = null
                            if (onViewProfile != null) {
                                onViewProfile(selectedId)
                            } else {
                                onChatClick(selectedId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Profile, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("View User Info", color = colors.txt, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text("View peer profile, ID and status", color = colors.muted, fontSize = 12.sp)
                            }
                        }
                    }

                    // Option 3: Block / Unblock Chat
                    TextButton(
                        onClick = {
                            longPressedChat = null
                            onToggleBlockChat(chat.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (chat.isBlocked) AppIcons.Check else AppIcons.Lock,
                                contentDescription = null,
                                tint = if (chat.isBlocked) colors.positive else colors.warning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (chat.isBlocked) "Unblock Chat" else "Block Chat",
                                    color = colors.txt,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (chat.isBlocked) "Allow messages from this user" else "Prevent incoming messages",
                                    color = colors.muted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Option 4: Delete Chat
                    TextButton(
                        onClick = {
                            val selected = chat
                            longPressedChat = null
                            chatForDeleteDialog = selected
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Delete, contentDescription = null, tint = colors.danger, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Delete Chat", color = colors.danger, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Clear conversation history and media", color = colors.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { longPressedChat = null }) {
                    Text("Close", color = colors.muted)
                }
            }
        )
    }

    // Nickname Edit Dialog
    if (chatForNicknameDialog != null) {
        val chat = chatForNicknameDialog!!
        val contact = remember(contacts, chat.id) {
            contacts.firstOrNull { it.id == chat.id || it.username.equals(chat.name, ignoreCase = true) }
        }
        val currentNickname = contact?.nickname ?: (if (chat.name != contact?.username) chat.name else "")
        var nicknameInput by remember(chatForNicknameDialog) { mutableStateOf(currentNickname) }

        AlertDialog(
            onDismissRequest = { chatForNicknameDialog = null },
            containerColor = colors.card,
            title = { Text("Contact Nickname", fontWeight = FontWeight.Bold, color = colors.txt) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Assign a private nickname for ${contact?.username ?: chat.name}. This is stored in your local DB and never shared.",
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
                        val targetId = contact?.id ?: chat.id
                        if (onSetNickname != null) {
                            onSetNickname(targetId, trimmed)
                        } else {
                            onRenameChat(chat.id, trimmed ?: contact?.username ?: chat.name)
                        }
                        chatForNicknameDialog = null
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
                    if (!contact?.nickname.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                val targetId = contact?.id ?: chat.id
                                if (onSetNickname != null) {
                                    onSetNickname(targetId, null)
                                } else {
                                    onRenameChat(chat.id, contact?.username ?: chat.name)
                                }
                                chatForNicknameDialog = null
                            }
                        ) {
                            Text("Reset", color = colors.danger)
                        }
                    }
                    TextButton(onClick = { chatForNicknameDialog = null }) {
                        Text("Cancel", color = colors.muted)
                    }
                }
            }
        )
    }

    // Delete Chat Confirmation Dialog
    if (chatForDeleteDialog != null) {
        val chat = chatForDeleteDialog!!
        AlertDialog(
            onDismissRequest = { chatForDeleteDialog = null },
            containerColor = colors.card,
            title = { Text("Delete Chat?", fontWeight = FontWeight.Bold, color = colors.txt) },
            text = {
                Text(
                    text = "Are you sure you want to delete all messages and chat history with ${chat.name}? This action cannot be undone.",
                    color = colors.muted,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteChat(chat.id)
                        chatForDeleteDialog = null
                    }
                ) {
                    Text("Delete Chat", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { chatForDeleteDialog = null }) {
                    Text("Cancel", color = colors.muted)
                }
            }
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search chats and contacts…", color = colors.muted, fontSize = 14.sp) },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(AppIcons.Close, contentDescription = "Clear", tint = colors.muted, modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.divider,
            focusedContainerColor = colors.container,
            unfocusedContainerColor = colors.container,
            focusedTextColor = colors.txt,
            unfocusedTextColor = colors.txt
        ),
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    chat: Chat,
    isPeerOnline: Boolean = false,
    isSameWifi: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = appColors
    val timeFormatted = remember(chat.lastMessageAt) {
        formatChatTime(chat.lastMessageAt)
    }

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
            name = chat.name,
            avatarUri = chat.avatarUri,
            size = 52.dp,
            showOnlineStatus = true,
            isOnline = isPeerOnline,
            isSameWifi = isSameWifi
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.txt,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = timeFormatted,
                    fontSize = 12.sp,
                    color = colors.muted
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.lastMessageSnippet,
                    fontSize = 14.sp,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (chat.unreadCount > 0) {
                    UnreadBadge(count = chat.unreadCount)
                }
            }
        }
    }
}

@Composable
private fun ContactSearchItem(
    contact: Profile,
    isPeerOnline: Boolean = false,
    isSameWifi: Boolean = false,
    onClick: () -> Unit
) {
    val colors = appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                fontSize = 15.sp,
                color = colors.txt
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (!contact.nickname.isNullOrBlank()) "@${contact.username}" else "Tap to start conversation",
                fontSize = 12.sp,
                color = colors.accent
            )
        }
    }
}

@Composable
private fun EmptyChatState() {
    val colors = appColors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                AppIcons.Chats,
                contentDescription = null,
                tint = colors.muted.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No chats found",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colors.txt
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Start a new conversation using the button below",
                fontSize = 13.sp,
                color = colors.muted
            )
        }
    }
}

private val chatTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

private fun formatChatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return chatTimeFormatter.format(Instant.ofEpochMilli(timestamp))
}
