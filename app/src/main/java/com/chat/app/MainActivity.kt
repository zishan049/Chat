package com.chat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chat.app.data.local.preferences.ThemePreferenceManager
import com.chat.app.messaging.domain.TransportDispatcher
import com.chat.app.ui.navigation.AppNavigation
import com.chat.app.ui.theme.ChatTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var transportDispatcher: TransportDispatcher

    @Inject
    lateinit var themePreferenceManager: ThemePreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        transportDispatcher.start()

        setContent {
            val isDarkMode by themePreferenceManager.isDarkMode.collectAsStateWithLifecycle()
            ChatTheme(darkTheme = isDarkMode) {
                AppNavigation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        transportDispatcher.stop()
    }
}
