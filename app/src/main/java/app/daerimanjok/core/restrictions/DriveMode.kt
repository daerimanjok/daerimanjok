package app.daerimanjok.core.restrictions

import app.daerimanjok.core.Signal
import app.daerimanjok.core.car.Gear
import app.daerimanjok.core.valueOrNull

enum class DriveMode { PARKED, DRIVING, UNKNOWN }

/**
 * Decide whether the UI must be in "driving" (distraction-optimised) mode.
 *
 * Source of truth is the platform's CarUxRestrictions. If that service is unavailable
 * we fall back to the gear: anything but P is treated as driving (fail safe).
 */
fun driveMode(requiresDistractionOptimization: Signal<Boolean>, gear: Signal<Gear>): DriveMode {
    requiresDistractionOptimization.valueOrNull()?.let { return if (it) DriveMode.DRIVING else DriveMode.PARKED }
    gear.valueOrNull()?.let { return if (it == Gear.PARK) DriveMode.PARKED else DriveMode.DRIVING }
    return DriveMode.UNKNOWN
}
