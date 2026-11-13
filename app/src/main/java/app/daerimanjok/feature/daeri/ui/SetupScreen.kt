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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.daerimanjok.R
import app.daerimanjok.core.restrictions.DriveMode
import app.daerimanjok.feature.daeri.OwnerSetup
import app.daerimanjok.feature.daeri.Pin
import app.daerimanjok.feature.daeri.Place
import app.daerimanjok.ui.components.ContentPadding
import app.daerimanjok.ui.components.DmButton
import app.daerimanjok.ui.components.DmButtonKind
import app.daerimanjok.ui.components.DmField
import app.daerimanjok.ui.components.DmIconButton
import app.daerimanjok.ui.components.DmIcons
import app.daerimanjok.ui.components.LocalDemoMode
import app.daerimanjok.ui.components.IconLine
import app.daerimanjok.ui.components.Keypad
import app.daerimanjok.ui.components.LocalDriveMode
import app.daerimanjok.ui.components.Panel
import app.daerimanjok.ui.components.PinDots
import app.daerimanjok.ui.components.RadioRow
import app.daerimanjok.ui.components.StepItem
import app.daerimanjok.ui.components.TopBar
import app.daerimanjok.ui.components.TwoColumn
import app.daerimanjok.ui.theme.Dm

/** S0 대리 준비: owner PIN → destination → parking note. Parked only. */
@Composable
fun SetupScreen(
    setup: OwnerSetup,
    places: List<Place>,
    startStep: Int,
    onSetPin: (String) -> Unit,
    onSetDestination: (Place) -> Unit,
    onSetNotes: (parkingSpot: String, note: String, contact: String) -> Unit,
    onAddHome: () -> Unit,
    onDone: () -> Unit,
    onBack: (() -> Unit)?,
) {
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var pin by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(setup.destination?.let(::placeKey)) }
    // 시연 모드 starts with a sample parking request (the emulator can't type Korean).
    val demoSpot = stringResource(R.string.demo_parking_spot)
    val demo = LocalDemoMode.current
    var spot by rememberSaveable { mutableStateOf(setup.parkingSpot.ifBlank { if (demo) demoSpot else "" }) }
    var note by rememberSaveable { mutableStateOf(setup.note) }
    var contact by rememberSaveable { mutableStateOf(setup.contact) }
    val parked = LocalDriveMode.current == DriveMode.PARKED

    val pinOk = Pin.isValid(pin) || (pin.isEmpty() && setup.hasPin)
    val pickedPlace = places.firstOrNull { placeKey(it) == picked } ?: setup.destination?.takeIf { placeKey(it) == picked }
    val canNext = parked && when (step) {
        0 -> pinOk
        1 -> pickedPlace != null
        else -> true
    }

    fun next() {
        when (step) {
            0 -> if (Pin.isValid(pin)) onSetPin(pin).also { pin = "" }
            1 -> pickedPlace?.let(onSetDestination)
        }
        if (step < 2) step++ else {
            onSetNotes(spot, note, contact)
            onDone()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("", onBack = onBack)
        TwoColumn(Modifier.fillMaxSize().padding(ContentPadding), left = {
            Text(stringResource(R.string.setup_headline), style = Dm.type.hL, color = Dm.colors.t1, modifier = Modifier.padding(top = 24.dp))
            Spacer(Modifier.height(64.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val destLabel = pickedPlace?.label()
                StepItem(
                    1, stringResource(R.string.setup_step_pin),
                    stringResource(if (setup.hasPin) R.string.setup_step_pin_done else R.string.setup_step_pin_todo),
                    done = setup.hasPin, current = step == 0, onClick = { step = 0 },
                )
                StepItem(
                    2, stringResource(R.string.setup_step_dest), destLabel ?: stringResource(R.string.setup_step_dest_todo),
                    done = setup.destination != null, current = step == 1, onClick = { if (setup.hasPin) step = 1 },
                )
                StepItem(
                    3, stringResource(R.string.setup_step_note), spot.ifBlank { stringResource(R.string.setup_step_note_todo) },
                    done = setup.parkingSpot.isNotBlank(), current = step == 2, onClick = { if (setup.destination != null) step = 2 },
                )
            }
            Spacer(Modifier.weight(1f))
            IconLine(DmIcons.Info, stringResource(R.string.setup_parked_only), iconSize = 28.dp)
        }) {
            Panel(Modifier.fillMaxSize(), padding = PaddingValues(start = 64.dp, end = 64.dp, top = 56.dp, bottom = 48.dp)) {
                Column(Modifier.weight(1f)) {
                    when (step) {
                        0 -> PinStep(pin, setup.hasPin, parked) { pin = it }
                        1 -> DestinationStep(places, picked, onPick = { picked = placeKey(it) }, onAddHome = onAddHome)
                        else -> NoteStep(spot, note, contact, { spot = it }, { note = it }, { contact = it })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) DmButton(stringResource(R.string.btn_prev), { step-- }, kind = DmButtonKind.Basic)
                    Spacer(Modifier.weight(1f))
                    DmButton(
                        stringResource(if (step == 2) R.string.setup_done else R.string.btn_next),
                        ::next,
                        modifier = Modifier.widthIn(min = 280.dp),
                        enabled = canNext,
                        parkedOnly = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun PinStep(pin: String, alreadySet: Boolean, parked: Boolean, onPin: (String) -> Unit) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.setup_pin_title), style = Dm.type.title, color = Dm.colors.t1)
            PinDots(pin.length, modifier = Modifier.padding(top = 28.dp))
            val msg = when {
                Pin.isValid(pin) -> stringResource(R.string.setup_pin_set)
                alreadySet -> stringResource(R.string.setup_pin_change)
                else -> stringResource(R.string.setup_pin_prompt)
            }
            Text(
                msg,
                style = Dm.type.label.copy(fontWeight = FontWeight.SemiBold),
                color = if (Pin.isValid(pin)) Dm.colors.accentText else Dm.colors.t2,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Keypad(pin, onPin, Modifier.width(460.dp), enabled = parked)
    }
}

@Composable
private fun DestinationStep(places: List<Place>, picked: String?, onPick: (Place) -> Unit, onAddHome: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.setup_dest_title), style = Dm.type.title, color = Dm.colors.t1)
        if (places.isEmpty()) {
            Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.setup_dest_empty), style = Dm.type.titleS, color = Dm.colors.t1)
                Text(stringResource(R.string.setup_dest_empty_desc), style = Dm.type.body, color = Dm.colors.t2)
                DmButton(stringResource(R.string.setup_dest_add), onAddHome, Modifier.padding(top = 20.dp), kind = DmButtonKind.Basic, parkedOnly = true)
            }
        } else {
            Column(
                Modifier.padding(top = 28.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                places.forEach { p ->
                    val selected = placeKey(p) == picked
                    RadioRow(p.label(), p.address, selected = selected, onClick = { onPick(p) }) {
                        // Editing a saved place happens in the navigation app, where it lives.
                        if (selected) DmIconButton(DmIcons.Edit, stringResource(R.string.cd_edit_place), onAddHome)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteStep(
    spot: String,
    note: String,
    contact: String,
    onSpot: (String) -> Unit,
    onNote: (String) -> Unit,
    onContact: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.setup_note_title), style = Dm.type.title, color = Dm.colors.t1)
        // One field per row, like the prototype: the note gets the full width for its sentence.
        DmField(stringResource(R.string.field_parking), spot, { onSpot(it.take(40)) }, Modifier.fillMaxWidth().padding(top = 28.dp), stringResource(R.string.field_parking_hint))
        DmField(stringResource(R.string.field_note), note, { onNote(it.take(80)) }, Modifier.fillMaxWidth().padding(top = 8.dp), stringResource(R.string.field_note_hint))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            DmField(
                stringResource(R.string.field_contact), contact, { v -> onContact(v.filter { it.isDigit() || it == '-' }.take(13)) },
                Modifier.weight(1f), stringResource(R.string.field_contact_hint),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Places come back from navigation as new objects; compare by position and POI. */
private fun placeKey(p: Place) = "${p.poiId}@${p.latitude},${p.longitude}"
