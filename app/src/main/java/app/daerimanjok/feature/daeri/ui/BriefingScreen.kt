package app.daerimanjok.feature.daeri.ui

import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import android.annotation.SuppressLint
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.daerimanjok.R
import app.daerimanjok.core.Signal
import app.daerimanjok.core.car.Gear
import app.daerimanjok.core.valueOrNull
import app.daerimanjok.feature.daeri.CarGuide
import app.daerimanjok.feature.daeri.CarModel
import app.daerimanjok.feature.daeri.DaeriTrip
import app.daerimanjok.feature.daeri.OwnerSetup
import app.daerimanjok.ui.CarStatus
import app.daerimanjok.ui.components.ContentPadding
import app.daerimanjok.ui.components.DmButton
import app.daerimanjok.ui.components.DmDivider
import app.daerimanjok.ui.components.DmIcons
import app.daerimanjok.ui.components.DmProgress
import app.daerimanjok.ui.components.ImagePlaceholder
import app.daerimanjok.ui.components.InfoRow
import app.daerimanjok.ui.components.Panel
import app.daerimanjok.ui.components.SegmentedTabs
import app.daerimanjok.ui.components.TopBar
import app.daerimanjok.ui.components.TwoColumn
import app.daerimanjok.ui.theme.Dm

/** S2 차량 인계: what the driver needs before setting off. Parked. */
@Composable
fun BriefingScreen(
    trip: DaeriTrip,
    setup: OwnerSetup,
    status: CarStatus,
    carModel: CarModel,
    onStartDriving: () -> Unit,
    onBack: () -> Unit,
) {
    val dest = trip.destination ?: setup.destination
    val destLabel = dest?.label().orEmpty()
    val tips = CarGuide.tips(carModel)
    var selected by rememberSaveable { mutableStateOf(tips.first().control) }
    val tip = tips.first { it.control == selected }

    Column(Modifier.fillMaxSize()) {
        TopBar("", onBack = onBack)
        TwoColumn(Modifier.fillMaxSize().padding(ContentPadding), left = {
            Text(stringResource(R.string.brief_headline), style = Dm.type.hL, color = Dm.colors.t1)
            Spacer(Modifier.height(48.dp))
            Label(stringResource(R.string.brief_dest))
            // The driver needs the address, not the owner's name for the place.
            Text(dest?.address?.ifBlank { null } ?: destLabel, style = Dm.type.title, color = Dm.colors.t1, modifier = Modifier.padding(top = 8.dp))
            DmDivider(Modifier.padding(vertical = 32.dp))
            Label(stringResource(R.string.brief_parking))
            Text(
                trip.parkingSpot.ifBlank { stringResource(R.string.return_no_spot) },
                style = Dm.type.title,
                color = if (trip.parkingSpot.isBlank()) Dm.colors.t2 else Dm.colors.t1,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (setup.note.isNotBlank()) Text(setup.note, style = Dm.type.bodyS, color = Dm.colors.t2, modifier = Modifier.padding(top = 8.dp))
            DmDivider(Modifier.padding(vertical = 32.dp))
            Label(stringResource(R.string.brief_model))
            Text(stringResource(carModel.displayName), style = Dm.type.title, color = Dm.colors.t1, modifier = Modifier.padding(top = 8.dp))
            DmDivider(Modifier.padding(vertical = 32.dp))
            Label(stringResource(R.string.brief_status))
            Spacer(Modifier.height(20.dp))
            CarStatusRows(status)
        }) {
            Column(Modifier.fillMaxSize()) {
                SegmentedTabs(
                    items = tips.map { it.control },
                    selected = selected,
                    label = { c -> stringResource(tips.first { it.control == c }.tab) },
                    onSelect = { selected = it },
                    modifier = Modifier.fillMaxWidth(),
                    fill = true,
                )
                Spacer(Modifier.height(32.dp))
                Panel(Modifier.fillMaxWidth().weight(1f), padding = PaddingValues(40.dp)) {
                    val picture = drawableNamed(tip.picture)
                    if (picture == 0) {
                        ImagePlaceholder(stringResource(R.string.brief_photo, stringResource(tip.tab)), Modifier.fillMaxWidth().weight(1f))
                    } else {
                        // Manual-style drawings on white: keep the white card in both themes so they read the same.
                        Image(
                            painterResource(picture),
                            contentDescription = stringResource(R.string.brief_photo, stringResource(tip.tab)),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(28.dp)).background(Color.White),
                        )
                    }
                    Spacer(Modifier.height(40.dp))
                    Text(stringResource(tip.where), style = Dm.type.bodyL.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.accentText)
                    Text(stringResource(tip.how), style = Dm.type.title, color = Dm.colors.t1, modifier = Modifier.padding(top = 12.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalArrangement = Arrangement.End) {
                    DmButton(stringResource(R.string.brief_start), onStartDriving, parkedOnly = true, trailingIcon = DmIcons.ArrowRight)
                }
            }
        }
    }
}

/** Drawable id by name, or 0 when this build has no such picture (see ControlTip.picture). */
@SuppressLint("DiscouragedApi")
@Composable
private fun drawableNamed(name: String?): Int {
    val context = LocalContext.current
    return remember(name) { name?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0 }
}

@Composable
private fun Label(text: String) = Text(text, style = Dm.type.label.copy(fontWeight = FontWeight.SemiBold), color = Dm.colors.t2)

@Composable
private fun CarStatusRows(s: CarStatus) {
    val na = stringResource(R.string.value_na)
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // The supported car (Grandeur) is a petrol car: fuel only, no EV battery.
        s.energyPercent?.takeIf { !s.isEv }?.let { pct ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoRow(stringResource(R.string.status_fuel), "$pct%")
                DmProgress(pct / 100f, color = if (pct <= 20) Dm.colors.warnMark else Dm.colors.accent)
            }
        }
        InfoRow(stringResource(R.string.status_gear), s.gear.valueOrNull()?.let { gearLabel(it) } ?: na)
        if (s.parkingBrake is Signal.Value) {
            InfoRow(
                stringResource(R.string.status_parking_brake),
                stringResource(if ((s.parkingBrake as Signal.Value<Boolean>).value) R.string.brake_on else R.string.brake_off),
            )
        }
        s.drivingMode.valueOrNull()?.let { InfoRow(stringResource(R.string.status_drive_mode), modeLabel(it)) }
    }
}

@Composable
private fun gearLabel(g: Gear): String? = when (g) {
    Gear.PARK -> stringResource(R.string.gear_p)
    Gear.REVERSE -> stringResource(R.string.gear_r)
    Gear.NEUTRAL -> stringResource(R.string.gear_n)
    Gear.DRIVE -> stringResource(R.string.gear_d)
    Gear.UNKNOWN -> null
}

@Composable
private fun modeLabel(m: DrivingModeType): String = when (m) {
    DrivingModeType.ECO -> stringResource(R.string.mode_eco)
    DrivingModeType.SPORT -> stringResource(R.string.mode_sport)
    else -> stringResource(R.string.mode_normal)
}
