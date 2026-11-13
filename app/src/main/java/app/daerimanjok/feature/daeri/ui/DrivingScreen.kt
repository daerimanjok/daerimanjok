package app.daerimanjok.feature.daeri.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.daerimanjok.R
import app.daerimanjok.core.car.Gear
import app.daerimanjok.core.navi.Remaining
import app.daerimanjok.core.valueOrNull
import app.daerimanjok.feature.daeri.DaeriTrip
import app.daerimanjok.ui.CarStatus
import app.daerimanjok.ui.components.DmButton
import app.daerimanjok.ui.components.DmButtonKind
import app.daerimanjok.ui.components.DmDivider
import app.daerimanjok.ui.components.Panel
import app.daerimanjok.ui.components.TopBar
import app.daerimanjok.ui.theme.Dm
import kotlin.math.roundToInt

/**
 * S3 안심 운행. Minimal while driving: no popups, nothing to read beyond a line or two.
 *
 * Wide (app alone next to the driving view): the prototype layout, route panel + card.
 * Narrow (840, beside the navigation app): one column with the same information.
 * The route is our own sketch from recorded points; turn-by-turn stays in navigation.
 */
@Composable
fun DrivingScreen(
    trip: DaeriTrip,
    status: CarStatus,
    speedLimitKmh: Int?,
    remaining: Remaining?,
    routePoints: List<Offset>,
    onArrived: () -> Unit,
) {
    val parked = status.gear.valueOrNull() == Gear.PARK
    Column(Modifier.fillMaxSize()) {
        TopBar("")
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, bottom = 40.dp)) {
            if (maxWidth >= 1200.dp) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(36.dp)).background(Dm.colors.mapLand)) {
                        RouteSketch(routePoints, Modifier.fillMaxSize(), planned = if (trip.demo) demoRoute else emptyList(), mockMap = trip.demo, padding = 80.dp)
                        Row(
                            Modifier.align(Alignment.BottomStart).padding(32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            LimitSign(speedLimitKmh)
                            SpeedBubble(status.speedKmh.valueOrNull())
                        }
                        EtaCard(remaining, Modifier.align(Alignment.BottomEnd).padding(32.dp))
                    }
                    StatusCard(trip, remaining, parked, onArrived, Modifier.width(520.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LimitSign(speedLimitKmh)
                        SpeedBubble(status.speedKmh.valueOrNull())
                    }
                    EtaCard(remaining, Modifier.fillMaxWidth())
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(36.dp)).background(Dm.colors.mapLand)) {
                        RouteSketch(routePoints, Modifier.fillMaxSize(), planned = if (trip.demo) demoRoute else emptyList(), mockMap = trip.demo, padding = 48.dp)
                    }
                    StatusCard(trip, remaining, parked, onArrived, Modifier.fillMaxWidth(), compact = true)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    trip: DaeriTrip,
    remaining: Remaining?,
    parked: Boolean,
    onArrived: () -> Unit,
    modifier: Modifier,
    compact: Boolean = false,
) {
    Panel(modifier, padding = PaddingValues(if (compact) 40.dp else 56.dp)) {
        Text(stringResource(R.string.drive_title), style = if (compact) Dm.type.title else Dm.type.hL, color = Dm.colors.t1)
        if (!compact) {
            Text(stringResource(R.string.drive_desc), style = Dm.type.bodyL, color = Dm.colors.t2, modifier = Modifier.padding(top = 20.dp))
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.drive_dest), style = Dm.type.label.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.t2)
        val dest = trip.destination
        Text(dest?.address?.ifBlank { null } ?: dest?.label().orEmpty(), style = Dm.type.title, color = Dm.colors.t1, modifier = Modifier.padding(top = 8.dp))
        Text(
            remaining?.let { stringResource(R.string.drive_eta_line, clockTime(System.currentTimeMillis() + it.durationSec * 1000L), formatDistanceM(it.distanceM)) }
                ?: stringResource(R.string.drive_waiting),
            style = Dm.type.bodyL, color = Dm.colors.t2, modifier = Modifier.padding(top = 12.dp),
        )
        // Fallback when navigation never reports arrival (and on the emulator, where it can't).
        if (parked) {
            Spacer(Modifier.height(32.dp))
            DmButton(stringResource(R.string.drive_arrived), onArrived, Modifier.fillMaxWidth(), kind = DmButtonKind.Basic, parkedOnly = true)
        }
    }
}

/** Red-ring speed-limit sign. Blank ring when the limit is unknown. */
@Composable
private fun LimitSign(limitKmh: Int?) {
    val label = limitKmh?.let { stringResource(R.string.cd_speed_limit, it) }.orEmpty()
    Box(
        Modifier
            .size(112.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(12.dp, Dm.colors.dangerMark, CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(limitKmh?.toString() ?: "–", style = Dm.type.title.copy(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold), color = Color(0xFF131417))
    }
}

@Composable
private fun SpeedBubble(kmh: Float?) {
    Row(
        Modifier.height(112.dp).clip(RoundedCornerShape(28.dp)).background(Dm.colors.surface).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(kmh?.roundToInt()?.toString() ?: "–", style = Dm.type.hXl.copy(fontSize = 72.sp, lineHeight = 80.sp), color = Dm.colors.t1)
        Text(stringResource(R.string.unit_kmh), style = Dm.type.bodyL, color = Dm.colors.t2, modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable
private fun EtaCard(remaining: Remaining?, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(28.dp)).background(Dm.colors.surface).padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        val now = System.currentTimeMillis()
        EtaItem(stringResource(R.string.label_arrival), remaining?.let { clockTime(now + it.durationSec * 1000L) } ?: "–")
        EtaItem(stringResource(R.string.label_remaining_dist), remaining?.let { formatDistanceM(it.distanceM) } ?: "–")
        EtaItem(stringResource(R.string.label_remaining_time), remaining?.let { formatDuration(it.durationSec * 1000L) } ?: "–")
    }
}

@Composable
private fun EtaItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = Dm.type.label, color = Dm.colors.t2)
        Text(value, style = Dm.type.title, color = Dm.colors.t1)
    }
}
