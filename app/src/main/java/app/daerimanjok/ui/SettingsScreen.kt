package app.daerimanjok.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.daerimanjok.R
import app.daerimanjok.core.restrictions.DriveMode
import app.daerimanjok.ui.components.ContentPadding
import app.daerimanjok.ui.components.DmButton
import app.daerimanjok.ui.components.DmButtonKind
import app.daerimanjok.ui.components.DmDivider
import app.daerimanjok.ui.components.DmIcons
import app.daerimanjok.ui.components.IconLine
import app.daerimanjok.ui.components.LocalDriveMode
import app.daerimanjok.ui.components.Panel
import app.daerimanjok.ui.components.SegmentedTabs
import app.daerimanjok.ui.components.TopBar
import app.daerimanjok.ui.theme.Dm
import app.daerimanjok.ui.theme.ThemeMode

/** Settings: theme, language and 시연 모드. Parked only, like all setup. */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    demoMode: Boolean,
    onDemoMode: (Boolean) -> Unit,
    onResetAll: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val parked = LocalDriveMode.current == DriveMode.PARKED
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings_title), onBack = onBack)
        Column(Modifier.padding(ContentPadding).widthIn(max = 1100.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                SettingRow(stringResource(R.string.settings_theme), stringResource(R.string.settings_theme_desc)) {
                    SegmentedTabs(
                        items = ThemeMode.entries,
                        selected = themeMode,
                        label = {
                            stringResource(
                                when (it) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.DARK -> R.string.theme_dark
                                    ThemeMode.LIGHT -> R.string.theme_light
                                }
                            )
                        },
                        onSelect = { if (parked) onThemeMode(it) },
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    DmDivider(Modifier.padding(vertical = 40.dp))
                    val lang = LocalConfiguration.current.locales[0].language
                    SettingRow(stringResource(R.string.settings_language), null) {
                        SegmentedTabs(
                            items = listOf("ko", "en"),
                            selected = if (lang == "ko") "ko" else "en",
                            label = { if (it == "ko") "한국어" else "English" },
                            onSelect = { if (parked && it != lang) setAppLanguage(context, it) },
                        )
                    }
                }
                DmDivider(Modifier.padding(vertical = 40.dp))
                SettingRow(stringResource(R.string.settings_demo), stringResource(R.string.settings_demo_desc)) {
                    Switch(
                        checked = demoMode,
                        onCheckedChange = onDemoMode,
                        enabled = parked,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Dm.colors.accent,
                            checkedThumbColor = Dm.colors.onAccent,
                            uncheckedTrackColor = Dm.colors.fillStrong,
                            uncheckedBorderColor = Dm.colors.divider,
                            uncheckedThumbColor = Dm.colors.t2,
                        ),
                    )
                }
                DmDivider(Modifier.padding(vertical = 40.dp))
                SettingRow(stringResource(R.string.settings_reset), stringResource(R.string.settings_reset_desc)) {
                    DmButton(stringResource(R.string.settings_reset_button), { confirmReset = true }, kind = DmButtonKind.Basic, parkedOnly = true)
                }
            }
            Spacer(Modifier.height(32.dp))
            IconLine(DmIcons.Info, stringResource(R.string.settings_parked_only))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title), style = Dm.type.titleS) },
            text = { Text(stringResource(R.string.settings_reset_confirm_desc), style = Dm.type.body) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onResetAll(); onBack() }) {
                    Text(stringResource(R.string.settings_reset_button), style = Dm.type.button, color = Dm.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.btn_cancel), style = Dm.type.button, color = Dm.colors.t1)
                }
            },
            containerColor = Dm.colors.surface,
            tonalElevation = 0.dp,
            titleContentColor = Dm.colors.t1,
            textContentColor = Dm.colors.t2,
        )
    }
}

@Composable
private fun SettingRow(title: String, desc: String?, control: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = Dm.type.titleS, color = Dm.colors.t1)
            desc?.let { Text(it, style = Dm.type.bodyS, color = Dm.colors.t2) }
        }
        control()
    }
}

/** Android's per-app language (API 33+): persists, and shows in the system's app settings. */
private fun setAppLanguage(context: Context, tag: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
}
