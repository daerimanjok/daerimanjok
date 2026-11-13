package app.daerimanjok.feature.daeri

import android.util.Log
import app.daerimanjok.core.car.CarSource
import app.daerimanjok.core.car.Gear
import app.daerimanjok.core.navi.NaviEvent
import app.daerimanjok.core.navi.NaviSource
import app.daerimanjok.core.navi.Remaining
import app.daerimanjok.core.valueOrNull
import app.daerimanjok.core.vehicle.VehicleSource
import app.daerimanjok.feature.daeri.demo.DemoDrive
import app.daerimanjok.feature.daeri.record.EventRow
import app.daerimanjok.feature.daeri.record.RoutePoint
import app.daerimanjok.feature.daeri.record.TripDao
import app.daerimanjok.feature.daeri.record.TripEvent
import app.daerimanjok.feature.daeri.record.TripRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What 시연 모드 is "driving" right now; the screens show this instead of the car's signals. */
data class DemoLive(
    val speedKmh: Float,
    val limitKmh: Int?,
    val gear: Gear,
    val remaining: Remaining,
)

/**
 * 대리만족's brain: owner setup plus the trip state machine (see [DaeriPhase]).
 *
 * Lives at app scope (see AppContainer) so recording continues while the driver has the
 * navigation app in front. Setup and trip are persisted (DataStore), the route and events in
 * the trip database, so an app restart mid-trip resumes where it was.
 */
class DaeriController(
    private val scope: CoroutineScope,
    private val store: DaeriStore,
    private val dao: TripDao,
    private val vehicle: VehicleSource,
    private val car: CarSource,
    private val navi: NaviSource,
    /** Settings → 시연 모드, read when a trip starts. */
    private val demoMode: StateFlow<Boolean>,
    /** Called with true when recording starts and false when it stops (drives the foreground service). */
    private val onRecordingChanged: (Boolean) -> Unit = {},
    /** Called once when the car arrives, with the parking spot to show the driver. */
    private val onArrived: (parkingSpot: String) -> Unit = {},
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val _setup = MutableStateFlow(OwnerSetup())
    val setup: StateFlow<OwnerSetup> = _setup.asStateFlow()

    private val _trip = MutableStateFlow(DaeriTrip())
    val trip: StateFlow<DaeriTrip> = _trip.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** false until saved data has been read; the UI waits for it to avoid flashing the wrong screen. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _routePoints = MutableStateFlow<List<RoutePoint>>(emptyList())
    /** Route recorded so far in the current trip, for the live sketch. */
    val routePoints: StateFlow<List<RoutePoint>> = _routePoints.asStateFlow()

    private val _demoLive = MutableStateFlow<DemoLive?>(null)
    /** Non-null while 시연 모드 plays a drive. */
    val demoLive: StateFlow<DemoLive?> = _demoLive.asStateFlow()

    private var recording: Job? = null
    private var accumulator: TripAccumulator? = null
    private var recorder: TripRecorder? = null
    /** Drive time of the demo; trip times follow it so the report reads like the real drive. */
    private var demoNow: Long? = null

    private fun now(): Long = demoNow ?: wallClock()

    init {
        scope.launch {
            _setup.value = store.loadSetup()
            val saved = store.loadTrip()
            _trip.value = saved
            // Only the current trip's rows are kept.
            runCatching { dao.deleteOtherPoints(saved.tripId ?: -1); dao.deleteOtherEvents(saved.tripId ?: -1) }
            _loaded.value = true
            when {
                // A demo interrupted by an app restart plays again from the start, on a clean slate.
                saved.demo && saved.phase == DaeriPhase.DRIVING -> {
                    val stats = TripStats(startedAtMs = saved.stats?.startedAtMs ?: wallClock())
                    runCatching { dao.deletePoints(stats.startedAtMs); dao.deleteEvents(stats.startedAtMs) }
                    commit(saved.copy(stats = stats))
                    playDemo(stats)
                }
                // A demo that has arrived has nothing left to record.
                saved.demo && saved.phase == DaeriPhase.ARRIVED -> Unit
                saved.phase == DaeriPhase.DRIVING || saved.phase == DaeriPhase.ARRIVED ->
                    startRecording(saved.stats ?: TripStats(startedAtMs = wallClock()), resume = true)
            }
        }
    }

    // --- 대리 준비 (owner setup, parked only) ---

    fun setPin(pin: String) {
        require(Pin.isValid(pin)) { "PIN must be ${Pin.LENGTH} digits" }
        val salt = Pin.newSalt()
        saveSetup(_setup.value.copy(pinHash = Pin.hash(pin, salt), pinSalt = salt))
    }

    fun setDestination(place: Place) = saveSetup(_setup.value.copy(destination = place))

    fun setNotes(parkingSpot: String, note: String, contact: String) = saveSetup(
        _setup.value.copy(parkingSpot = parkingSpot.trim(), note = note.trim(), contact = contact.trim())
    )

    fun checkPin(pin: String): Boolean {
        val s = _setup.value
        return Pin.matches(pin, s.pinSalt ?: return false, s.pinHash ?: return false)
    }

    // --- Trip ---

    /** 대리 모드 시작: show the driver briefing. Remembers where the trip starts. */
    fun startDaeriMode() {
        val s = _setup.value
        if (!s.complete || _trip.value.phase != DaeriPhase.IDLE) return
        commit(DaeriTrip(phase = DaeriPhase.BRIEFING, destination = s.destination, parkingSpot = s.parkingSpot))
        navi.refresh()
        scope.launch {
            val here = withTimeoutOrNull(3_000) { navi.location.first { it != null } }
            here?.address?.takeIf { it.isNotBlank() }?.let { addr ->
                _trip.update { if (it.phase == DaeriPhase.BRIEFING) it.copy(startAddress = addr) else it }
            }
        }
    }

    /** Back out of the briefing before the trip starts. */
    fun cancelDaeriMode() {
        if (_trip.value.phase == DaeriPhase.BRIEFING) commit(DaeriTrip())
    }

    /** 운행 시작: route to the destination and start recording (or play the demo drive). */
    fun startDriving() {
        val t = _trip.value
        if (t.phase != DaeriPhase.BRIEFING) return
        val demo = demoMode.value
        val stats = TripStats(startedAtMs = wallClock())
        commit(
            t.copy(
                phase = DaeriPhase.DRIVING, stats = stats, demo = demo,
                startAddress = if (demo) DemoDrive.START_ADDRESS else t.startAddress,
            )
        )
        if (demo) {
            playDemo(stats)
        } else {
            startRecording(stats, resume = false)
            t.destination?.let { navi.routeTo(it.latitude, it.longitude, it.name, it.address, it.poiId, it.poiSubId) }
        }
    }

    /** Navigation says we're there, or the driver taps 도착했어요. */
    fun markArrived() {
        val t = _trip.value
        if (t.phase != DaeriPhase.DRIVING) return
        recorder?.onMarker(now())
        commit(t.copy(phase = DaeriPhase.ARRIVED, arrivedAtMs = now()))
        onArrived(t.parkingSpot)
    }

    /** 운행 종료: save the trip and lock it behind the owner's PIN. */
    fun endTrip() {
        val t = _trip.value
        if (t.phase != DaeriPhase.DRIVING && t.phase != DaeriPhase.ARRIVED) return
        val end = now()
        val acc = accumulator
        val rec = recorder
        rec?.finish(end)?.forEach { acc?.onEvent(it.type) }
        val finished = acc?.finish(end) ?: (t.stats ?: TripStats(startedAtMs = end)).copy(endedAtMs = end)
        stopRecording()
        rec?.let { scope.launch { flush(it) } }
        // No cancelRoute(): navigation ends guidance on arrival by itself, and cancelling
        // brings the navigation app to the front over the "운행 완료" screen.
        commit(t.copy(phase = DaeriPhase.ENDED, stats = finished, arrivedAtMs = t.arrivedAtMs ?: end))
    }

    /** 기록 삭제: the owner has read the report. Setup (PIN, destination, notes) is kept. */
    fun deleteReport() {
        val t = _trip.value
        if (t.phase != DaeriPhase.ENDED) return
        t.tripId?.let { id -> scope.launch { runCatching { dao.deletePoints(id); dao.deleteEvents(id) } } }
        _routePoints.value = emptyList()
        commit(DaeriTrip())
    }

    /**
     * Settings → 처음부터 다시 시작: forget the owner setup, any trip and its record, and go back
     * to 대리 준비. Screen settings (theme, language, 시연 모드) are kept.
     */
    fun resetAll() {
        stopRecording()
        _routePoints.value = emptyList()
        _setup.value = OwnerSetup()
        _trip.value = DaeriTrip()
        scope.launch {
            runCatching {
                store.saveSetup(OwnerSetup())
                store.saveTrip(DaeriTrip())
                dao.deleteAllPoints()
                dao.deleteAllEvents()
            }.onFailure { Log.w(TAG, "reset failed", it) }
        }
    }

    /** Recorded route of a trip (report). */
    fun points(tripId: Long): Flow<List<RoutePoint>> = dao.points(tripId)

    /** Recorded events of a trip (report). */
    fun events(tripId: Long): Flow<List<TripEvent>> = dao.events(tripId).map { rows -> rows.map(EventRow::toEvent) }

    // --- Recording ---

    private fun startRecording(initial: TripStats, resume: Boolean) {
        stopRecording()
        onRecordingChanged(true)
        val tripId = initial.startedAtMs
        val acc = TripAccumulator(initial).also { accumulator = it }
        recording = scope.launch {
            val previous = if (resume) runCatching { dao.pointsNow(tripId) }.getOrDefault(emptyList()) else emptyList()
            val rec = TripRecorder(tripId, previous).also { recorder = it }
            launch { rec.points.collect { _routePoints.value = it } }

            fun onSpeed(kmh: Float) {
                val at = now()
                acc.onSpeed(kmh, at)
                rec.onSpeed(at, kmh).forEach { acc.onEvent(it.type) }
                publish(acc)
            }
            launch { car.speedKmh().collect { s -> s.valueOrNull()?.let(::onSpeed) } }
            launch { navi.speedLimitKmh.collect(rec::onLimit) }
            launch { navi.location.collect { l -> l?.let { rec.onLocation(it.latitude, it.longitude) } } }
            launch { vehicle.brakePressed().collect { rec.onBrake(it.valueOrNull()) } }
            launch {
                var last: Gear? = null
                car.gear().collect { s ->
                    val g = s.valueOrNull() ?: return@collect
                    acc.onGear(g, now())
                    if (last != null && g != last) rec.onMarker(now())
                    last = g
                    publish(acc)
                }
            }
            launch {
                vehicle.doorOpen().collect { s ->
                    val open = s.valueOrNull() ?: return@collect
                    val before = acc.stats.driverDoorOpens
                    acc.onDriverDoor(open)
                    if (acc.stats.driverDoorOpens > before) rec.onDoorOpened(now())
                    publish(acc)
                }
            }
            launch {
                navi.events.collect { e ->
                    if (e is NaviEvent.Arrived) {
                        acc.onArrived(e.info.name.ifBlank { e.info.address })
                        publish(acc)
                        markArrived()
                    }
                }
            }
            // Position: navigation only answers when asked.
            launch {
                while (isActive) {
                    navi.requestLocation()
                    delay(LOCATION_EVERY_MS)
                }
            }
            // Tick: advance distance/moving time at constant speed, persist, save points in batches.
            launch {
                var sinceFlush = 0L
                while (isActive) {
                    delay(TICK_MS)
                    acc.onTick(now())
                    publish(acc)
                    runCatching { store.saveTrip(_trip.value) }.onFailure { Log.w(TAG, "save failed", it) }
                    sinceFlush += TICK_MS
                    if (sinceFlush >= FLUSH_EVERY_MS) {
                        flush(rec)
                        sinceFlush = 0
                    }
                }
            }
        }
    }

    /**
     * 시연 모드: play [DemoDrive] through the same recorder and accumulator as a real car,
     * [DemoDrive.PLAYBACK] times faster. Trip times follow the drive's own clock.
     */
    private fun playDemo(initial: TripStats) {
        stopRecording()
        onRecordingChanged(true)
        val start = initial.startedAtMs
        val acc = TripAccumulator(initial).also { accumulator = it }
        val rec = TripRecorder(start).also { recorder = it }
        recording = scope.launch {
            launch { rec.points.collect { _routePoints.value = it } }
            var lastFlush = 0L
            for ((i, f) in DemoDrive.frames.withIndex()) {
                val at = start + f.tMs
                demoNow = at
                rec.onLocation(f.lat, f.lon)
                rec.onLimit(f.limitKmh)
                rec.onRoad(f.road)
                acc.onGear(f.gear, at)
                acc.onSpeed(f.kmh, at)
                rec.onSpeed(at, f.kmh).forEach { acc.onEvent(it.type) }
                acc.onTick(at)
                // The screen only needs a couple of updates a second.
                if (i % UI_EVERY_FRAMES == 0 || f.gear == Gear.PARK) _demoLive.value = DemoLive(
                    speedKmh = f.kmh,
                    limitKmh = f.limitKmh,
                    gear = f.gear,
                    remaining = Remaining(distanceM = f.remainingM, durationSec = (f.remainingM / AVG_MPS).toInt()),
                )
                if (i % UI_EVERY_FRAMES == 0) publish(acc)
                if (f.tMs - lastFlush >= FLUSH_EVERY_MS) {
                    flush(rec)
                    runCatching { store.saveTrip(_trip.value) }
                    lastFlush = f.tMs
                }
                delay(DemoDrive.STEP_MS / DemoDrive.PLAYBACK)
            }
            publish(acc)
            markArrived()
        }
    }

    private suspend fun flush(rec: TripRecorder) {
        val (points, events) = rec.drain()
        runCatching {
            if (points.isNotEmpty()) dao.insertPoints(points)
            if (events.isNotEmpty()) dao.insertEvents(events)
        }.onFailure { Log.w(TAG, "saving route failed", it) }
    }

    private fun stopRecording() {
        if (recording == null) return
        recording?.cancel()
        recording = null
        accumulator = null
        recorder = null
        demoNow = null
        _demoLive.value = null
        onRecordingChanged(false)
    }

    private fun publish(acc: TripAccumulator) {
        _trip.update {
            if (it.phase == DaeriPhase.DRIVING || it.phase == DaeriPhase.ARRIVED) it.copy(stats = acc.stats) else it
        }
    }

    private fun saveSetup(next: OwnerSetup) {
        _setup.value = next
        scope.launch { runCatching { store.saveSetup(next) }.onFailure { Log.w(TAG, "save failed", it) } }
    }

    private fun commit(next: DaeriTrip) {
        _trip.value = next
        scope.launch { runCatching { store.saveTrip(next) }.onFailure { Log.w(TAG, "save failed", it) } }
    }

    private companion object {
        const val TAG = "Daeri"
        const val TICK_MS = 2_000L
        const val LOCATION_EVERY_MS = 5_000L
        const val FLUSH_EVERY_MS = 30_000L
        /** Rough city average for "time left" in the demo: 40 km/h. */
        const val AVG_MPS = 40 / 3.6
        const val UI_EVERY_FRAMES = 5
    }
}
