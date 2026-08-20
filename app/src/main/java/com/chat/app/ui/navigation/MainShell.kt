package com.chat.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat.app.contacts.presentation.ContactsScreen
import com.chat.app.domain.model.Identity
import com.chat.app.messaging.presentation.chatlist.ChatListScreen
import com.chat.app.profile.presentation.ProfileScreen
import com.chat.app.settings.presentation.SettingsScreen
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MainShell(
    selfIdentity: Identity?,
    activePort: Int,
    onNavigateToChat: (String) -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToMediaStorage: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val tabs = remember { listOf("chats", "contacts", "profile", "settings") }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    var showActionSheet by remember { mutableStateOf(false) }
    var showMyQrModal by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 1. Android System Navbar Back Button: Dismiss My QR Modal if open
    BackHandler(enabled = showMyQrModal) {
        showMyQrModal = false
    }

    // 2. Android System Navbar Back Button: Dismiss Quick Action Sheet if open
    BackHandler(enabled = showActionSheet && !showMyQrModal) {
        showActionSheet = false
    }

    // 3. Android System Navbar Back Button: Return to Chats Tab (Tab 0) if on any secondary tab
    BackHandler(enabled = pagerState.currentPage != 0 && !showActionSheet && !showMyQrModal) {
        scope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    fun navigateToTab(route: String) {
        val index = tabs.indexOf(route)
        if (index >= 0) {
            scope.launch {
                pagerState.animateScrollToPage(index)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // Swipeable Screens Pager (Left/Right Swipe support)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> tabs[page] }
        ) { page ->
            when (tabs[page]) {
                "chats" -> {
                    ChatListScreen(
                        onNavigateToPairing = onNavigateToPairing,
                        onNavigateToContacts = { navigateToTab("contacts") },
                        onNavigateToSettings = { navigateToTab("settings") },
                        onNavigateToProfile = { navigateToTab("profile") },
                        onShowMyQr = { showMyQrModal = true },
                        onConversationClick = onNavigateToChat
                    )
                }
                "contacts" -> {
                    ContactsScreen(
                        onNavigateBack = { navigateToTab("chats") },
                        onNavigateToPairing = onNavigateToPairing,
                        onContactSelected = onNavigateToChat,
                        onNavigateToProfile = { navigateToTab("profile") }
                    )
                }
                "profile" -> {
                    ProfileScreen(
                        onNavigateBack = { navigateToTab("chats") },
                        onNavigateToSettings = { navigateToTab("settings") }
                    )
                }
                "settings" -> {
                    SettingsScreen(
                        onNavigateBack = { navigateToTab("chats") },
                        onNavigateToProfile = { navigateToTab("profile") },
                        onNavigateToMediaStorage = onNavigateToMediaStorage,
                        onAccountDeleted = onAccountDeleted
                    )
                }
            }
        }

        // Persistent Floating Glass Bottom Navigation Bar
        GlassNavigationBar(
            currentRoute = tabs[pagerState.currentPage],
            onNavigate = { route -> navigateToTab(route) },
            onCenterAction = { showActionSheet = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )

        // Quick Actions Bottom Sheet (+)
        if (showActionSheet) {
            QuickActionSheet(
                onDismiss = { showActionSheet = false },
                onStartChat = {
                    showActionSheet = false
                    navigateToTab("contacts")
                },
                onScanQr = {
                    showActionSheet = false
                    onNavigateToPairing()
                },
                onMyQr = {
                    showActionSheet = false
                    showMyQrModal = true
                }
            )
        }

        // My QR Code In-Modal
        if (showMyQrModal) {
            MyQrCodeDialog(
                identity = selfIdentity,
                activePort = activePort,
                onDismiss = { showMyQrModal = false }
            )
        }
    }
}

@Composable
private fun QuickActionSheet(
    onDismiss: () -> Unit,
    onStartChat: () -> Unit,
    onScanQr: () -> Unit,
    onMyQr: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (AppTheme.colors.isDark) 0.65f else 0.40f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(28.dp),
            backgroundColor = AppTheme.colors.surface,
            borderColor = AppGlassBorderBright
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Quick Actions",
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTextPrimary
                    ),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                ActionSheetItem(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = "New Conversation",
                    subtitle = "Message a connected peer",
                    onClick = onStartChat
                )

                ActionSheetItem(
                    icon = Icons.Outlined.QrCodeScanner,
                    title = "Scan QR Code",
                    subtitle = "Pair instantly via camera",
                    onClick = onScanQr
                )

                ActionSheetItem(
                    icon = Icons.Outlined.QrCode,
                    title = "My QR Code",
                    subtitle = "Show your pairing identity code",
                    onClick = onMyQr
                )
            }
        }
    }
}

@Composable
private fun ActionSheetItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    GlassCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = AppGlassLow,
        borderColor = AppGlassBorderSubtle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(AppSurfaceElevated, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = AppTextPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppTextPrimary))
                Text(text = subtitle, style = TextStyle(fontSize = 12.sp, color = AppTextSecondary))
            }
        }
    }
}
