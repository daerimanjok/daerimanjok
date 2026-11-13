package app.daerimanjok.feature.daeri

import app.daerimanjok.core.car.Gear
import app.daerimanjok.feature.daeri.record.EventType

/** Everything recorded while the car is handed over. All times are wall-clock epoch ms. */
data class TripStats(
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val startOdometerKm: Float? = null,
    val lastOdometerKm: Float? = null,
    /** Distance integrated from speed: fallback when the odometer does not move. */
    val estimatedDistanceKm: Double = 0.0,
    val topSpeedKmh: Float = 0f,
    val movingMs: Long = 0,
    val hardBrakes: Int = 0,
    val hardAccels: Int = 0,
    val overspeeds: Int = 0,
    val driverDoorOpens: Int = 0,
    val arrivedAt: String? = null,
    val lastParkedAtMs: Long? = null,
) {
    private val odometerDeltaKm: Double?
        get() {
            val start = startOdometerKm ?: return null
            val last = lastOdometerKm ?: return null
            return (last - start).toDouble().takeIf { it >= MIN_ODOMETER_DELTA_KM }
        }

    /** True when [distanceKm] comes from the odometer rather than the speed estimate. */
    val distanceFromOdometer: Boolean get() = odometerDeltaKm != null

    val distanceKm: Double get() = odometerDeltaKm ?: estimatedDistanceKm

    fun durationMs(nowMs: Long): Long = (endedAtMs ?: nowMs) - startedAtMs

    private companion object {
        const val MIN_ODOMETER_DELTA_KM = 0.1
    }
}

/**
 * Turns the raw stream of car signals into [TripStats]. Pure logic, no Android, so it
 * is unit-tested directly. Feed it events in the order they arrive.
 */
class TripAccumulator(initial: TripStats) {

    var stats: TripStats = initial
        private set

    private var speedKmh: Float = 0f
    private var lastIntegratedAt: Long? = null
    private var driverDoorOpen: Boolean? = null
    private var gear: Gear? = null

    fun onSpeed(kmh: Float, nowMs: Long) {
        integrate(nowMs)
        speedKmh = kmh
        if (kmh > stats.topSpeedKmh) stats = stats.copy(topSpeedKmh = kmh)
    }

    /** Call periodically so distance and moving time advance at constant speed too. */
    fun onTick(nowMs: Long) = integrate(nowMs)

    fun onOdometer(km: Float) {
        stats = stats.copy(
            startOdometerKm = stats.startOdometerKm ?: km,
            lastOdometerKm = km,
        )
    }

    fun onDriverDoor(open: Boolean) {
        if (open && driverDoorOpen == false) stats = stats.copy(driverDoorOpens = stats.driverDoorOpens + 1)
        driverDoorOpen = open
    }

    fun onGear(newGear: Gear, nowMs: Long) {
        if (newGear == Gear.PARK && gear != null && gear != Gear.PARK) {
            stats = stats.copy(lastParkedAtMs = nowMs)
        }
        gear = newGear
    }

    /** Counts an event found by the EventDetector. */
    fun onEvent(type: EventType) {
        stats = when (type) {
            EventType.OVERSPEED -> stats.copy(overspeeds = stats.overspeeds + 1)
            EventType.HARD_ACCEL -> stats.copy(hardAccels = stats.hardAccels + 1)
            EventType.HARD_BRAKE -> stats.copy(hardBrakes = stats.hardBrakes + 1)
            EventType.DOOR_OPEN -> stats
        }
    }

    fun onArrived(destination: String) {
        stats = stats.copy(arrivedAt = destination)
    }

    fun finish(nowMs: Long): TripStats {
        integrate(nowMs)
        stats = stats.copy(endedAtMs = nowMs)
        return stats
    }

    private fun integrate(nowMs: Long) {
        val last = lastIntegratedAt
        lastIntegratedAt = nowMs
        if (last == null || nowMs <= last) return
        val dt = nowMs - last
        if (speedKmh > MOVING_KMH) {
            stats = stats.copy(
                estimatedDistanceKm = stats.estimatedDistanceKm + speedKmh * dt / 3_600_000.0,
                movingMs = stats.movingMs + dt,
            )
        }
    }

    companion object {
        const val MOVING_KMH = 1f
    }
}
