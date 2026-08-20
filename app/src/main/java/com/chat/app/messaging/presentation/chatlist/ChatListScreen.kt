package com.chat.app.messaging.presentation.chatlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chat.app.domain.model.Conversation
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatListScreen(
    onNavigateToPairing: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onShowMyQr: () -> Unit,
    onConversationClick: (String) -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("All") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // Filter conversations based on selected tab
    val displayedConversations = remember(state.conversations, selectedFilter) {
        when (selectedFilter) {
            "Unread" -> state.conversations.filter { it.unreadCount > 0 }
            else -> state.conversations
        }
    }

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
                    .padding(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onNavigateToProfile)
                ) {
                    GlowingCubeLogo(size = 36.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Chats",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(
                        icon = if (isSearchExpanded) Icons.Default.Close else Icons.Outlined.Search,
                        onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) viewModel.onSearchQueryChanged("")
                        },
                        size = 38.dp,
                        iconSize = 18.dp,
                        contentDescription = "Search"
                    )

                    Box {
                        GlassIconButton(
                            icon = Icons.Outlined.MoreVert,
                            onClick = { showMoreMenu = true },
                            size = 38.dp,
                            iconSize = 18.dp,
                            contentDescription = "More Options"
                        )

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier
                                .background(AppTheme.colors.surface)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Scan QR Code",
                                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppTextPrimary)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.QrCodeScanner,
                                        contentDescription = null,
                                        tint = AppTextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigateToPairing()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "My QR Code",
                                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppTextPrimary)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.QrCode,
                                        contentDescription = null,
                                        tint = AppTextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onShowMyQr()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "My Profile",
                                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppTextPrimary)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Badge,
                                        contentDescription = null,
                                        tint = AppTextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigateToProfile()
                                }
                            )
                        }
                    }
                }
            }

            // Search Bar (Expands smoothly)
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                GlassTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = "Search conversations...",
                    leadingIcon = Icons.Outlined.Search,
                    trailingIcon = if (state.searchQuery.isNotEmpty()) {
                        {
                            GlassIconButton(
                                icon = Icons.Default.Clear,
                                onClick = { viewModel.onSearchQueryChanged("") },
                                size = 28.dp,
                                iconSize = 14.dp
                            )
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            // Filter Chips Bar ([All], [Unread], [Contacts], [Groups])
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Unread", "Contacts", "Groups").forEach { filter ->
                    GlassFilterChip(
                        text = filter,
                        isSelected = selectedFilter == filter,
                        onClick = {
                            selectedFilter = filter
                            if (filter == "Contacts") {
                                onNavigateToContacts()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Conversations List / Empty State
            if (displayedConversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        GlowingCubeLogo(size = 72.dp)

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = if (state.searchQuery.isBlank()) "No conversations yet." else "No matching chats.",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (state.searchQuery.isBlank()) {
                            GlassButton(
                                text = "Start Chat",
                                isPrimary = true,
                                onClick = onNavigateToContacts,
                                modifier = Modifier.width(170.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedConversations, key = { it.id }) { conversation ->
                        val isPeerOnline = state.presenceMap[conversation.id]?.isOnline == true
                        GlassConversationRow(
                            conversation = conversation,
                            isOnline = isPeerOnline,
                            onClick = { onConversationClick(conversation.id) },
                            onDelete = { viewModel.deleteConversation(conversation.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassConversationRow(
    conversation: Conversation,
    isOnline: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = remember(conversation.lastMessageAt) {
        val date = Date(conversation.lastMessageAt)
        val now = Date()
        val diff = (now.time - date.time) / (1000 * 60 * 60 * 24)
        when {
            diff == 0L -> timeFormatter.format(date)
            diff == 1L -> "Yesterday"
            diff < 7L -> SimpleDateFormat("EEE", Locale.getDefault()).format(date)
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }

    GlassCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = AppGlassLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contact Avatar with real dynamic presence indicator
            UserAvatar(
                name = conversation.effectiveName,
                avatarUri = conversation.contactAvatarUri,
                isOnline = isOnline,
                size = 50.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.effectiveName,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = formattedTime,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = AppTextTertiary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.lastMessageSnippet.ifBlank { "No messages yet" },
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = if (conversation.unreadCount > 0) AppTextPrimary else AppTextSecondary,
                            fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (conversation.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.textPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.background
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                GlassIconButton(
                    icon = Icons.Outlined.MoreVert,
                    onClick = { showMenu = true },
                    size = 32.dp,
                    iconSize = 16.dp,
                    backgroundColor = Color.Transparent
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(AppTheme.colors.surface)
                        .border(1.dp, AppGlassBorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete Chat", color = AccentDestructive) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = AccentDestructive,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

