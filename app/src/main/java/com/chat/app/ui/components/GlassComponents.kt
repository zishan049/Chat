package com.chat.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chat.app.ui.theme.*

/**
 * Core Glassmorphic Surface Container.
 * Provides translucent frosted depth, a subtle 1px border, and smooth rounded corners.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = AppGlassLow,
    borderColor: Color = AppGlassBorder,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, shape),
        content = content
    )
}

/**
 * Clickable Glass Card with interactive pressed states.
 */
@Composable
fun GlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = AppGlassLow,
    borderColor: Color = AppGlassBorder,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedBg by animateColorAsState(
        targetValue = if (isPressed) AppGlassMedium else backgroundColor,
        label = "glassCardBg"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        label = "glassCardScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(animatedBg)
            .border(1.dp, if (isPressed) AppGlassBorderBright else borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        content = content
    )
}

/**
 * Premium Glass Button with High Contrast & Subtle Glow.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label = "btnScale"
    )

    val colors = AppTheme.colors

    val bgColor = when {
        !enabled -> if (isPrimary) colors.textPrimary.copy(alpha = 0.3f) else AppGlassUltraLow
        isPrimary -> if (isPressed) colors.textSecondary else colors.textPrimary
        else -> if (isPressed) AppGlassMedium else AppGlassLow
    }

    val contentColor = when {
        !enabled -> AppTextTertiary
        isPrimary -> colors.background
        else -> AppTextPrimary
    }

    val borderStroke = when {
        isPrimary -> null
        isPressed -> 1.dp to AppGlassBorderBright
        else -> 1.dp to AppGlassBorder
    }

    Box(
        modifier = modifier
            .scale(scale)
            .height(54.dp)
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (borderStroke != null) Modifier.border(borderStroke.first, borderStroke.second, shape)
                else Modifier
            )
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = text,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        letterSpacing = 0.2.sp
                    )
                )
            }
        }
    }
}

/**
 * Circular / Rounded Glass Icon Button.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    tint: Color = AppTextPrimary,
    backgroundColor: Color = AppGlassLow,
    shape: Shape = CircleShape
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f, label = "iconBtnScale")

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(shape)
            .background(if (isPressed) AppGlassMedium else backgroundColor)
            .border(1.dp, if (isPressed) AppGlassBorderBright else AppGlassBorderSubtle, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Frosted Glass Text Input Field.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    errorText: String? = null,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppTextSecondary
                ),
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (isFocused) AppGlassMedium else AppGlassLow)
                .border(
                    width = 1.dp,
                    color = when {
                        errorText != null -> AccentDestructive
                        isFocused -> AppGlassBorderBright
                        else -> AppGlassBorderSubtle
                    },
                    shape = shape
                )
                .padding(horizontal = 16.dp, vertical = if (singleLine) 14.dp else 12.dp)
        ) {
            Row(
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (isFocused) AppTextPrimary else AppTextTertiary,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(20.dp)
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(
                                fontSize = 15.sp,
                                color = AppTextTertiary
                            )
                        )
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Normal
                        ),
                        singleLine = singleLine,
                        maxLines = maxLines,
                        cursorBrush = SolidColor(AppTextPrimary),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions
                    )
                }

                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingIcon()
                }
            }
        }

        if (errorText != null) {
            Text(
                text = errorText,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = AccentDestructive
                ),
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Horizontal Filter Pill (e.g. [All], [Unread], [Contacts], [Groups]).
 */
@Composable
fun GlassFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "chipScale")

    val colors = AppTheme.colors

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) colors.textPrimary else AppGlassLow)
            .border(
                1.dp,
                if (isSelected) colors.textPrimary else AppGlassBorderSubtle,
                RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) colors.background else AppTextSecondary
            )
        )
    }
}

/**
 * Universal User Avatar with image support & 3D Glass Monogram fallback.
 */
@Composable
fun UserAvatar(
    name: String,
    modifier: Modifier = Modifier,
    avatarUri: String? = null,
    isOnline: Boolean? = null,
    size: Dp = 48.dp,
    showCubeFallback: Boolean = false
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(AppSurfaceElevated)
                .border(1.dp, AppGlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUri.isNullOrBlank()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val imageModel = remember(avatarUri) {
                    val file = java.io.File(avatarUri)
                    if (file.exists()) file else avatarUri
                }
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (showCubeFallback) {
                GlowingCubeLogo(modifier = Modifier.size(size * 0.7f))
            } else {
                val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Text(
                    text = initial,
                    style = TextStyle(
                        fontSize = (size.value * 0.42f).sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTextPrimary
                    )
                )
            }
        }

        // Online indicator dot
        if (isOnline == true) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .clip(CircleShape)
                    .background(AccentGreen)
                    .border(2.dp, BackgroundBlack, CircleShape)
            )
        }
    }
}

/**
 * Isometric 3D Translucent Glass Cube Logo with Halo Glow.
 * Matches the reference design icon.
 */
@Composable
fun GlowingCubeLogo(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp
) {
    val isDark = AppTheme.colors.isDark
    val haloColor = if (isDark) Color.White else Color(0xFF0F172A)
    val ringBg1 = if (isDark) Color(0x28FFFFFF) else Color(0x12000000)
    val ringBg2 = if (isDark) Color(0x0CFFFFFF) else Color(0x04000000)

    val primaryBubbleFill = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0F172A)
    val primaryBubbleStroke = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0F172A)
    val secondaryBubbleFill = if (isDark) Color(0x35FFFFFF) else Color(0x200F172A)
    val secondaryBubbleStroke = if (isDark) Color(0x65FFFFFF) else Color(0x400F172A)
    val dotColor = if (isDark) Color(0xFF090A0C) else Color(0xFFFFFFFF)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Ambient Radial Halo Glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.width / 2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        haloColor.copy(alpha = if (isDark) 0.22f else 0.12f),
                        haloColor.copy(alpha = if (isDark) 0.08f else 0.04f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // Frosted Glass Circle Wrapper
        Box(
            modifier = Modifier
                .size(size * 0.88f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(ringBg1, ringBg2)
                    )
                )
                .border(1.dp, AppGlassBorderBright, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Dual Message Bubble Canvas
            Canvas(modifier = Modifier.size(size * 0.54f)) {
                val w = this.size.width
                val h = this.size.height

                // Secondary Companion Bubble (Top Left)
                val secLeft = w * 0.05f
                val secTop = h * 0.08f
                val secW = w * 0.56f
                val secH = h * 0.46f
                val secRadius = androidx.compose.ui.geometry.CornerRadius(secH * 0.38f, secH * 0.38f)

                val secBubblePath = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = secLeft,
                            top = secTop,
                            right = secLeft + secW,
                            bottom = secTop + secH,
                            cornerRadius = secRadius
                        )
                    )
                }
                drawPath(secBubblePath, secondaryBubbleFill)
                drawPath(secBubblePath, secondaryBubbleStroke, style = Stroke(width = 2f))

                // Primary Message Bubble (Center & Bottom Right)
                val priLeft = w * 0.24f
                val priTop = h * 0.30f
                val priW = w * 0.72f
                val priH = h * 0.58f
                val priRadius = androidx.compose.ui.geometry.CornerRadius(priH * 0.36f, priH * 0.36f)

                val priBubblePath = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = priLeft,
                            top = priTop,
                            right = priLeft + priW,
                            bottom = priTop + priH,
                            cornerRadius = priRadius
                        )
                    )
                    // Tail on primary bubble
                    moveTo(priLeft + priW * 0.30f, priTop + priH)
                    lineTo(priLeft + priW * 0.15f, priTop + priH + h * 0.12f)
                    lineTo(priLeft + priW * 0.45f, priTop + priH)
                    close()
                }
                drawPath(priBubblePath, primaryBubbleFill)
                drawPath(priBubblePath, primaryBubbleStroke, style = Stroke(width = 2.5f))

                // Message Indicator Dots inside primary bubble
                val dotCenterY = priTop + priH * 0.50f
                val dotRadius = priW * 0.055f
                val dotSpacing = priW * 0.18f
                val startX = priLeft + priW * 0.32f

                for (i in 0..2) {
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(startX + i * dotSpacing, dotCenterY)
                    )
                }
            }
        }
    }
}

/**
 * Floating Monochrome Glass Bottom Navigation Bar.
 */
@Composable
fun GlassNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onCenterAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(34.dp),
            backgroundColor = AppTheme.colors.navBarBackground,
            borderColor = AppGlassBorder
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chats Tab
                NavTabItem(
                    label = "Chats",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    selectedIcon = Icons.Filled.ChatBubble,
                    isSelected = currentRoute == "chats",
                    onClick = { onNavigate("chats") }
                )

                // Contacts Tab
                NavTabItem(
                    label = "Contacts",
                    icon = Icons.Outlined.PeopleOutline,
                    selectedIcon = Icons.Filled.People,
                    isSelected = currentRoute == "contacts",
                    onClick = { onNavigate("contacts") }
                )

                // Center Floating Quick Action Button (+)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colors.textPrimary)
                        .border(1.dp, AppGlassBorderBright, CircleShape)
                        .clickable(onClick = onCenterAction),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Action",
                        tint = AppTheme.colors.background,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // My Identity Tab
                NavTabItem(
                    label = "Identity",
                    icon = Icons.Outlined.Badge,
                    selectedIcon = Icons.Filled.Badge,
                    isSelected = currentRoute == "profile",
                    onClick = { onNavigate("profile") }
                )

                // Settings Tab
                NavTabItem(
                    label = "Settings",
                    icon = Icons.Outlined.Settings,
                    selectedIcon = Icons.Filled.Settings,
                    isSelected = currentRoute == "settings",
                    onClick = { onNavigate("settings") }
                )
            }
        }
    }
}

@Composable
private fun NavTabItem(
    label: String,
    icon: ImageVector,
    selectedIcon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else icon,
            contentDescription = label,
            tint = if (isSelected) AppTextPrimary else AppTextTertiary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) AppTextPrimary else AppTextTertiary
            )
        )
    }
}

/**
 * Glass Side Navigation Drawer Menu (Reference Design Screen 6).
 */
@Composable
fun GlassDrawerContent(
    selfName: String,
    selfAvatarUri: String?,
    onNavigate: (String) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(290.dp)
            .background(AppTheme.colors.drawerBackground)
            .border(1.dp, AppGlassBorderSubtle, RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .padding(24.dp)
    ) {
        // Drawer Header (Profile info)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    onNavigate("profile")
                    onCloseDrawer()
                }
                .padding(8.dp)
        ) {
            UserAvatar(
                name = selfName.ifBlank { "User" },
                avatarUri = selfAvatarUri,
                size = 48.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = selfName.ifBlank { "User" },
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTextPrimary
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AccentGreen)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Online",
                        style = TextStyle(fontSize = 12.sp, color = AppTextSecondary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = AppGlassBorderSubtle)
        Spacer(modifier = Modifier.height(16.dp))

        // Drawer Menu Items
        DrawerItem(icon = Icons.Outlined.Badge, label = "My Identity") {
            onNavigate("profile")
            onCloseDrawer()
        }
        DrawerItem(icon = Icons.Outlined.People, label = "Contacts") {
            onNavigate("contacts")
            onCloseDrawer()
        }
        DrawerItem(icon = Icons.Outlined.ChatBubbleOutline, label = "Chats") {
            onNavigate("chats")
            onCloseDrawer()
        }
        DrawerItem(icon = Icons.Outlined.Call, label = "Calls") {
            onNavigate("calls")
            onCloseDrawer()
        }
        DrawerItem(icon = Icons.Outlined.FolderZip, label = "Media & Storage") {
            onNavigate("files")
            onCloseDrawer()
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = AppGlassBorderSubtle)
        Spacer(modifier = Modifier.height(16.dp))

        DrawerItem(icon = Icons.Outlined.Settings, label = "Settings") {
            onNavigate("settings")
            onCloseDrawer()
        }
        DrawerItem(icon = Icons.Outlined.WbSunny, label = "Appearance") {
            onNavigate("settings")
            onCloseDrawer()
        }
        DrawerItem(icon = Icons.Outlined.Lock, label = "Privacy & Security") {
            onNavigate("settings")
            onCloseDrawer()
        }

        Spacer(modifier = Modifier.weight(1f))

        // Log out / Reset
        GlassCard(
            onClick = {
                onNavigate("logout")
                onCloseDrawer()
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Logout,
                    contentDescription = null,
                    tint = AccentDestructive,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Log Out",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentDestructive
                    )
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = AppTextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AppTextPrimary
            )
        )
    }
}
