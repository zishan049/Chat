package com.chat.app.contacts.presentation

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chat.app.domain.model.Contact
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.*

@Composable
fun ContactsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onContactSelected: (String) -> Unit,
    onNavigateToProfile: (() -> Unit)? = null,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isSearchExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Contacts",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                )

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

                    GlassIconButton(
                        icon = Icons.Outlined.PersonAdd,
                        onClick = onNavigateToPairing,
                        size = 38.dp,
                        iconSize = 18.dp,
                        contentDescription = "Add Contact"
                    )
                }
            }

            // Search Bar
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                GlassTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = "Search contacts...",
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: My Identity Card (Only when not searching)
                if (state.searchQuery.isBlank()) {
                    item {
                        Column {
                            Text(
                                text = "My Identity",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextTertiary
                                ),
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )

                            GlassCard(
                                onClick = { onNavigateToProfile?.invoke() ?: onNavigateToPairing() },
                                shape = RoundedCornerShape(20.dp),
                                backgroundColor = GlassWhiteLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(
                                        name = state.selfIdentity?.displayName ?: "Me",
                                        avatarUri = state.selfIdentity?.avatarUri,
                                        isOnline = true,
                                        size = 46.dp
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = state.selfIdentity?.displayName ?: "Me",
                                            style = TextStyle(
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                        )
                                        Text(
                                            text = "Tap to view your identity & QR code",
                                            style = TextStyle(
                                                fontSize = 13.sp,
                                                color = TextSecondary
                                            )
                                        )
                                    }

                                    GlassIconButton(
                                        icon = Icons.Outlined.QrCode,
                                        onClick = { onNavigateToProfile?.invoke() ?: onNavigateToPairing() },
                                        size = 36.dp,
                                        iconSize = 18.dp,
                                        backgroundColor = GlassWhiteMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: Connected Contacts
                item {
                    Text(
                        text = if (state.searchQuery.isBlank()) "Connected" else "Results",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextTertiary
                        ),
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }

                if (state.contacts.isEmpty()) {
                    item {
                        GlassSurface(
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = GlassWhiteUltraLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (state.searchQuery.isBlank()) "No contacts yet." else "No matching contacts.",
                                    style = TextStyle(fontSize = 14.sp, color = TextSecondary)
                                )
                                if (state.searchQuery.isBlank()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    GlassButton(
                                        text = "Add Contact",
                                        isPrimary = true,
                                        onClick = onNavigateToPairing,
                                        modifier = Modifier.width(160.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(state.contacts, key = { it.id }) { contact ->
                        val isPeerOnline = state.presenceMap[contact.id]?.isOnline == true
                        GlassContactRow(
                            contact = contact,
                            isOnline = isPeerOnline,
                            onClick = { onContactSelected(contact.id) },
                            onEditNickname = { viewModel.startEditNickname(contact) },
                            onToggleBlock = { viewModel.toggleBlockContact(contact) },
                            onDelete = { viewModel.deleteContact(contact) }
                        )
                    }
                }

                // Section 3: Invite to Chat (Only when not searching)
                if (state.searchQuery.isBlank()) {
                    item {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "Invite to Chat",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextTertiary
                                ),
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )

                            GlassCard(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Join me on Chat for private, zero-trust messaging: https://chat.app/join")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Invite to Chat"))
                                },
                                shape = RoundedCornerShape(20.dp),
                                backgroundColor = GlassWhiteLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(GlassWhiteMedium),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PersonAddAlt1,
                                            contentDescription = null,
                                            tint = TextPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Invite a Friend",
                                            style = TextStyle(
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                        )
                                        Text(
                                            text = "Share your QR or invite link",
                                            style = TextStyle(
                                                fontSize = 13.sp,
                                                color = TextSecondary
                                            )
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = "Share",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Edit Nickname Dialog
        state.editingNicknameForContact?.let { contact ->
            Dialog(onDismissRequest = viewModel::cancelEditNickname) {
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
                        Text(
                            text = "Set Nickname",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        GlassTextField(
                            value = state.nicknameInput,
                            onValueChange = viewModel::onNicknameInputChanged,
                            label = "Nickname for ${contact.displayName}",
                            placeholder = contact.displayName,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { viewModel.saveNickname() })
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = viewModel::cancelEditNickname) {
                                Text("Cancel", color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            GlassButton(
                                text = "Save",
                                isPrimary = true,
                                onClick = viewModel::saveNickname,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassContactRow(
    contact: Contact,
    isOnline: Boolean,
    onClick: () -> Unit,
    onEditNickname: () -> Unit,
    onToggleBlock: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    GlassCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = GlassWhiteLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = contact.displayName,
                avatarUri = contact.avatarUri,
                isOnline = isOnline,
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.effectiveName,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isOnline) "Online" else "Offline",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = if (isOnline) AccentGreen else TextTertiary
                    )
                )
            }

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
                        .background(Color(0xF014151B))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Set Nickname") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = TextPrimary) },
                        onClick = {
                            showMenu = false
                            onEditNickname()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (contact.isBlocked) "Unblock" else "Block") },
                        leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null, tint = TextPrimary) },
                        onClick = {
                            showMenu = false
                            onToggleBlock()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Contact", color = AccentDestructive) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = AccentDestructive) },
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

