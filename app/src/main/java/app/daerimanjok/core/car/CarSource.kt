package app.daerimanjok.core.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.daerimanjok.core.Signal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

/** Gear selector position, independent of any SDK's own type. */
enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/**
 * Standard AAOS Car API: things the Pleos Vehicle SDK does not cover (or has deprecated).
 *  - vehicle speed (PERF_VEHICLE_SPEED), runtime permission CAR_SPEED
 *  - gear (GEAR_SELECTION); Pleos' Gear API is deprecated in favour of this
 *  - driver-distraction state (CarUxRestrictions) → "driving" vs "parked" UI
 */
class CarSource(private val context: Context) {

    private var car: Car? = null

    private fun car(): Car? {
        car?.let { return it }
        return runCatching { Car.createCar(context.applicationContext) }
            .onFailure { Log.e(TAG, "Car.createCar failed", it) }
            .getOrNull()
            ?.also { car = it }
    }

    fun release() {
        runCatching { car?.disconnect() }
        car = null
    }

    fun hasSpeedPermission(): Boolean = granted(SPEED_PERMISSION)

    // Bumped after a runtime permission dialog so permission-gated flows re-subscribe.
    private val permissionTick = MutableStateFlow(0)

    fun onPermissionsChanged() {
        permissionTick.value++
    }

    /** Vehicle speed in km/h. Needs the runtime CAR_SPEED permission. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun speedKmh(): Flow<Signal<Float>> = permissionTick.flatMapLatest {
        property(VehiclePropertyIds.PERF_VEHICLE_SPEED, SPEED_PERMISSION) { v ->
            (v as? Float)?.let { it * 3.6f } // m/s → km/h
        }
    }

    fun gear(): Flow<Signal<Gear>> =
        property(VehiclePropertyIds.GEAR_SELECTION, Car.PERMISSION_POWERTRAIN) { v ->
            when (v as? Int) {
                android.car.VehicleGear.GEAR_PARK -> Gear.PARK
                android.car.VehicleGear.GEAR_REVERSE -> Gear.REVERSE
                android.car.VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
                null -> null
                // DRIVE plus numbered forward gears (manual transmissions)
                else -> Gear.DRIVE
            }
        }

    /** Parking brake applied (PARKING_BRAKE_ON). No Virtual Console control on the emulator. */
    fun parkingBrakeOn(): Flow<Signal<Boolean>> =
        property(VehiclePropertyIds.PARKING_BRAKE_ON, Car.PERMISSION_POWERTRAIN) { v -> v as? Boolean }

    /**
     * true when the platform requires distraction-optimised UI (i.e. the car is moving).
     * This is the source of truth for driving vs. parked; never infer it from speed alone.
     */
    fun requiresDistractionOptimization(): Flow<Signal<Boolean>> = callbackFlow {
        trySend(Signal.Loading)
        val mgr = car()?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        if (mgr == null) {
            trySend(Signal.Unavailable("UX restriction service unavailable"))
            awaitClose { }
            return@callbackFlow
        }
        runCatching { mgr.currentCarUxRestrictions?.isRequiresDistractionOptimization }
            .getOrNull()?.let { trySend(Signal.Value(it)) }
        mgr.registerListener { r -> trySend(Signal.Value(r.isRequiresDistractionOptimization)) }
        awaitClose { runCatching { mgr.unregisterListener() } }
    }.distinctUntilChanged()

    /**
     * Subscribe to one AAOS vehicle property. The current value is delivered as the
     * first event after registering, so no separate "get" is needed.
     */
    // registerCallback is deprecated from Android 15 (subscribePropertyEvents), but the
    // Pleos emulator is Android 14, where the replacement does not exist yet.
    @Suppress("DEPRECATION")
    private fun <T : Any> property(propertyId: Int, permission: String, map: (Any?) -> T?): Flow<Signal<T>> = callbackFlow {
        trySend(Signal.Loading)
        val pm = car()?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        val name = VehiclePropertyIds.toString(propertyId)
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                map(value.value)?.let { trySend(Signal.Value(it)) }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                trySend(Signal.Unavailable("$name error"))
            }
        }
        val registered = when {
            !granted(permission) -> {
                trySend(Signal.Unavailable("$permission not granted")); false
            }
            pm == null -> {
                trySend(Signal.Unavailable("Car property service unavailable")); false
            }
            else -> runCatching {
                pm.registerCallback(callback, propertyId, CarPropertyManager.SENSOR_RATE_ONCHANGE).also {
                    // Some properties (e.g. GEAR_SELECTION on the emulator) only send an event on
                    // the next change after a re-subscribe, so read the current value once too.
                    runCatching { pm.getProperty<Any>(propertyId, 0) }.getOrNull()
                        ?.let { v -> map(v.value)?.let { trySend(Signal.Value(it)) } }
                }
            }.onFailure {
                Log.w(TAG, "$name register failed", it)
                trySend(Signal.Unavailable(it.message ?: "$name unavailable"))
            }.getOrDefault(false)
        }
        awaitClose { if (registered) runCatching { pm?.unregisterCallback(callback, propertyId) } }
    }.distinctUntilChanged()

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CarSource"
        const val SPEED_PERMISSION = Car.PERMISSION_SPEED
    }
}
