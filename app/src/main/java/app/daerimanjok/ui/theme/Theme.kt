package app.daerimanjok.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.daerimanjok.R

/*
 * 대리만족 design tokens, taken 1:1 from the UI prototype (handover-official, 2026-11-11).
 * The prototype is drawn at the emulator's app panel size (1682 × 1210) at 160 dpi, so its
 * px values are used directly as dp/sp.
 */

/** Colour tokens. Names follow the prototype's CSS variables. */
@Immutable
data class DmColors(
    val bg: Color,
    val surface: Color,
    val tabOn: Color,
    val fill: Color,
    val fillStrong: Color,
    val press: Color,
    val t1: Color,
    val t2: Color,
    val t3: Color,
    val divider: Color,
    val accent: Color,
    val accentPress: Color,
    val onAccent: Color,
    val accentText: Color,
    val accentSoft: Color,
    val warn: Color,
    val warnMark: Color,
    val warnSoft: Color,
    val danger: Color,
    val dangerMark: Color,
    val dangerSoft: Color,
    val ok: Color,
    val info: Color,
    val infoSoft: Color,
    val mapLand: Color,
    val mapBlock: Color,
    val mapRoad: Color,
    val mapWater: Color,
    val car: Color,
    val isDark: Boolean,
)

val DarkColors = DmColors(
    bg = Color(0xFF131417),
    surface = Color(0xFF1D1E21),
    tabOn = Color(0xFF313236),
    fill = Color.White.copy(alpha = 0.06f),
    fillStrong = Color.White.copy(alpha = 0.10f),
    press = Color.White.copy(alpha = 0.14f),
    t1 = Color.White,
    t2 = Color.White.copy(alpha = 0.64f),
    t3 = Color.White.copy(alpha = 0.44f),
    divider = Color.White.copy(alpha = 0.10f),
    // Brand green
    accent = Color(0xFF42B258),
    accentPress = Color(0xFF379A4B),
    onAccent = Color(0xFF07170C),
    accentText = Color(0xFF6CCB7F),
    accentSoft = Color(0xFF42B258).copy(alpha = 0.16f),
    warn = Color(0xFFFFA23A),
    warnMark = Color(0xFFFF8A00),
    warnSoft = Color(0xFFFFA23A).copy(alpha = 0.14f),
    danger = Color(0xFFFF6A4D),
    dangerMark = Color(0xFFFF4C28),
    dangerSoft = Color(0xFFFF4C28).copy(alpha = 0.14f),
    ok = Color(0xFF00E074),
    info = Color(0xFF0082FF),
    infoSoft = Color(0xFF0082FF).copy(alpha = 0.16f),
    mapLand = Color(0xFF1A1B1E),
    mapBlock = Color(0xFF232428),
    mapRoad = Color(0xFF30323A),
    mapWater = Color(0xFF1C2836),
    car = Color.White,
    isDark = true,
)

private val Ink = Color(0xFF131417)

val LightColors = DmColors(
    bg = Color(0xFFF6F4EF),
    surface = Color.White,
    tabOn = Color.White,
    fill = Ink.copy(alpha = 0.05f),
    fillStrong = Ink.copy(alpha = 0.08f),
    press = Ink.copy(alpha = 0.10f),
    t1 = Ink,
    t2 = Ink.copy(alpha = 0.64f),
    t3 = Ink.copy(alpha = 0.46f),
    divider = Ink.copy(alpha = 0.10f),
    // Brand green; small accent text uses a darker green so it stays readable on the light page.
    accent = Color(0xFF42B258),
    accentPress = Color(0xFF379A4B),
    onAccent = Color.White,
    accentText = Color(0xFF2B8640),
    accentSoft = Color(0xFFE3F4E6),
    warn = Color(0xFFB35F00),
    warnMark = Color(0xFFF08A00),
    warnSoft = Color(0xFFFCEFDD),
    danger = Color(0xFFD8361A),
    dangerMark = Color(0xFFFE3D16),
    dangerSoft = Color(0xFFFDE6E0),
    ok = Color(0xFF02C265),
    info = Color(0xFF0064FF),
    infoSoft = Color(0xFF0064FF).copy(alpha = 0.10f),
    mapLand = Color(0xFFECE8E1),
    mapBlock = Color(0xFFF8F6F2),
    mapRoad = Color.White,
    mapWater = Color(0xFFCFE2EF),
    car = Ink,
    isDark = false,
)

/** Pretendard (SIL OFL 1.1, licence in assets/licenses), the prototype's font. */
val AppFont = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

/** Type scale from the prototype. Every style carries the prototype's tight letter-spacing. */
@Immutable
object DmType {
    private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = -0.01) = TextStyle(
        fontFamily = AppFont,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.em,
        // Like the prototype's `word-break: keep-all`: Korean wraps between words, not inside one.
        lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase),
    )

    val hXl = style(60, 80, FontWeight.ExtraBold, -0.035)
    val hL = style(48, 64, FontWeight.Bold, -0.03)
    val title = style(36, 44, FontWeight.Bold, -0.025)
    val titleS = style(32, 40, FontWeight.Bold, -0.02)
    val barTitle = titleS
    val bodyL = style(30, 38, FontWeight.Normal)
    val body = style(28, 36, FontWeight.Normal)
    val bodyS = style(26, 34, FontWeight.Normal)
    val label = style(24, 28, FontWeight.Normal)
    val labelS = style(20, 24, FontWeight.Normal)
    val button = style(30, 38, FontWeight.Bold, -0.02)
    val tab = style(26, 34, FontWeight.SemiBold)
}

/** User choice in Settings. SYSTEM follows the car's day/night setting. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

val LocalDmColors = staticCompositionLocalOf { DarkColors }

/** Shortcut: `Dm.colors.accent`, `Dm.type.title`. */
object Dm {
    val colors: DmColors
        @Composable @ReadOnlyComposable get() = LocalDmColors.current
    val type get() = DmType
}

@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val c = if (dark) DarkColors else LightColors
    // Material components (text fields, switches, ripples) take their colours from these.
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        secondary = c.warn,
        tertiary = c.ok,
        background = c.bg,
        onBackground = c.t1,
        surface = c.surface,
        onSurface = c.t1,
        surfaceVariant = c.fillStrong.compositeOver(c.bg),
        onSurfaceVariant = c.t2,
        outline = c.divider,
        outlineVariant = c.divider,
        error = c.danger,
    )
    // Material animates between its styles (e.g. a text field's label), which fails when
    // they mix em and sp letter-spacing; its defaults use sp, so convert ours.
    val typography = Typography(
        displayMedium = DmType.hL.spTracking(),
        headlineLarge = DmType.title.spTracking(),
        headlineMedium = DmType.titleS.spTracking(),
        titleLarge = DmType.titleS.spTracking(),
        titleMedium = DmType.bodyL.copy(fontWeight = FontWeight.SemiBold).spTracking(),
        bodyLarge = DmType.body.spTracking(),
        bodyMedium = DmType.bodyS.spTracking(),
        labelLarge = DmType.button.spTracking(),
        labelMedium = DmType.label.copy(fontWeight = FontWeight.SemiBold).spTracking(),
    )
    CompositionLocalProvider(LocalDmColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

private fun TextStyle.spTracking() = copy(letterSpacing = (letterSpacing.value * fontSize.value).sp)
