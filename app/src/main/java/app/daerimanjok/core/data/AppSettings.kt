package app.daerimanjok.core.data

import app.daerimanjok.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * User settings (Settings screen). Language is not here: it uses Android's per-app locale.
 */
class AppSettings(private val prefs: AppPrefs) {

    /** Light/dark choice. Default follows the car's day/night setting. */
    val themeMode: Flow<ThemeMode> = prefs.string(KEY_THEME).map { v ->
        ThemeMode.entries.firstOrNull { it.name == v } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) = prefs.setString(KEY_THEME, mode.name)

    /**
     * 시연 모드: replays a recorded route so the trip screens and report can be shown on the
     * emulator, whose GPS doesn't move. Off by default; real-car data is always the default.
     */
    val demoMode: Flow<Boolean> = prefs.string(KEY_DEMO).map { it == "1" }

    suspend fun setDemoMode(on: Boolean) = prefs.setString(KEY_DEMO, if (on) "1" else null)

    private companion object {
        const val KEY_THEME = "settings_theme"
        const val KEY_DEMO = "settings_demo"
    }
}
