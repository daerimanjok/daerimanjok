package app.daerimanjok.feature.daeri.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.daerimanjok.R
import app.daerimanjok.core.car.Gear
import app.daerimanjok.core.valueOrNull
import app.daerimanjok.feature.daeri.DaeriTrip
import app.daerimanjok.feature.daeri.OwnerSetup
import app.daerimanjok.ui.CarStatus
import app.daerimanjok.ui.components.ContentPadding
import app.daerimanjok.ui.components.DmButton
import app.daerimanjok.ui.components.DmDivider
import app.daerimanjok.ui.components.DmIcons
import app.daerimanjok.ui.components.DoneChip
import app.daerimanjok.ui.components.IconLine
import app.daerimanjok.ui.components.IconTile
import app.daerimanjok.ui.components.InfoRow
import app.daerimanjok.ui.components.Panel
import app.daerimanjok.ui.components.TopBar
import app.daerimanjok.ui.theme.Dm

/** S4 차량 반환: the owner's parking spot, big, then 운행 종료. */
@Composable
fun ReturnScreen(trip: DaeriTrip, setup: OwnerSetup, status: CarStatus, onEndTrip: () -> Unit) {
    val parked = status.gear.valueOrNull() == Gear.PARK
    val stats = trip.stats
    Column(Modifier.fillMaxSize()) {
        TopBar("", stateText = if (parked) stringResource(R.string.state_parked_done) else null)
        // Parking spot and card as one centred group: the same free space on both sides.
        Row(
            Modifier.fillMaxSize().padding(ContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(160.dp, Alignment.CenterHorizontally),
        ) {
            Column(Modifier.weight(1f, fill = false)) {
                DoneChip(stringResource(R.string.return_chip))
                Spacer(Modifier.height(48.dp))
                Text(stringResource(R.string.return_label), style = Dm.type.bodyL, color = Dm.colors.t2)
                Spacer(Modifier.height(24.dp))
                Text(
                    trip.parkingSpot.ifBlank { stringResource(R.string.return_no_spot) },
                    style = if (trip.parkingSpot.isBlank()) Dm.type.hL
                    else Dm.type.hXl.copy(fontSize = 120.sp, lineHeight = 136.sp),
                    color = if (trip.parkingSpot.isBlank()) Dm.colors.t2 else Dm.colors.t1,
                )
                if (setup.note.isNotBlank()) IconLine(DmIcons.Info, setup.note, Modifier.padding(top = 48.dp), iconSize = 40.dp)
            }
            Panel(Modifier.width(560.dp), padding = PaddingValues(56.dp)) {
                Text(stringResource(R.string.return_panel_title), style = Dm.type.titleS, color = Dm.colors.t1)
                DmDivider(Modifier.padding(vertical = 32.dp))
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    stats?.let {
                        InfoRow(stringResource(R.string.label_duration), formatDuration(it.durationMs(trip.arrivedAtMs ?: System.currentTimeMillis())))
                        InfoRow(stringResource(R.string.label_distance), stringResource(R.string.fmt_km, oneDecimal(it.distanceKm)))
                    }
                }
                Spacer(Modifier.height(48.dp))
                DmButton(stringResource(R.string.return_end), onEndTrip, Modifier.fillMaxWidth(), parkedOnly = true)
            }
        }
    }
}

/** Shown once after 운행 종료, before the report locks. */
@Composable
fun TripDoneScreen(onOk: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("", stateText = stringResource(R.string.state_parked_done))
        Column(
            Modifier.fillMaxSize().padding(ContentPadding).padding(start = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            IconTile(DmIcons.Lock, size = 104.dp)
            Text(stringResource(R.string.done_title), style = Dm.type.hXl, color = Dm.colors.t1, modifier = Modifier.padding(top = 40.dp))
            Text(stringResource(R.string.done_thanks), style = Dm.type.bodyL, color = Dm.colors.accentText, modifier = Modifier.padding(top = 20.dp))
            DmButton(stringResource(R.string.btn_ok), onOk, Modifier.padding(top = 64.dp).widthIn(min = 280.dp))
        }
    }
}
