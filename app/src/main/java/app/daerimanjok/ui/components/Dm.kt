package app.daerimanjok.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.daerimanjok.R
import app.daerimanjok.core.car.Gear
import app.daerimanjok.core.restrictions.DriveMode
import app.daerimanjok.ui.theme.Dm

/*
 * 대리만족 components, built to the UI prototype's measurements (px = dp at 160 dpi).
 * Screens should be assembled from these so every screen matches the prototype.
 */

/** Current drive mode, available to any composable without threading it through parameters. */
val LocalDriveMode = compositionLocalOf { DriveMode.UNKNOWN }

/** Current gear, for the status pill. Provided by AppRoot. */
val LocalGear = compositionLocalOf<Gear?> { null }

/** Shows the "시연 모드" badge in the top bar. Provided by AppRoot. */
val LocalDemoMode = compositionLocalOf { false }

/** Opens Settings from the top bar. Null hides the button. Provided by AppRoot. */
val LocalOpenSettings = compositionLocalOf<(() -> Unit)?> { null }

/** Screen padding under the top bar (prototype `.content`). */
val ContentPadding = PaddingValues(start = 72.dp, end = 72.dp, top = 8.dp, bottom = 64.dp)

private fun Gear.letter() = when (this) {
    Gear.PARK -> "P"
    Gear.REVERSE -> "R"
    Gear.NEUTRAL -> "N"
    Gear.DRIVE -> "D"
    Gear.UNKNOWN -> null
}

/**
 * Prototype `.bar`: optional back button, title, and on the right the demo badge,
 * the settings button and the live driving-state pill ("● 정차 중 · P").
 *
 * [stateText] overrides the pill's words (e.g. "주차 완료" after arrival).
 */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    stateText: String? = null,
) {
    Row(
        modifier.fillMaxWidth().height(112.dp).padding(start = 48.dp, end = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) DmIconButton(DmIcons.Back, stringResource(R.string.cd_back), onBack)
        else Spacer(Modifier.width(8.dp))
        Text(title, style = Dm.type.barTitle, color = Dm.colors.t1, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (LocalDemoMode.current) {
            Text(stringResource(R.string.demo_badge), style = Dm.type.labelS.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.t3)
        }
        // Settings are for the owner while parked; hidden while the driver is on the road.
        if (LocalDriveMode.current == DriveMode.PARKED) LocalOpenSettings.current?.let { open ->
            DmIconButton(DmIcons.Settings, stringResource(R.string.cd_settings), open)
        }
        StatePill(stateText)
    }
}

/** "● 정차 중 · P" / "● 주행 중 · D". Green dot when parked, blue while driving. */
@Composable
fun StatePill(text: String? = null) {
    val driving = LocalDriveMode.current == DriveMode.DRIVING
    val words = text ?: stringResource(if (driving) R.string.state_driving else R.string.state_parked)
    val gear = LocalGear.current?.letter()
    Row(
        Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(Dm.colors.fill).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (driving) Dm.colors.info else Dm.colors.ok))
        Text(
            if (gear != null) "$words · $gear" else words,
            style = Dm.type.labelS.copy(fontWeight = FontWeight.SemiBold),
            color = Dm.colors.t2,
        )
    }
}

/** Status chip with a check, e.g. "✓ 목적지에 도착했어요" (prototype S4). */
@Composable
fun DoneChip(text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(40.dp)).background(Dm.colors.accentSoft).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(DmIcons.Check, null, tint = Dm.colors.accentText, modifier = Modifier.size(28.dp))
        Text(text, style = Dm.type.label.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.accentText)
    }
}

/** Prototype `.iconbtn`: 72 × 72, transparent until pressed. */
@Composable
fun DmIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (pressed) Dm.colors.press else Color.Transparent)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) Dm.colors.t1 else Dm.colors.t3, modifier = Modifier.size(40.dp))
    }
}

enum class DmButtonKind { Primary, Basic, Text }

/**
 * Prototype `.btn`: 96 high, radius 24, 30/38 bold.
 * [parkedOnly] disables it while driving and shows a lock (setup, reading, typing).
 */
@Composable
fun DmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: DmButtonKind = DmButtonKind.Primary,
    enabled: Boolean = true,
    parkedOnly: Boolean = false,
    trailingIcon: ImageVector? = null,
) {
    val blocked = parkedOnly && LocalDriveMode.current != DriveMode.PARKED
    val active = enabled && !blocked
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val c = Dm.colors
    val (bg, fg) = when {
        !active -> c.fill to c.t3
        kind == DmButtonKind.Primary -> (if (pressed) c.accentPress else c.accent) to c.onAccent
        kind == DmButtonKind.Basic -> (if (pressed) c.press else c.fill) to c.t1
        else -> (if (pressed) c.fill else Color.Transparent) to c.accentText
    }
    val background by animateColorAsState(bg, label = "btn")
    Row(
        modifier
            .defaultMinSize(minHeight = 96.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .clickable(interaction, indication = null, enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (kind == DmButtonKind.Text) 28.dp else 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        if (blocked) Icon(DmIcons.Lock, null, tint = fg, modifier = Modifier.size(32.dp))
        Text(text, style = Dm.type.button, color = fg, maxLines = 1)
        if (trailingIcon != null && !blocked) Icon(trailingIcon, null, tint = fg, modifier = Modifier.size(36.dp))
    }
}

/** Prototype `.panel`: surface card, radius 36. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 56.dp, vertical = 56.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(36.dp)).background(Dm.colors.surface).padding(padding),
        content = content,
    )
}

/** Prototype `.eyebrow`: small accent line above a headline. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    Text(text, style = Dm.type.label.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.accentText, modifier = modifier)

@Composable
fun DmDivider(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(1.dp).background(Dm.colors.divider))

/** Prototype `.icon-tile`: 88 × 88 accent-tinted square with an icon. */
@Composable
fun IconTile(icon: ImageVector, size: Dp = 88.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Dm.colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Dm.colors.accentText, modifier = Modifier.size(size * 0.45f)) }
}

/** Icon + text line, e.g. "ⓘ 건물 뒤편 입구로 들어가 주세요". */
@Composable
fun IconLine(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Dm.colors.t2,
    iconTint: Color = color,
    iconSize: Dp = 32.dp,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(iconSize))
        Text(text, style = Dm.type.bodyS, color = color)
        trailing()
    }
}

/**
 * Prototype `.field`: label over an 88-high input (radius 20, accent border when focused).
 * Text input is for parked-only screens; the car keyboard is blocked while driving.
 */
@Composable
fun DmField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboard: KeyboardOptions = KeyboardOptions.Default,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val c = Dm.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = Dm.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.t2)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = LocalDriveMode.current == DriveMode.PARKED,
            textStyle = Dm.type.body.copy(color = c.t1),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = keyboard,
            interactionSource = interaction,
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (focused) c.surface else c.fill)
                        .border(2.dp, if (focused) c.accent else Color.Transparent, RoundedCornerShape(20.dp))
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) Text(placeholder, style = Dm.type.body, color = c.t3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            },
        )
    }
}

/** Left-column step in setup: number (or check when done), title and summary. */
@Composable
fun StepItem(
    number: Int,
    title: String,
    summary: String,
    done: Boolean,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Dm.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (current) c.surface else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val (bg, fg) = when {
            current -> c.accent to c.onAccent
            done -> c.accentSoft to c.accentText
            else -> Color.Transparent to c.t3
        }
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(bg)
                .border(2.dp, if (!current && !done) c.divider else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done && !current) Icon(DmIcons.Check, null, tint = fg, modifier = Modifier.size(28.dp))
            else Text("$number", style = Dm.type.label.copy(fontWeight = FontWeight.Bold), color = fg)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = Dm.type.bodyL.copy(fontWeight = FontWeight.SemiBold), color = c.t1)
            Text(summary, style = Dm.type.label, color = c.t2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Radio row used to pick a destination: ring + name + address. */
@Composable
fun RadioRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Dm.colors
    val bg by animateColorAsState(if (selected) c.accentSoft else c.fill, label = "radio")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).border(3.dp, if (selected) c.accent else c.t3, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(20.dp).clip(CircleShape).background(c.accent)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = Dm.type.titleS, color = c.t1)
            Text(subtitle, style = Dm.type.bodyS, color = c.t2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Prototype `.tabs`: segmented control, 72-high tabs. */
@Composable
fun <T> SegmentedTabs(
    items: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    val c = Dm.colors
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(c.fill).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            val on = item == selected
            val bg by animateColorAsState(if (on) c.tabOn else Color.Transparent, label = "tab")
            Box(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .height(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .clickable(role = Role.Tab) { onSelect(item) }
                    .padding(horizontal = if (fill) 8.dp else 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(item), style = Dm.type.tab, color = if (on) c.t1 else c.t2, maxLines = 1)
            }
        }
    }
}

/** Label on the left, value on the right (car status, trip summary). */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Dm.colors.t1) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Dm.type.bodyL, color = Dm.colors.t2)
        Text(value, style = Dm.type.bodyL.copy(fontWeight = FontWeight.Bold), color = valueColor)
    }
}

/** Thin rounded bar, e.g. fuel level. [fraction] is 0..1. */
@Composable
fun DmProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = Dm.colors.accent) {
    Box(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Dm.colors.fill)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(color))
    }
}

/** Placeholder frame where a photo will go (control photos for S2). */
@Composable
fun ImagePlaceholder(caption: String, modifier: Modifier = Modifier) {
    val c = Dm.colors
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(c.fill)
            .border(2.dp, c.divider, RoundedCornerShape(28.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(DmIcons.Image, null, tint = c.t3, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(caption, style = Dm.type.bodyS, color = c.t3)
    }
}

/** Prototype `.pins`: 4 rings that fill in as digits are typed. */
@Composable
fun PinDots(filled: Int, length: Int = 4, error: Boolean = false, modifier: Modifier = Modifier) {
    val c = Dm.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        repeat(length) { i ->
            val on = i < filled
            val ring = when {
                error -> c.danger
                on -> c.accent
                else -> c.t3
            }
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (on) ring else Color.Transparent)
                    .border(3.dp, ring, CircleShape)
            )
        }
    }
}

/**
 * Prototype `.keypad`: 3 × 4 keys, 104 high, radius 24.
 * Bottom row: 초기화 (clear) · 0 · 지우기 (delete one).
 */
@Composable
fun Keypad(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 4,
    enabled: Boolean = true,
) {
    val clear = stringResource(R.string.key_clear)
    val delete = stringResource(R.string.key_delete)
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(clear, "0", delete))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    val isAction = key == clear || key == delete
                    Key(
                        label = key,
                        small = isAction,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        onValueChange(
                            when (key) {
                                clear -> ""
                                delete -> value.dropLast(1)
                                else -> if (value.length < length) value + key else value
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, small: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val c = Dm.colors
    Box(
        modifier
            .height(104.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (pressed) c.press else c.fill)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = Dm.type.button.copy(fontSize = if (small) 26.sp else 40.sp, fontWeight = FontWeight.SemiBold),
            color = if (enabled) c.t1 else c.t3,
        )
    }
}

/** Two-column screen body (prototype grid): fixed-width left column, flexible right. */
@Composable
fun TwoColumn(
    modifier: Modifier = Modifier,
    leftWidth: Dp = 480.dp,
    gap: Dp = 80.dp,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable BoxScope.() -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(Modifier.width(leftWidth).fillMaxHeight(), content = left)
        Box(Modifier.weight(1f).fillMaxHeight(), content = right)
    }
}

