package com.chat.app.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object ChatList : Screen("chat_list")
    data object Pairing : Screen("pairing")
    data object Contacts : Screen("contacts")
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")
    data class Chat(val conversationId: String) : Screen("chat/{conversationId}") {
        companion object {
            const val ROUTE = "chat/{conversationId}"
            fun createRoute(conversationId: String) = "chat/$conversationId"
        }
    }
}
