package com.chat.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chat.app.messaging.presentation.chat.ChatScreen
import com.chat.app.onboarding.presentation.OnboardingScreen
import com.chat.app.pairing.presentation.PairingScreen
import com.chat.app.ui.components.SplashScreen
import com.chat.app.ui.theme.BackgroundBlack

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val authState by mainViewModel.authState.collectAsStateWithLifecycle()

    val incomingPairedContact by mainViewModel.incomingPairedContact.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = authState) {
            is AuthState.Loading -> {
                SplashScreen()
            }

            is AuthState.Authenticated, is AuthState.Unauthenticated -> {
                val startDestination = if (authState is AuthState.Authenticated) {
                    Screen.Main.route
                } else {
                    Screen.Onboarding.route
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable(Screen.Onboarding.route) {
                        OnboardingScreen(
                            onNavigateToMain = {
                                mainViewModel.refreshAuth()
                                navController.navigate(Screen.Main.route) {
                                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Main.route) {
                        val identity = (authState as? AuthState.Authenticated)?.identity
                        val activePort = mainViewModel.getActivePort()
                        MainShell(
                            selfIdentity = identity,
                            activePort = activePort,
                            onNavigateToChat = { conversationId ->
                                navController.navigate(Screen.Chat.createRoute(conversationId))
                            },
                            onNavigateToPairing = {
                                navController.navigate(Screen.Pairing.route)
                            },
                            onNavigateToMediaStorage = {
                                navController.navigate(Screen.MediaStorage.route)
                            },
                            onAccountDeleted = {
                                mainViewModel.logout()
                                navController.navigate(Screen.Onboarding.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Pairing.route) {
                        PairingScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onPairingSuccess = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable(Screen.MediaStorage.route) {
                        com.chat.app.media.presentation.MediaStorageScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Screen.Chat.ROUTE,
                        arguments = listOf(
                            navArgument("conversationId") { type = NavType.StringType }
                        )
                    ) {
                        ChatScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        // Global Instant Peer Pairing Dialog (Appears immediately on the QR-sharing device)
        incomingPairedContact?.let { contact ->
            PeerPairingSuccessDialog(
                contact = contact,
                onStartChat = {
                    mainViewModel.dismissPairingDialog()
                    navController.navigate(Screen.Chat.createRoute(contact.id))
                },
                onDismiss = {
                    mainViewModel.dismissPairingDialog()
                }
            )
        }
    }
}

@Composable
private fun PeerPairingSuccessDialog(
    contact: com.chat.app.domain.model.Contact,
    onStartChat: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        com.chat.app.ui.components.GlassSurface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            backgroundColor = com.chat.app.ui.theme.AppTheme.colors.surface,
            borderColor = com.chat.app.ui.theme.AccentGreen.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                com.chat.app.ui.components.UserAvatar(
                    name = contact.displayName,
                    avatarUri = contact.avatarUri,
                    isOnline = true,
                    size = 64.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "New Contact Paired!",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 19.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = com.chat.app.ui.theme.AppTextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Secure cryptographic channel established with ${contact.displayName}.",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = com.chat.app.ui.theme.AppTextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Fingerprint: ${contact.fingerprint}",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = com.chat.app.ui.theme.AppTextTertiary
                    )
                )

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    com.chat.app.ui.components.GlassButton(
                        text = "Dismiss",
                        isPrimary = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    com.chat.app.ui.components.GlassButton(
                        text = "Start Chat",
                        isPrimary = true,
                        onClick = onStartChat,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

