package com.chat.app.ui.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chat.app.Screen
import com.chat.app.data.Profile
import com.chat.app.ui.theme.appColors
import java.io.File


@Composable
fun AvatarCircle(
    name: String,
    avatarUri: String?,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    showOnlineStatus: Boolean = false,
    isOnline: Boolean = true,
    isSameWifi: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = appColors
    val context = LocalContext.current
    val initial = remember(name) {
        name.trim().take(1).uppercase()
    }

    val model = remember(avatarUri) {
        if (avatarUri.isNullOrBlank()) null
        else if (avatarUri.startsWith("content://") || avatarUri.startsWith("http://") || avatarUri.startsWith("https://") || avatarUri.startsWith("file://")) {
            avatarUri
        } else if (avatarUri.startsWith("/")) {
            val f = File(avatarUri)
            if (f.exists()) f else null
        } else {
            val f = File(context.filesDir, avatarUri)
            if (f.exists()) f else null
        }
    }

    val avatarGradients = remember {
        listOf(
            listOf(androidx.compose.ui.graphics.Color(0xFF4FACFE), androidx.compose.ui.graphics.Color(0xFF00F2FE)), // Blue
            listOf(androidx.compose.ui.graphics.Color(0xFFFA709A), androidx.compose.ui.graphics.Color(0xFFFEE140)), // Pink-Yellow
            listOf(androidx.compose.ui.graphics.Color(0xFF667EEA), androidx.compose.ui.graphics.Color(0xFF764BA2)), // Purple-Indigo
            listOf(androidx.compose.ui.graphics.Color(0xFF11998E), androidx.compose.ui.graphics.Color(0xFF38EF7D)), // Emerald
            listOf(androidx.compose.ui.graphics.Color(0xFFFF5858), androidx.compose.ui.graphics.Color(0xFFF857A6)), // Coral-Rose
            listOf(androidx.compose.ui.graphics.Color(0xFFFFA726), androidx.compose.ui.graphics.Color(0xFFFF7043)), // Amber-Orange
            listOf(androidx.compose.ui.graphics.Color(0xFF26C6DA), androidx.compose.ui.graphics.Color(0xFF00ACC1)), // Cyan-Teal
            listOf(androidx.compose.ui.graphics.Color(0xFF8E2DE2), androidx.compose.ui.graphics.Color(0xFF4A00E0)), // Deep Violet
            listOf(androidx.compose.ui.graphics.Color(0xFF00C9FF), androidx.compose.ui.graphics.Color(0xFF92FE9D))  // Neon Mint
        )
    }
    val gradient = remember(name) {
        val index = (Math.abs(name.hashCode())) % avatarGradients.size
        androidx.compose.ui.graphics.Brush.linearGradient(avatarGradients[index])
    }

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = name,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(gradient),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial.ifEmpty { "?" },
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.42f).sp
                )
            }
        }

        if (showOnlineStatus) {
            val isWifiBadge = isSameWifi && isOnline
            val badgeSize = (size.value * (if (isWifiBadge) 0.36f else 0.28f)).coerceAtLeast(if (isWifiBadge) 14f else 10f).dp
            val dotColor = if (isOnline) colors.positive else colors.muted.copy(alpha = 0.5f)
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .padding(if (isWifiBadge) 1.dp else 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (isWifiBadge) colors.card else dotColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isWifiBadge) {
                        Icon(
                            imageVector = AppIcons.Wifi,
                            contentDescription = "Same Wi-Fi",
                            tint = colors.accent,
                            modifier = Modifier.size((badgeSize.value * 0.65f).dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UnreadBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return
    val colors = appColors
    val text = if (count > 99) "99+" else count.toString()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.divider),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = colors.txt,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun TelegramFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val colors = appColors
    FloatingActionButton(
        onClick = onClick,
        containerColor = colors.card,
        contentColor = colors.txt,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(6.dp),
        modifier = modifier.size(56.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.txt,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun TelegramBottomNavbar(
    currentScreen: Screen,
    totalUnreadCount: Int = 0,
    selfProfile: Profile? = null,
    onScreenChange: (Screen) -> Unit,
    onPencilClick: () -> Unit = {},
    onAddPersonClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = appColors

    val isMediaStorageScreen = currentScreen is Screen.MediaStorage

    val centerIcon = when (currentScreen) {
        is Screen.Contacts -> AppIcons.PersonAdd
        is Screen.MediaStorage -> AppIcons.Profile
        is Screen.Settings -> AppIcons.Edit
        else -> AppIcons.Edit
    }

    val centerAction = when (currentScreen) {
        is Screen.Contacts -> onAddPersonClick
        is Screen.MediaStorage -> onProfileClick
        is Screen.Settings -> onProfileClick
        else -> onPencilClick
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Pill Shaped Floating Surface
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            color = colors.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, colors.divider)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Chats
                NavPillTabItem(
                    title = "Chats",
                    icon = AppIcons.Chats,
                    isSelected = currentScreen is Screen.ChatList,
                    badgeCount = totalUnreadCount,
                    onClick = { onScreenChange(Screen.ChatList) },
                    modifier = Modifier.weight(1f)
                )

                // 2. Contacts
                NavPillTabItem(
                    title = "Contacts",
                    icon = AppIcons.Contacts,
                    isSelected = currentScreen is Screen.Contacts,
                    onClick = { onScreenChange(Screen.Contacts) },
                    modifier = Modifier.weight(1f)
                )

                // 3. Dynamic Center Action Button - Stable fixed width container to prevent layout resizing
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = centerAction,
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        color = colors.card,
                        border = BorderStroke(1.dp, colors.divider),
                        shadowElevation = 4.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isMediaStorageScreen) {
                                AvatarCircle(
                                    name = selfProfile?.username ?: "Me",
                                    avatarUri = selfProfile?.avatarUri,
                                    size = 46.dp
                                )
                            } else {
                                AnimatedContent(
                                    targetState = centerIcon,
                                    transitionSpec = { scaleIn(animationSpec = tween(150)) + fadeIn(animationSpec = tween(150)) togetherWith scaleOut(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150)) },
                                    label = "centerIconAnim"
                                ) { icon ->
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "Center Action",
                                        tint = colors.txt,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Media Storage
                NavPillTabItem(
                    title = "Media",
                    icon = AppIcons.Storage,
                    isSelected = currentScreen is Screen.MediaStorage,
                    onClick = { onScreenChange(Screen.MediaStorage) },
                    modifier = Modifier.weight(1f)
                )

                // 5. Settings
                NavPillTabItem(
                    title = "Settings",
                    icon = AppIcons.Settings,
                    isSelected = currentScreen is Screen.Settings,
                    onClick = { onScreenChange(Screen.Settings) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavPillTabItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors
    val activeColor = colors.accent
    val inactiveColor = colors.muted

    val pillBgColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent.copy(alpha = 0.20f) else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "pillBgColor"
    )

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "iconScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = pillBgColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isSelected) activeColor else inactiveColor,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    )
                }
            }

            if (badgeCount > 0) {
                UnreadBadge(
                    count = badgeCount,
                    modifier = Modifier.offset(x = 4.dp, y = (-2).dp)
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) activeColor else inactiveColor
        )
    }
}
