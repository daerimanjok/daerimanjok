package app.daerimanjok.feature.daeri.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.daerimanjok.R
import app.daerimanjok.core.restrictions.DriveMode
import app.daerimanjok.feature.daeri.DaeriTrip
import app.daerimanjok.feature.daeri.record.EventType
import app.daerimanjok.feature.daeri.record.RoutePoint
import app.daerimanjok.feature.daeri.record.TripEvent
import app.daerimanjok.ui.toSketch
import app.daerimanjok.ui.components.ContentPadding
import app.daerimanjok.ui.components.DmButton
import app.daerimanjok.ui.components.DmButtonKind
import app.daerimanjok.ui.components.DmDivider
import app.daerimanjok.ui.components.DmIcons
import app.daerimanjok.ui.components.IconTile
import app.daerimanjok.ui.components.InfoRow
import app.daerimanjok.ui.components.Keypad
import app.daerimanjok.ui.components.LocalDriveMode
import app.daerimanjok.ui.components.Panel
import app.daerimanjok.ui.components.PinDots
import app.daerimanjok.ui.components.SegmentedTabs
import app.daerimanjok.ui.components.TopBar
import app.daerimanjok.ui.theme.Dm
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ReportTab { SUMMARY, SPEED }

/** S5 대리 리포트: locked behind the owner PIN, then 요약 / 속도 분석. Parked only. */
@Composable
fun ReportScreen(
    trip: DaeriTrip,
    points: List<RoutePoint>,
    events: List<TripEvent>,
    checkPin: (String) -> Boolean,
    onDelete: () -> Unit,
) {
    var unlocked by rememberSaveable { mutableStateOf(false) }
    val parked = LocalDriveMode.current == DriveMode.PARKED
    // Never leave the report open once the car moves.
    LaunchedEffect(parked) { if (!parked) unlocked = false }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (unlocked) stringResource(R.string.report_bar) else "")
        if (!unlocked) LockView(trip, parked) { pin -> checkPin(pin).also { if (it) unlocked = true } }
        else ReportView(trip, points, events, onLock = { unlocked = false }, onDelete = onDelete)
    }
}

@Composable
private fun LockView(trip: DaeriTrip, parked: Boolean, tryPin: (String) -> Boolean) {
    var pin by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    val day = tripDay(trip)
    // Keypad sits beside the headline with open space on the right (prototype layout).
    Row(
        Modifier.fillMaxSize().padding(ContentPadding).padding(start = 48.dp, end = 268.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(120.dp),
    ) {
        Column(Modifier.weight(1f)) {
            IconTile(DmIcons.Lock, size = 104.dp)
            Text(stringResource(R.string.lock_headline, day), style = Dm.type.hXl, color = Dm.colors.t1, modifier = Modifier.padding(top = 40.dp))
            // Only a wrong PIN needs words here; the dots say what to do.
            if (wrong) Text(stringResource(R.string.lock_wrong), style = Dm.type.bodyL, color = Dm.colors.danger, modifier = Modifier.padding(top = 20.dp))
            PinDots(pin.length, error = wrong, modifier = Modifier.padding(top = 48.dp))
        }
        Keypad(
            pin,
            { v ->
                wrong = false
                pin = v
                if (v.length == 4) {
                    if (!tryPin(v)) wrong = true
                    pin = ""
                }
            },
            Modifier.width(530.dp),
            enabled = parked,
        )
    }
}

@Composable
private fun ReportView(
    trip: DaeriTrip,
    points: List<RoutePoint>,
    events: List<TripEvent>,
    onLock: () -> Unit,
    onDelete: () -> Unit,
) {
    val stats = trip.stats ?: return
    var tab by rememberSaveable { mutableStateOf(ReportTab.SUMMARY) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val endMs = stats.endedAtMs ?: System.currentTimeMillis()
    val overspeed = stats.overspeeds
    val hardAccel = stats.hardAccels
    val attention = overspeed + hardAccel + stats.hardBrakes
    val locale = LocalConfiguration.current.locales[0]
    val day = tripDay(trip)

    Column(Modifier.fillMaxSize().padding(ContentPadding), verticalArrangement = Arrangement.spacedBy(40.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${reportDate(stats.startedAtMs, locale)} · ${clockTime(stats.startedAtMs)} → ${clockTime(endMs)}",
                    style = Dm.type.label.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.accentText,
                )
                Text(
                    stringResource(R.string.report_title_good, day),
                    style = Dm.type.hL, color = Dm.colors.t1,
                )
            }
            SegmentedTabs(
                items = ReportTab.entries,
                selected = tab,
                label = { stringResource(if (it == ReportTab.SUMMARY) R.string.tab_summary else R.string.tab_speed) },
                onSelect = { tab = it },
            )
        }
        when (tab) {
            ReportTab.SUMMARY -> SummaryTab(trip, endMs, points, events, onLock) { confirmDelete = true }
            ReportTab.SPEED -> SpeedTab(trip, points)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title), style = Dm.type.titleS) },
            text = { Text(stringResource(R.string.delete_desc), style = Dm.type.body) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete_confirm), style = Dm.type.button, color = Dm.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
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
private fun ColumnScope.SummaryTab(
    trip: DaeriTrip,
    endMs: Long,
    points: List<RoutePoint>,
    events: List<TripEvent>,
    onLock: () -> Unit,
    onDelete: () -> Unit,
) {
    val stats = trip.stats ?: return
    val overspeed = stats.overspeeds
    val hardAccel = stats.hardAccels
    val attention = overspeed + hardAccel + stats.hardBrakes
    val sketch = points.toSketch()
    // Each event drawn where the car was at that moment.
    val markers = events.filter { it.type != EventType.DOOR_OPEN }.mapNotNull { e ->
        points.filter { it.lat != null && it.lon != null }.minByOrNull { abs(it.atMs - e.atMs) }
            ?.let { p -> Offset(p.lon!!.toFloat(), p.lat!!.toFloat()) to eventColor(e.type) }
    }
    // Stats strip, divided top and bottom like the prototype.
    Column {
        DmDivider()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 32.dp)) {
            StatCell(stringResource(R.string.stat_distance), oneDecimal(stats.distanceKm), " " + stringResource(R.string.unit_km), Modifier.weight(1f), first = true)
            StatCell(stringResource(R.string.stat_duration), "${(stats.durationMs(endMs) / 60_000).toInt()}", stringResource(R.string.unit_min), Modifier.weight(1f))
            StatCell(stringResource(R.string.stat_top), "${stats.topSpeedKmh.roundToInt()}", " " + stringResource(R.string.unit_kmh), Modifier.weight(1f))
            StatCell(
                stringResource(R.string.stat_attention), "$attention", stringResource(R.string.unit_count), Modifier.weight(1f),
                sub = stringResource(R.string.attention_breakdown, overspeed, hardAccel, stats.hardBrakes),
                valueColor = if (attention == 0) Dm.colors.accentText else Dm.colors.warn,
            )
        }
        DmDivider()
    }
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(36.dp)) {
            Text(stringResource(R.string.panel_route), style = Dm.type.bodyS.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.t1)
            Box(Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp).clip(RoundedCornerShape(20.dp))) {
                if (trip.demo) RouteSketch(sketch, Modifier.fillMaxSize(), markers, planned = demoRoute, mockMap = true, padding = 40.dp)
                else if (sketch.size >= 2 && sketch.distinct().size > 1) RouteSketch(sketch, Modifier.fillMaxSize().padding(24.dp), markers)
                else Text(stringResource(R.string.route_empty), style = Dm.type.bodyS, color = Dm.colors.t3, modifier = Modifier.align(Alignment.Center))
            }
            if (attention == 0) {
                Row(
                    Modifier.align(Alignment.End).clip(CircleShape).background(Dm.colors.accentSoft).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(DmIcons.Check, null, tint = Dm.colors.accentText, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.route_badge_ok), style = Dm.type.labelS.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.accentText)
                }
            }
        }
        Panel(Modifier.width(560.dp).fillMaxHeight(), padding = PaddingValues(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 28.dp)) {
            Text(
                stringResource(R.string.panel_timeline), style = Dm.type.bodyS.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.t1,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                TimelineRow(clockTime(stats.startedAtMs), Dm.colors.t3, stringResource(R.string.tl_start), trip.startAddress)
                events.forEach { e -> TimelineRow(clockTime(e.atMs), eventColor(e.type), eventTitle(e.type), eventDetail(e)) }
                TimelineRow(
                    clockTime(trip.arrivedAtMs ?: endMs), Dm.colors.accent,
                    stringResource(R.string.tl_arrive, trip.destination?.label().orEmpty()),
                    trip.parkingSpot.takeIf { it.isNotBlank() }?.let { stringResource(R.string.tl_parked, it) },
                )
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DmButton(stringResource(R.string.btn_lock), onLock, Modifier.weight(1f), kind = DmButtonKind.Basic)
                DmButton(stringResource(R.string.btn_delete), onDelete, kind = DmButtonKind.Text, parkedOnly = true)
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier,
    first: Boolean = false,
    sub: String? = null,
    valueColor: Color = Dm.colors.t1,
) {
    Row(modifier.fillMaxHeight()) {
        if (!first) Box(Modifier.width(1.dp).fillMaxHeight().background(Dm.colors.divider))
        Column(Modifier.padding(start = if (first) 0.dp else 32.dp, end = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = Dm.type.label, color = Dm.colors.t2)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = Dm.type.hL, color = valueColor)
                Text(unit, style = Dm.type.titleS, color = if (valueColor == Dm.colors.t1) Dm.colors.t2 else valueColor, modifier = Modifier.padding(bottom = 6.dp))
            }
            sub?.let { Text(it, style = Dm.type.labelS, color = Dm.colors.t2) }
        }
    }
}

@Composable
private fun TimelineRow(time: String, dot: Color, title: String, sub: String?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(time, style = Dm.type.label, color = Dm.colors.t2, modifier = Modifier.width(68.dp))
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Dm.type.bodyS.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.t1)
            sub?.let { Text(it, style = Dm.type.labelS, color = Dm.colors.t2, maxLines = 1) }
        }
    }
}

@Composable
private fun ColumnScope.SpeedTab(trip: DaeriTrip, samples: List<RoutePoint>) {
    val stats = trip.stats ?: return
    val overspeed = stats.overspeeds
    val hardAccel = stats.hardAccels
    val movingH = stats.movingMs / 3_600_000.0
    val avg = if (movingH > 0) (stats.distanceKm / movingH).roundToInt() else null
    val limited = samples.filter { it.limitKmh != null }
    val compliance = if (limited.isEmpty()) null else limited.count { it.speedKmh <= it.limitKmh!! } * 100 / limited.size
    val attention = overspeed + hardAccel + stats.hardBrakes

    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.speed_chart_title), style = Dm.type.titleS, color = Dm.colors.t1, modifier = Modifier.weight(1f))
                Legend(Dm.colors.info, stringResource(R.string.legend_speed), dashed = false)
                Spacer(Modifier.width(24.dp))
                Legend(Dm.colors.t3, stringResource(R.string.legend_limit), dashed = true)
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(top = 32.dp)) {
                if (samples.size >= 2) SpeedChart(samples, Modifier.fillMaxSize())
                else Text(stringResource(R.string.speed_empty), style = Dm.type.bodyS, color = Dm.colors.t3, modifier = Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.width(560.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(48.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    MiniStat(stringResource(R.string.stat_compliance), compliance?.let { "$it%" } ?: "–", Modifier.weight(1f))
                    MiniStat(stringResource(R.string.stat_avg), avg?.let { "$it" } ?: "–", Modifier.weight(1f), unit = stringResource(R.string.unit_kmh))
                    MiniStat(stringResource(R.string.stat_top), "${stats.topSpeedKmh.roundToInt()}", Modifier.weight(1f), unit = stringResource(R.string.unit_kmh))
                }
            }
            Panel(Modifier.fillMaxWidth().weight(1f), padding = PaddingValues(48.dp)) {
                Text(stringResource(R.string.stat_attention), style = Dm.type.titleS, color = Dm.colors.t1)
                Row(Modifier.padding(vertical = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    IconTile(if (attention == 0) DmIcons.Check else DmIcons.Info, size = 76.dp)
                    Text(
                        if (attention == 0) stringResource(R.string.attention_none) else stringResource(R.string.attention_some, attention),
                        style = Dm.type.title, color = Dm.colors.t1,
                    )
                }
                InfoRow(stringResource(R.string.label_overspeed), stringResource(R.string.fmt_times, overspeed))
                DmDivider(Modifier.padding(vertical = 20.dp))
                InfoRow(stringResource(R.string.label_hard_accel), stringResource(R.string.fmt_times, hardAccel))
                DmDivider(Modifier.padding(vertical = 20.dp))
                InfoRow(stringResource(R.string.label_hard_brake), stringResource(R.string.fmt_times, stats.hardBrakes))
                if (attention == 0) {
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.speed_summary_good), style = Dm.type.bodyL, color = Dm.colors.accentText)
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier, unit: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = Dm.type.labelS, color = Dm.colors.t2, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Dm.type.title, color = Dm.colors.t1)
            unit?.let { Text(" $it", style = Dm.type.labelS.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.t1, modifier = Modifier.padding(bottom = 4.dp)) }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(width = 32.dp, height = 4.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
            )
        }
        Text(label, style = Dm.type.labelS, color = Dm.colors.t2)
    }
}

/**
 * Speed area (blue) with the speed limit as a dashed step line, the top speed marked,
 * and, where road names are known, a strip of road segments under the time axis.
 */
@Composable
private fun SpeedChart(samples: List<RoutePoint>, modifier: Modifier) {
    val line = Dm.colors.info
    val limitColor = Dm.colors.t3
    val grid = Dm.colors.divider
    val t0 = samples.first().atMs
    val t1 = samples.last().atMs.coerceAtLeast(t0 + 1)
    val top = samples.maxBy { it.speedKmh }
    val segments = roadSegments(samples)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            // Y axis labels
            Column(Modifier.width(56.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(120, 80, 40, 0).forEach { Text("$it", style = Dm.type.labelS, color = Dm.colors.t3) }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Canvas(Modifier.fillMaxSize()) {
                    val maxKmh = 120f
                    fun x(t: Long) = (t - t0).toFloat() / (t1 - t0) * size.width
                    fun y(v: Float) = size.height - v.coerceAtMost(maxKmh) / maxKmh * size.height
                    listOf(0f, 40f, 80f, 120f).forEach { v -> drawLine(grid, Offset(0f, y(v)), Offset(size.width, y(v)), strokeWidth = 2f) }
                    val path = Path().apply {
                        samples.forEachIndexed { i, s -> if (i == 0) moveTo(x(s.atMs), y(s.speedKmh)) else lineTo(x(s.atMs), y(s.speedKmh)) }
                    }
                    val area = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0.08f))))
                    drawPath(path, line, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    samples.zipWithNext().forEach { (a, b) ->
                        a.limitKmh?.let { lim ->
                            drawLine(
                                limitColor, Offset(x(a.atMs), y(lim.toFloat())), Offset(x(b.atMs), y(lim.toFloat())), strokeWidth = 4f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                            )
                        }
                    }
                    drawCircle(line, 12f, Offset(x(top.atMs), y(top.speedKmh)))
                    drawCircle(Color.White, 5f, Offset(x(top.atMs), y(top.speedKmh)))
                }
                // Top-speed label above its dot.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val fx = (top.atMs - t0).toFloat() / (t1 - t0)
                    val fy = 1f - top.speedKmh.coerceAtMost(120f) / 120f
                    Text(
                        stringResource(R.string.speed_max_marker, top.speedKmh.roundToInt()),
                        style = Dm.type.labelS.copy(fontWeight = FontWeight.Bold), color = Dm.colors.t1,
                        modifier = Modifier.padding(start = (maxWidth * fx - 64.dp).coerceAtLeast(0.dp), top = (maxHeight * fy - 52.dp).coerceAtLeast(0.dp)),
                    )
                }
            }
        }
        if (segments.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 56.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                segments.forEach { (road, from, to) ->
                    val highlight = road != segments.first().first
                    Box(
                        Modifier.weight(((to - from).coerceAtLeast(1)).toFloat()).height(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (highlight) Dm.colors.infoSoft else Dm.colors.fill),
                        contentAlignment = Alignment.Center,
                    ) { Text(road, style = Dm.type.labelS, color = if (highlight) Dm.colors.info else Dm.colors.t2, maxLines = 1) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 56.dp, top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..4).forEach { i -> Text(clockTime(t0 + (t1 - t0) * i / 4), style = Dm.type.labelS, color = Dm.colors.t3) }
        }
    }
}

/** Consecutive points on the same road, as (road, start ms, end ms). Empty when names are unknown. */
private fun roadSegments(points: List<RoutePoint>): List<Triple<String, Long, Long>> {
    val out = mutableListOf<Triple<String, Long, Long>>()
    points.forEach { p ->
        val road = p.road ?: return emptyList()
        val last = out.lastOrNull()
        if (last != null && last.first == road) out[out.size - 1] = last.copy(third = p.atMs)
        else out += Triple(road, last?.third ?: p.atMs, p.atMs)
    }
    return out
}

@Composable
private fun eventColor(type: EventType): Color = when (type) {
    EventType.OVERSPEED, EventType.HARD_BRAKE -> Dm.colors.danger
    EventType.HARD_ACCEL -> Dm.colors.warn
    EventType.DOOR_OPEN -> Dm.colors.t3
}

@Composable
private fun eventTitle(type: EventType): String = stringResource(
    when (type) {
        EventType.OVERSPEED -> R.string.label_overspeed
        EventType.HARD_ACCEL -> R.string.label_hard_accel
        EventType.HARD_BRAKE -> R.string.label_hard_brake
        EventType.DOOR_OPEN -> R.string.tl_door
    }
)

@Composable
private fun eventDetail(e: TripEvent): String? = when (e.type) {
    EventType.DOOR_OPEN -> null
    EventType.OVERSPEED -> stringResource(R.string.tl_speed_limit, (e.maxKmh ?: e.speedKmh).roundToInt(), e.limitKmh ?: 0)
        .takeIf { e.limitKmh != null } ?: stringResource(R.string.tl_speed, (e.maxKmh ?: e.speedKmh).roundToInt())
    else -> stringResource(R.string.tl_speed, e.speedKmh.roundToInt())
}

/** "어젯밤" for a 시연 모드 trip (the demo tells the next-morning story), else from the trip's date. */
@Composable
private fun tripDay(trip: DaeriTrip): String = when {
    trip.demo -> stringResource(R.string.day_last_night)
    else -> trip.stats?.let { dayWord(it.startedAtMs) } ?: stringResource(R.string.day_today)
}
