package com.priorityring.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.priorityring.app.navigation.AppNavigation
import com.priorityring.app.ui.theme.PriorityRingTheme
import com.priorityring.data.settings.SettingsRepository
import com.priorityring.data.settings.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The app's single Activity; every screen is a Compose destination inside it. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars; Scaffold adds the right padding back.
        // (Edge-to-edge is mandatory for apps targeting Android 15+ anyway.)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = hiltViewModel()
            val theme = vm.themeMode.collectAsStateWithLifecycle().value
            PriorityRingTheme(mode = theme) {
                AppNavigation()
            }
        }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    // Starts as DARK (the default) so there's no flash before DataStore has loaded.
    val themeMode = settings.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)
}
