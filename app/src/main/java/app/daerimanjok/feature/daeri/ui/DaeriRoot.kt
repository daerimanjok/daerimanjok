package app.daerimanjok.feature.daeri.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.daerimanjok.feature.daeri.DaeriPhase
import app.daerimanjok.ui.MainViewModel

/** Picks the screen from the trip phase (see [DaeriPhase]) and the owner's setup. */
@Composable
fun DaeriRoot(vm: MainViewModel) {
    val d = vm.daeri
    val loaded by d.loaded.collectAsStateWithLifecycle()
    val setup by d.setup.collectAsStateWithLifecycle()
    val trip by d.trip.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val speedLimit by vm.speedLimitKmh.collectAsStateWithLifecycle()
    val remaining by vm.remaining.collectAsStateWithLifecycle()
    val carModel by vm.carModel.collectAsStateWithLifecycle()
    val routeSketch by vm.routeSketch.collectAsStateWithLifecycle()

    // Setup stays open until 설정 완료, even once PIN and destination make it "complete".
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var setupStep by rememberSaveable { mutableIntStateOf(0) }
    // "운행이 완료되었어요" shows once, right after the driver ends the trip.
    var justEnded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(loaded, setup.complete) {
        if (loaded && !setup.complete && !setupOpen) {
            setupStep = if (!setup.hasPin) 0 else 1
            setupOpen = true
        }
    }

    if (!loaded) return

    when (trip.phase) {
        DaeriPhase.IDLE -> if (setupOpen || !setup.complete) {
            BackHandler(enabled = setup.complete) { setupOpen = false }
            SetupScreen(
                setup = setup,
                places = places,
                startStep = setupStep,
                onSetPin = d::setPin,
                onSetDestination = d::setDestination,
                onSetNotes = d::setNotes,
                onAddHome = vm::openAddHome,
                onDone = { setupOpen = false },
                onBack = if (setup.complete) ({ setupOpen = false }) else null,
            )
        } else {
            CallScreen(
                setup = setup,
                onStart = d::startDaeriMode,
                onChangeDestination = { setupStep = 1; setupOpen = true },
                onEditSetup = { setupStep = 0; setupOpen = true },
            )
        }
        DaeriPhase.BRIEFING -> {
            BackHandler { d.cancelDaeriMode() }
            BriefingScreen(trip, setup, status, carModel, onStartDriving = d::startDriving, onBack = d::cancelDaeriMode)
        }
        DaeriPhase.DRIVING -> DrivingScreen(trip, status, speedLimit, remaining, routeSketch, onArrived = d::markArrived)
        DaeriPhase.ARRIVED -> ReturnScreen(trip, setup, status, onEndTrip = { d.endTrip(); justEnded = true })
        DaeriPhase.ENDED -> if (justEnded) TripDoneScreen { justEnded = false }
        else {
            val id = trip.tripId ?: 0L
            val points by remember(id) { d.points(id) }.collectAsStateWithLifecycle(emptyList())
            val events by remember(id) { d.events(id) }.collectAsStateWithLifecycle(emptyList())
            ReportScreen(trip, points, events, checkPin = d::checkPin, onDelete = d::deleteReport)
        }
    }
}
