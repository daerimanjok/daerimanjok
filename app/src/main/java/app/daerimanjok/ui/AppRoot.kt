package app.daerimanjok.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.daerimanjok.core.restrictions.DriveMode
import app.daerimanjok.core.valueOrNull
import app.daerimanjok.feature.daeri.ui.DaeriRoot
import app.daerimanjok.ui.components.LocalDemoMode
import app.daerimanjok.ui.components.LocalDriveMode
import app.daerimanjok.ui.components.LocalGear
import app.daerimanjok.ui.components.LocalOpenSettings
import app.daerimanjok.ui.theme.Dm

/**
 * App shell: provides driving state, gear and settings access to every screen, then shows
 * either Settings or the 대리만족 flow. Screens adapt to the window size themselves
 * (full app area, or the narrow column next to navigation).
 */
@Composable
fun AppRoot(vm: MainViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val demoMode by vm.demoMode.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    // Settings are parked-only: close them as soon as the car starts moving.
    LaunchedEffect(status.driveMode) {
        if (status.driveMode == DriveMode.DRIVING) settingsOpen = false
    }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    CompositionLocalProvider(
        LocalDriveMode provides status.driveMode,
        LocalGear provides status.gear.valueOrNull(),
        LocalDemoMode provides demoMode,
        LocalOpenSettings provides { settingsOpen = true },
    ) {
        // Surface sets the default content (text) colour for everything inside.
        Surface(Modifier.fillMaxSize(), color = Dm.colors.bg, contentColor = Dm.colors.t1) {
            if (settingsOpen) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeMode = vm::setThemeMode,
                    demoMode = demoMode,
                    onDemoMode = vm::setDemoMode,
                    onResetAll = vm::resetAll,
                    onBack = { settingsOpen = false },
                )
            } else {
                DaeriRoot(vm)
            }
        }
    }
}
