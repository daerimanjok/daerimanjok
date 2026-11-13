package app.daerimanjok.feature.daeri.record

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns the live signals of one trip into route points and events. Pure logic (no Android),
 * unit-tested; the controller feeds it and writes [drain]ed rows to the database in batches.
 *
 * A route point is saved when 10 s have passed, the car moved 50 m, or something happened.
 */
class TripRecorder(private val tripId: Long, initialPoints: List<RoutePoint> = emptyList()) {

    private val detector = EventDetector()
    private val _points = MutableStateFlow(initialPoints)
    /** Every point of this trip so far, for the live route sketch. */
    val points: StateFlow<List<RoutePoint>> = _points.asStateFlow()

    private val pendingPoints = mutableListOf<RoutePoint>()
    private val pendingEvents = mutableListOf<TripEvent>()

    private var limitKmh: Int? = null
    private var road: String? = null
    private var lat: Double? = null
    private var lon: Double? = null
    private var brake: Boolean? = null
    private var speedKmh = 0f
    private var lastPoint: RoutePoint? = initialPoints.lastOrNull()

    fun onLimit(kmh: Int?) { limitKmh = kmh }
    fun onRoad(name: String?) { road = name }
    fun onBrake(pressed: Boolean?) { brake = pressed }

    fun onLocation(latitude: Double, longitude: Double) {
        lat = latitude
        lon = longitude
    }

    /** @return events found by this sample (already queued for saving). */
    fun onSpeed(atMs: Long, kmh: Float): List<TripEvent> {
        speedKmh = kmh
        val events = detector.onSample(atMs, kmh, limitKmh, brake)
        pendingEvents += events
        if (events.isNotEmpty() || pointDue(atMs)) addPoint(atMs)
        return events
    }

    /** Gear change, door, arrival: always worth a point. */
    fun onMarker(atMs: Long) = addPoint(atMs)

    fun onDoorOpened(atMs: Long): TripEvent =
        TripEvent(EventType.DOOR_OPEN, atMs, speedKmh).also { pendingEvents += it; addPoint(atMs) }

    /** End of trip: closes a running 과속 episode and adds a last point. */
    fun finish(atMs: Long): List<TripEvent> {
        val events = detector.finish()
        pendingEvents += events
        addPoint(atMs)
        return events
    }

    /** Rows not yet saved; clears them. */
    fun drain(): Pair<List<RoutePoint>, List<EventRow>> {
        val p = pendingPoints.toList()
        val e = pendingEvents.map { EventRow.of(tripId, it) }
        pendingPoints.clear()
        pendingEvents.clear()
        return p to e
    }

    private fun pointDue(atMs: Long): Boolean {
        val last = lastPoint ?: return true
        if (atMs - last.atMs >= POINT_EVERY_MS) return true
        val (la, lo) = (lat ?: return false) to (lon ?: return false)
        val (pla, plo) = (last.lat ?: return true) to (last.lon ?: return true)
        return metersBetween(pla, plo, la, lo) >= POINT_EVERY_M
    }

    private fun addPoint(atMs: Long) {
        val p = RoutePoint(tripId = tripId, atMs = atMs, lat = lat, lon = lon, speedKmh = speedKmh, limitKmh = limitKmh, road = road)
        lastPoint = p
        pendingPoints += p
        _points.value = _points.value + p
    }

    companion object {
        const val POINT_EVERY_MS = 10_000L
        const val POINT_EVERY_M = 50.0

        /** Haversine distance in metres. */
        fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
