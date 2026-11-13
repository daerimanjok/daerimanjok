package app.daerimanjok.core.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.DrivingModeStateListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import android.content.Context
import android.util.Log
import app.daerimanjok.core.Signal
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Wraps the Pleos [Vehicle] SDK and exposes each property we use as a cold [Flow].
 *
 * SDK rules this class enforces (from the Pleos docs):
 *  - check capability before reading, where the SDK offers a check
 *  - read the current value once, then listen for updates
 *  - unregister with the same listener (and area) that was registered
 *  - all calls are async; results arrive via callbacks
 *
 * Collect a flow to subscribe; cancel the collector to unsubscribe.
 */
class VehicleSource(context: Context) {

    private val vehicle = Vehicle(context.applicationContext)
    @Volatile private var initialized = false

    fun initialize(): Result<Unit> = runCatching {
        if (!initialized) {
            vehicle.initialize()
            initialized = true
        }
    }.onFailure { Log.e(TAG, "Vehicle initialize failed", it) }

    fun release() {
        if (!initialized) return
        runCatching { vehicle.release() }.onFailure { Log.w(TAG, "Vehicle release failed", it) }
        initialized = false
    }

    // Gear: Pleos marks its Gear API deprecated ("use the Google GEAR_SELECTION property"),
    // so gear comes from CarSource.gear() instead.

    /** Total distance driven (odometer), in km. */
    fun odometerKm(): Flow<Signal<Float>> {
        val odometer = vehicle.getOdometer()
        return signal(
            name = "odometerKm",
            check = { ok, fail -> odometer.checkCurrentDistanceDrivenCapabilities({ ok() }, fail) },
            read = odometer::getCurrentDistanceDriven,
            listener = { onValue, onError ->
                object : DistanceDrivenListener {
                    override fun onDistanceDrivenUpdated(distance: Float?) = onValue(distance)
                    override fun onFailed(e: Exception) = onError(e)
                }
            },
            register = odometer::registerDistanceDriven,
            unregister = odometer::unregisterDistanceDriven,
        )
    }

    /** true while the brake pedal is pressed. */
    fun brakePressed(): Flow<Signal<Boolean>> {
        val brake = vehicle.getBrake()
        return signal(
            name = "brakePressed",
            check = { ok, fail -> brake.checkBrakePedalStatusCapabilities({ ok() }, fail) },
            read = brake::getCurrentBrakePedalStatus,
            listener = { onValue, onError ->
                object : BrakePedalStatusListener {
                    override fun onBrakePedalStatusUpdated(pressed: Boolean?) = onValue(pressed)
                    override fun onFailed(e: Exception) = onError(e)
                }
            },
            register = brake::registerBrakePedalStatus,
            unregister = brake::unregisterBrakePedalStatus,
        )
    }

    /** Fuel level, 0–100 %. Combustion and hybrid cars. */
    fun fuelPercent(): Flow<Signal<Int>> {
        val fuel = vehicle.getFuel()
        return signal(
            name = "fuelPercent",
            check = { ok, fail -> fuel.checkFuelPercentCapabilities({ ok() }, fail) },
            read = fuel::getFuelPercent,
            listener = { onValue, onError ->
                object : FuelPercentListener {
                    override fun onFuelPercentUpdated(percent: Int?) = onValue(percent)
                    override fun onFailed(e: Exception) = onError(e)
                }
            },
            register = fuel::registerFuelPercent,
            unregister = fuel::unregisterFuelPercent,
        )
    }

    /** EV battery state of charge, 0–100 %. Electric cars. */
    fun evBatteryPercent(): Flow<Signal<Int>> {
        val ev = vehicle.getEvBattery()
        return signal(
            name = "evBatteryPercent",
            check = { ok, fail -> ev.checkEvBatteryStateOfChargeCapabilities({ ok() }, fail) },
            read = ev::getEvBatteryStateOfCharge,
            listener = { onValue, onError ->
                object : EvBatteryStateOfChargeListener {
                    override fun onEvBatteryStateOfChargeUpdated(percent: Int?) = onValue(percent)
                    override fun onFailed(e: Exception) = onError(e)
                }
            },
            register = ev::registerEvBatteryStateOfCharge,
            unregister = ev::unregisterEvBatteryStateOfCharge,
        )
    }

    /** Selected drive mode (normal / eco / sport). */
    fun drivingMode(): Flow<Signal<DrivingModeType>> {
        val mode = vehicle.getDrivingMode()
        return signal(
            name = "drivingMode",
            check = { ok, fail -> mode.checkDrivingModeStateCapabilities({ ok() }, fail) },
            read = mode::getCurrentDrivingModeState,
            listener = { onValue, onError ->
                object : DrivingModeStateListener {
                    override fun onDrivingModeStateUpdated(type: DrivingModeType?) = onValue(type)
                    override fun onFailed(e: Exception) = onError(e)
                }
            },
            register = mode::registerDrivingModeState,
            unregister = mode::unregisterDrivingModeState,
        )
    }

    /** Vehicle identification number, read once. Used to tell the car model apart. */
    fun vin(onResult: (String?) -> Unit) {
        if (!initialized) return onResult(null)
        runCatching {
            vehicle.getCarInfo().getVINInfo({ onResult(it) }, { e -> Log.w(TAG, "VIN unavailable", e); onResult(null) })
        }.onFailure { Log.w(TAG, "VIN read failed", it); onResult(null) }
    }

    /** true while the given door is open (position > 0). */
    fun doorOpen(door: DoorValue = DoorValue.ROW_1_LEFT): Flow<Signal<Boolean>> {
        val doors = vehicle.getDoor()
        val area = DoorArea(door)
        return signal(
            name = "doorOpen",
            check = { ok, fail ->
                doors.checkDoorPositionCapabilities(
                    onComplete = { _, areaBounds ->
                        if (areaBounds.containsKey(area)) ok()
                        else fail(UnsupportedOperationException("$door not supported on this car"))
                    },
                    onFailed = fail,
                )
            },
            read = { onValue, onError -> doors.getCurrentDoorPosition(area, { onValue(it?.let { p -> p > 0 }) }, onError) },
            listener = { onValue, onError ->
                object : DoorPositionListener {
                    override fun onPositionUpdated(pos: Int?) = onValue(pos?.let { it > 0 })
                    override fun onFailed(e: Exception) = onError(e)
                }
            },
            register = { doors.registerDoorPosition(area, it) },
            unregister = { doors.unregisterDoorPosition(area, it) },
        )
    }

    /**
     * Generic capability-check → read → listen pipeline shared by every property.
     * [check] is optional because not every domain exposes a capability call (e.g. Gear).
     */
    @OptIn(DelicateCoroutinesApi::class) // isClosedForSend: only used to skip late registration
    private fun <T : Any, L> signal(
        name: String,
        check: ((ok: () -> Unit, fail: (Exception) -> Unit) -> Unit)? = null,
        read: (onValue: (T?) -> Unit, onError: (Exception) -> Unit) -> Unit,
        listener: (onValue: (T?) -> Unit, onError: (Exception) -> Unit) -> L,
        register: (L) -> Unit,
        unregister: (L) -> Unit,
    ): Flow<Signal<T>> = callbackFlow {
        trySend(Signal.Loading)
        val onValue: (T?) -> Unit = { v ->
            trySend(if (v == null) Signal.Unavailable("no value") else Signal.Value(v))
        }
        val onError: (Exception) -> Unit = { e ->
            Log.w(TAG, "$name unavailable: ${e.message ?: e.javaClass.simpleName}")
            trySend(Signal.Unavailable(e.message ?: e.javaClass.simpleName))
        }
        val l = listener(onValue, onError)
        var registered = false
        val start = {
            runCatching {
                // The capability callback may arrive after the collector has gone away.
                if (isClosedForSend) return@runCatching
                check(initialized) { "Vehicle not initialized" }
                read(onValue, onError)
                register(l)
                registered = true
            }.onFailure { onError(it as? Exception ?: RuntimeException(it)) }
        }
        if (check == null) start()
        else runCatching { check({ start() }, onError) }
            .onFailure { onError(it as? Exception ?: RuntimeException(it)) }

        awaitClose { if (registered) runCatching { unregister(l) } }
    }.distinctUntilChanged()

    private companion object {
        const val TAG = "VehicleSource"
    }
}
