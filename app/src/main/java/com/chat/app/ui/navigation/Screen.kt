package com.chat.app.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Main : Screen("main")
    data object ChatList : Screen("chat_list")
    data object Pairing : Screen("pairing")
    data object Contacts : Screen("contacts")
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")
    data object MediaStorage : Screen("media_storage")
    data class Chat(val conversationId: String) : Screen("chat/{conversationId}") {
        companion object {
            const val ROUTE = "chat/{conversationId}"
            fun createRoute(conversationId: String) = "chat/$conversationId"
        }
    }
}

