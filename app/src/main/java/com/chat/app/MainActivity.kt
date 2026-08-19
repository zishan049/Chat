package com.chat.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.chat.app.data.Chat
import com.chat.app.data.MediaType
import com.chat.app.ui.screens.*
import com.chat.app.ui.components.*
import com.chat.app.ui.theme.*
import com.chat.app.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

sealed class Screen {
    object ChatList : Screen()
    data class ChatRoom(val chatId: String, val chatName: String, val avatarUri: String?) : Screen()
    object Settings : Screen()
    object NewChat : Screen()
    object Contacts : Screen()
    data class AddContact(val initialTab: Int = 0) : Screen()
    data class Profile(val profileId: String, val isSelf: Boolean) : Screen()
    object MediaStorage : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.chat.app.telemetry.AppTelemetry.init(this)
        
        // Remote Broadcast Receiver for Instant PC Loger Diagnostics
        val testReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val ctx = context ?: this@MainActivity
                when (action) {
                    "com.chat.app.START_STRESS_TEST" -> {
                        val iterations = intent.getIntExtra("iterations", 300)
                        com.chat.app.telemetry.AppDiagnosticsTestRunner.runStressTest(ctx, iterations)
                    }
                    "com.chat.app.CANCEL_STRESS_TEST" -> {
                        com.chat.app.telemetry.AppDiagnosticsTestRunner.cancelTest()
                    }
                    "com.chat.app.START_SECURITY_TEST" -> {
                        com.chat.app.telemetry.AppDiagnosticsTestRunner.runSecurityAudit(ctx)
                    }
                    "com.chat.app.DISMISS_TEST_OVERLAY" -> {
                        com.chat.app.telemetry.AppDiagnosticsTestRunner.dismissOverlay()
                    }
                    "com.chat.app.HIDE_TEST_OVERLAY" -> {
                        com.chat.app.telemetry.AppDiagnosticsTestRunner.hideOverlay()
                    }
                    "com.chat.app.SHOW_TEST_OVERLAY" -> {
                        com.chat.app.telemetry.AppDiagnosticsTestRunner.showOverlay()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("com.chat.app.START_STRESS_TEST")
            addAction("com.chat.app.CANCEL_STRESS_TEST")
            addAction("com.chat.app.START_SECURITY_TEST")
            addAction("com.chat.app.DISMISS_TEST_OVERLAY")
            addAction("com.chat.app.HIDE_TEST_OVERLAY")
            addAction("com.chat.app.SHOW_TEST_OVERLAY")
        }
        ContextCompat.registerReceiver(this, testReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()
            val isHaptics by viewModel.isHaptics.collectAsState()
            val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()

            ChatTheme(darkTheme = isDark) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!isOnboardingCompleted) {
                        OnboardingScreen(
                            onCompleteOnboarding = { username, avatarUri, bio, age ->
                                viewModel.completeOnboarding(username, avatarUri, bio, age)
                            }
                        )
                    } else {
                        ChatApp(
                            viewModel = viewModel,
                            isDark = isDark,
                            isHaptics = isHaptics,
                            onDarkToggle = viewModel::setDarkMode,
                            onHapticsToggle = viewModel::setHaptics,
                        )
                    }

                    // Live Diagnostics & Stress Test Modal Overlay
                    com.chat.app.ui.components.DiagnosticTestOverlay()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val levelName = when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
            android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
            android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
            android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
            else -> "LEVEL_$level"
        }
        com.chat.app.telemetry.AppTelemetry.logTrimMemory(level, levelName, "Trimmed caches for level $levelName")
        com.chat.app.utils.AppMemoryManager.onTrimMemory(level)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val chatId = intent?.getStringExtra("chatId")
        val chatName = intent?.getStringExtra("chatName")
        val avatarUri = intent?.getStringExtra("avatarUri")
        if (chatId != null && chatName != null) {
            viewModel.openChat(chatId)
            viewModel.navigateTo(Screen.ChatRoom(chatId, chatName, avatarUri))
        }
    }
}

@Composable
fun ChatApp(
    viewModel: ChatViewModel,
    isDark: Boolean,
    isHaptics: Boolean,
    onDarkToggle: (Boolean) -> Unit,
    onHapticsToggle: (Boolean) -> Unit,
) {
    val chats       by viewModel.chats.collectAsState()
    val messages    by viewModel.messages.collectAsState()
    val selfProfile by viewModel.selfProfile.collectAsState()
    val storage     by viewModel.storageBytes.collectAsState()
    val storageBreakdown by viewModel.storageBreakdown.collectAsState()
    val contacts    by viewModel.contacts.collectAsState()
    val mediaMsgs   by viewModel.allMediaMessages.collectAsState()
    val isSound     by viewModel.isSound.collectAsState()
    val isAutoDownload by viewModel.isAutoDownload.collectAsState()
    val isMessagePreview by viewModel.isMessagePreview.collectAsState()
    val peerPresence by viewModel.peerPresence.collectAsState()
    val colors      = appColors

    var screen by remember { mutableStateOf<Screen>(Screen.ChatList) }

    LaunchedEffect(Unit) {
        viewModel.navigationTarget.collect { target ->
            screen = target
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(Unit) {
            val permission = "android.permission.POST_NOTIFICATIONS"
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(permission)
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val coroutineScope = rememberCoroutineScope()

    val isNotChatList = screen !is Screen.ChatList
    BackHandler(enabled = isNotChatList) {
        when (val currentScreen = screen) {
            is Screen.MediaStorage -> screen = Screen.ChatList
            is Screen.Contacts -> screen = Screen.ChatList
            is Screen.Settings -> screen = Screen.ChatList
            is Screen.Profile -> {
                if (currentScreen.isSelf) {
                    screen = Screen.Settings
                } else {
                    val activeId = viewModel.activeChatId.value
                    if (activeId != null) {
                        val activeChat = chats.firstOrNull { it.id == activeId }
                        if (activeChat != null) {
                            screen = Screen.ChatRoom(activeId, activeChat.name, activeChat.avatarUri)
                        } else {
                            screen = Screen.ChatList
                        }
                    } else {
                        screen = Screen.ChatList
                    }
                }
            }
            is Screen.ChatRoom -> {
                viewModel.closeChat()
                screen = Screen.ChatList
            }
            is Screen.AddContact -> screen = Screen.Contacts
            else -> screen = Screen.ChatList
        }
    }

    val notificationFlow = viewModel.incomingNotification
    var activeNotification by remember { mutableStateOf<com.chat.app.viewmodel.InAppNotification?>(null) }
    val scannedPeerProfile by viewModel.scannedPeerProfile.collectAsState()
    
    LaunchedEffect(Unit) {
        notificationFlow.collect { notif ->
            activeNotification = notif
            delay(4000)
            activeNotification = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val isDetailScreen = screen !is Screen.ChatList && screen !is Screen.Contacts && screen !is Screen.MediaStorage && screen !is Screen.Settings
            Box(modifier = Modifier.weight(1f)) {
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.collect { settledPage ->
                        val targetTab = when (settledPage) {
                            0 -> Screen.ChatList
                            1 -> Screen.Contacts
                            2 -> Screen.MediaStorage
                            3 -> Screen.Settings
                            else -> Screen.ChatList
                        }
                        val isTabScreen = screen is Screen.ChatList || screen is Screen.Contacts || screen is Screen.MediaStorage || screen is Screen.Settings
                        if (isTabScreen && screen != targetTab) {
                            screen = targetTab
                        }
                    }
                }
                
                LaunchedEffect(screen) {
                    val screenName = when (screen) {
                        is Screen.ChatList -> "ChatListScreen"
                        is Screen.ChatRoom -> "ChatRoomScreen (${(screen as Screen.ChatRoom).chatName})"
                        is Screen.Contacts -> "ContactsScreen"
                        is Screen.AddContact -> "AddContactScreen"
                        is Screen.MediaStorage -> "MediaStorageScreen"
                        is Screen.Profile -> "ProfileScreen (${if ((screen as Screen.Profile).isSelf) "Self" else "Peer"})"
                        is Screen.Settings -> "SettingsScreen"
                        is Screen.NewChat -> "NewChatScreen"
                    }
                    com.chat.app.telemetry.AppTelemetry.logScreenTransition(screenName, mapOf("screenObject" to screen.toString()))

                    val targetPage = when (screen) {
                        is Screen.ChatList -> 0
                        is Screen.Contacts -> 1
                        is Screen.MediaStorage -> 2
                        is Screen.Settings -> 3
                        else -> null
                    }
                    if (targetPage != null && pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
                        pagerState.scrollToPage(targetPage)
                    }
                }
                
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> ChatListScreen(
                            chats = chats,
                            contacts = contacts,
                            peerPresence = peerPresence,
                            selfProfile = selfProfile,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            onChatClick = { chatId ->
                                val chat = chats.firstOrNull { it.id == chatId } ?: return@ChatListScreen
                                viewModel.openChat(chatId)
                                screen = Screen.ChatRoom(chatId, chat.name, chat.avatarUri)
                            },
                            onContactClick = { contact ->
                                val existingChat = chats.firstOrNull { it.name == contact.username }
                                val chatId = existingChat?.id ?: viewModel.createChat(contact.username, contact.avatarUri)
                                viewModel.openChat(chatId)
                                screen = Screen.ChatRoom(chatId, contact.username, contact.avatarUri)
                            },
                            onProfileClick = { screen = Screen.Profile(selfProfile?.id ?: "", true) },
                            onQrClick = { screen = Screen.AddContact(0) },
                            onDeleteChat = viewModel::deleteChat,
                            onRenameChat = viewModel::renameChat,
                            onToggleBlockChat = viewModel::toggleBlockChat,
                            onSetNickname = viewModel::setContactNickname,
                            onViewProfile = { chatId -> screen = Screen.Profile(chatId, false) },
                        )
                        1 -> ContactsScreen(
                            contacts = contacts,
                            peerPresence = peerPresence,
                            selfProfile = selfProfile,
                            onContactClick = { contact ->
                                val existingChat = chats.firstOrNull { it.name == contact.username }
                                val chatId = existingChat?.id ?: viewModel.createChat(contact.username, contact.avatarUri)
                                viewModel.openChat(chatId)
                                screen = Screen.ChatRoom(chatId, contact.username, contact.avatarUri)
                            },
                            onDeleteContact = { contact ->
                                viewModel.deleteContact(contact)
                            },
                            onNavigateToAddContact = {
                                screen = Screen.AddContact(0)
                            },
                            onProfileClick = { screen = Screen.Profile(selfProfile?.id ?: "", true) },
                            onSetNickname = viewModel::setContactNickname
                        )
                        2 -> {
                            MediaStorageScreen(
                                mediaMessages = mediaMsgs,
                                storageBreakdown = storageBreakdown,
                                onBack = { 
                                    coroutineScope.launch { pagerState.scrollToPage(0) }
                                    screen = Screen.ChatList 
                                },
                                onDeleteMessage = viewModel::deleteMessage,
                                onCleanOrphans = viewModel::cleanOrphanMedia,
                                onClearCategory = viewModel::clearCategoryMedia,
                            )
                        }
                        3 -> {
                            SettingsScreen(
                                selfProfile = selfProfile,
                                storageBytes = storage,
                                isDarkMode = isDark,
                                isHaptics = isHaptics,
                                isSound = isSound,
                                isAutoDownload = isAutoDownload,
                                isMessagePreview = isMessagePreview,
                                onDarkModeToggle = onDarkToggle,
                                onHapticsToggle = onHapticsToggle,
                                onSoundToggle = viewModel::setSound,
                                onAutoDownloadToggle = viewModel::setAutoDownloadMedia,
                                onMessagePreviewToggle = viewModel::setMessagePreview,
                                onBack = { 
                                    coroutineScope.launch { pagerState.scrollToPage(0) }
                                    screen = Screen.ChatList 
                                },
                                onEditProfile = { screen = Screen.Profile(selfProfile?.id ?: "", true) },
                                onMediaStorageClick = { 
                                    coroutineScope.launch { pagerState.scrollToPage(2) }
                                    screen = Screen.MediaStorage 
                                },
                                onDeleteAllChats = { chats.forEach { viewModel.deleteChat(it.id) } },
                                onDeleteProfileAndReset = viewModel::deleteProfileAndResetApp
                            )
                        }
                    }
                }

                if (isDetailScreen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.bg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        when (val currentScreen = screen) {
                            is Screen.ChatRoom -> {
                                val currentChat = remember(chats, currentScreen.chatId) {
                                    chats.firstOrNull { it.id == currentScreen.chatId }
                                }
                                val isBlocked = currentChat?.isBlocked == true
                                val isTypingMap by viewModel.isPeerTyping.collectAsState()
                                val isTyping = isTypingMap[currentScreen.chatId] == true
                                val isPeerOnline = peerPresence[currentScreen.chatId]?.isOnline == true
                                val isSameWifi = peerPresence[currentScreen.chatId]?.isSameWifi == true
                                val peerLastSeen = peerPresence[currentScreen.chatId]?.lastSeenAt
                                    ?: remember(contacts, currentScreen.chatId) {
                                        contacts.firstOrNull { it.id == currentScreen.chatId }?.lastSeenAt
                                    }
                                
                                ChatRoomScreen(
                                    chatName = currentChat?.name ?: currentScreen.chatName,
                                    chatAvatarUri = currentChat?.avatarUri ?: currentScreen.avatarUri,
                                    messages = messages,
                                    selfId = selfProfile?.id ?: "",
                                    isBlocked = isBlocked,
                                    isPeerTyping = isTyping,
                                    isPeerOnline = isPeerOnline,
                                    isSameWifi = isSameWifi,
                                    peerLastSeenAt = peerLastSeen,
                                    onBack = { viewModel.closeChat(); screen = Screen.ChatList },
                                    onSendText = viewModel::sendTextMessage,
                                    onSendMedia = viewModel::sendMediaMessage,
                                    onDeleteMessage = viewModel::deleteMessage,
                                    onRetryMessage = viewModel::retryFailedMessage,
                                    onEditMessage = viewModel::editMessage,
                                    onViewProfile = { screen = Screen.Profile(currentScreen.chatId, false) },
                                    onUnblock = { viewModel.toggleBlockChat(currentScreen.chatId) }
                                )
                            }
                            is Screen.Profile -> {
                                LaunchedEffect(currentScreen.profileId) {
                                    if (!currentScreen.isSelf) {
                                        viewModel.probePeer(currentScreen.profileId)
                                    }
                                }
                                val profile = if (currentScreen.isSelf) {
                                    selfProfile
                                } else {
                                    contacts.firstOrNull { it.id == currentScreen.profileId }
                                        ?: chats.firstOrNull { it.id == currentScreen.profileId }?.let {
                                            com.chat.app.data.Profile(
                                                id = it.id,
                                                username = it.name,
                                                avatarUri = it.avatarUri,
                                                isBlocked = it.isBlocked
                                            )
                                        }
                                }
                                val isPeerOnline = if (currentScreen.isSelf) true else peerPresence[currentScreen.profileId]?.isOnline == true
                                val isSameWifi = if (currentScreen.isSelf) false else (peerPresence[currentScreen.profileId]?.isSameWifi == true)
                                val peerLastSeen = if (currentScreen.isSelf) null else (peerPresence[currentScreen.profileId]?.lastSeenAt ?: profile?.lastSeenAt)
                                
                                ProfileScreen(
                                    profile = profile,
                                    isSelf = currentScreen.isSelf,
                                    isPeerOnline = isPeerOnline,
                                    isSameWifi = isSameWifi,
                                    peerLastSeenAt = peerLastSeen,
                                    onBack = { 
                                        val activeId = viewModel.activeChatId.value
                                        if (activeId != null) {
                                            val activeChat = chats.firstOrNull { it.id == activeId }
                                            if (activeChat != null) {
                                                screen = Screen.ChatRoom(activeId, activeChat.name, activeChat.avatarUri)
                                            } else {
                                                screen = Screen.ChatList
                                            }
                                        } else {
                                            screen = Screen.Settings
                                        }
                                    },
                                    onUpdateProfile = viewModel::updateSelfProfile,
                                    onToggleBlock = { viewModel.toggleBlockChat(currentScreen.profileId) },
                                    onDeleteAllChats = {
                                        val chatId = currentScreen.profileId
                                        viewModel.deleteChat(chatId)
                                        val matchingChat = chats.firstOrNull { it.id == chatId || (profile != null && it.name == profile.username) }
                                        if (matchingChat != null && matchingChat.id != chatId) {
                                            viewModel.deleteChat(matchingChat.id)
                                        }
                                        viewModel.closeChat()
                                        screen = Screen.ChatList
                                    },
                                    onDeleteContact = {
                                        if (profile != null) {
                                            viewModel.deleteContact(profile)
                                        } else {
                                            viewModel.deleteContact(currentScreen.profileId)
                                        }
                                        viewModel.closeChat()
                                        screen = Screen.ChatList
                                    },
                                    onSetNickname = { nickname ->
                                        viewModel.setContactNickname(currentScreen.profileId, nickname)
                                    }
                                )
                            }
                            is Screen.AddContact -> com.chat.app.ui.screens.AddContactScreen(
                                selfProfile = selfProfile,
                                initialTab = currentScreen.initialTab,
                                onBack = { screen = Screen.Contacts },
                                onAddContact = { id, name, age, description -> viewModel.addContact(id, name, null, age, description) },
                                onQrScanned = viewModel::onQrScanned
                            )
                            else -> {}
                        }
                    }
                }
            } // end weight Box

            val totalUnreadCount = remember(chats) { chats.sumOf { it.unreadCount } }
            val currentNavbarScreen by remember(screen, pagerState) {
                derivedStateOf {
                    if (screen is Screen.ChatList || screen is Screen.Contacts || screen is Screen.MediaStorage || screen is Screen.Settings) {
                        when (pagerState.targetPage) {
                            0 -> Screen.ChatList
                            1 -> Screen.Contacts
                            2 -> Screen.MediaStorage
                            3 -> Screen.Settings
                            else -> Screen.ChatList
                        }
                    } else {
                        screen
                    }
                }
            }

            if (!isDetailScreen) {
                TelegramBottomNavbar(
                    currentScreen = currentNavbarScreen,
                    totalUnreadCount = totalUnreadCount,
                    selfProfile = selfProfile,
                    onScreenChange = { targetScreen ->
                        val targetPage = when (targetScreen) {
                            is Screen.ChatList -> 0
                            is Screen.Contacts -> 1
                            is Screen.MediaStorage -> 2
                            is Screen.Settings -> 3
                            else -> null
                        }
                        if (targetPage != null) {
                            if (pagerState.currentPage != targetPage) {
                                coroutineScope.launch {
                                    pagerState.scrollToPage(targetPage)
                                }
                            }
                            screen = targetScreen
                        } else {
                            screen = targetScreen
                        }
                    },
                    onPencilClick = {
                        coroutineScope.launch {
                            pagerState.scrollToPage(1)
                        }
                        screen = Screen.Contacts
                    },
                    onAddPersonClick = {
                        screen = Screen.AddContact(0)
                    },
                    onProfileClick = {
                        screen = Screen.Profile(selfProfile?.id ?: "", true)
                    }
                )
            }
        } // end Column

        // Notification Banner
        AnimatedVisibility(
            visible = activeNotification != null,
            enter = slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val notif = activeNotification
            if (notif != null) {
                val notifContact = contacts.firstOrNull { it.id == notif.chatId }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            viewModel.openChat(notif.chatId)
                            screen = Screen.ChatRoom(notif.chatId, notif.senderName, notifContact?.avatarUri)
                            activeNotification = null
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = colors.card,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarCircle(
                            name = notif.senderName,
                            avatarUri = notifContact?.avatarUri,
                            size = 40.dp
                        )

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                notif.senderName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.txt
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                notif.text,
                                fontSize = 12.sp,
                                color = colors.muted,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { activeNotification = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                AppIcons.Close,
                                contentDescription = "Dismiss",
                                tint = colors.muted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal
    val activeScannedPeer = scannedPeerProfile
    if (activeScannedPeer != null) {
        com.chat.app.ui.components.ScannedProfileModal(
            scannedData = activeScannedPeer,
            onStartChat = { peerId ->
                viewModel.clearScannedPeerProfile()
                val targetName = activeScannedPeer.name.ifBlank { "Contact" }
                // Do NOT pass the raw QR avatarUri here — it's a local path from the
                // OTHER device and would overwrite the valid generated avatar that
                // addContact already saved. Passing null lets createOrUpdateChat keep
                // the existing avatar.
                viewModel.createChat(peerId, targetName, null)
                viewModel.openChat(peerId)
                // Look up the real avatar from the contacts list
                val savedAvatar = contacts.firstOrNull { it.id == peerId }?.avatarUri
                screen = Screen.ChatRoom(peerId, targetName, savedAvatar)
            },
            onDismiss = {
                viewModel.clearScannedPeerProfile()
            }
        )
    }

    // Live Diagnostics & Stress Test Modal Overlay (Controlled via PC Loger)
    com.chat.app.ui.components.DiagnosticTestOverlay()
}
