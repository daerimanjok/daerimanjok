package app.daerimanjok.ui

import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.daerimanjok.DaeriApp
import app.daerimanjok.core.Signal
import app.daerimanjok.core.car.Gear
import app.daerimanjok.core.navi.Remaining
import app.daerimanjok.core.navi.isSet
import app.daerimanjok.core.restrictions.DriveMode
import app.daerimanjok.core.restrictions.driveMode
import app.daerimanjok.core.valueOrNull
import app.daerimanjok.feature.daeri.CarModel
import app.daerimanjok.feature.daeri.DaeriController
import app.daerimanjok.feature.daeri.Place
import app.daerimanjok.feature.daeri.PlaceKind
import app.daerimanjok.feature.daeri.RecordingService
import app.daerimanjok.feature.daeri.record.RoutePoint
import app.daerimanjok.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Snapshot of every car signal the screens show. */
data class CarStatus(
    val gear: Signal<Gear> = Signal.Loading,
    val speedKmh: Signal<Float> = Signal.Loading,
    val parkingBrake: Signal<Boolean> = Signal.Loading,
    val drivingMode: Signal<DrivingModeType> = Signal.Loading,
    /** Battery % on an EV, else fuel %. */
    val energyPercent: Int? = null,
    val isEv: Boolean = false,
    val requiresDistractionOptimization: Signal<Boolean> = Signal.Loading,
) {
    val driveMode: DriveMode get() = driveMode(requiresDistractionOptimization, gear)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as DaeriApp).container

    val daeri: DaeriController = c.daeri

    private val energy = combine(c.vehicle.evBatteryPercent(), c.vehicle.fuelPercent()) { ev, fuel ->
        ev.valueOrNull()?.let { it to true } ?: fuel.valueOrNull()?.let { it to false }
    }

    private val drive = combine(
        c.car.gear(),
        c.car.speedKmh(),
        c.car.parkingBrakeOn(),
        c.vehicle.drivingMode(),
        c.car.requiresDistractionOptimization(),
    ) { gear, speed, brake, mode, ux ->
        CarStatus(gear = gear, speedKmh = speed, parkingBrake = brake, drivingMode = mode, requiresDistractionOptimization = ux)
    }

    val status: StateFlow<CarStatus> = combine(drive, energy, c.daeri.demoLive) { s, e, demo ->
        val withEnergy = s.copy(energyPercent = e?.first, isEv = e?.second ?: false)
        // 시연 모드 drives the screens as if the car were moving.
        if (demo == null) withEnergy else withEnergy.copy(
            gear = Signal.Value(demo.gear),
            speedKmh = Signal.Value(demo.speedKmh),
            requiresDistractionOptimization = Signal.Value(demo.gear != Gear.PARK),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarStatus())

    val driveMode: StateFlow<DriveMode> = status.map { it.driveMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriveMode.UNKNOWN)

    /** Saved places from the navigation app (집, 회사, favourites), empty slots left out. */
    val places: StateFlow<List<Place>> = c.navi.bookmarks.map { list ->
        list.filter { it.isSet }.map { b ->
            Place(
                kind = when (b.category) {
                    BookmarkType.HOME -> PlaceKind.HOME
                    BookmarkType.OFFICE -> PlaceKind.OFFICE
                    else -> PlaceKind.OTHER
                },
                name = b.name,
                address = b.address,
                latitude = b.latitude,
                longitude = b.longitude,
                poiId = b.poiId,
                poiSubId = b.poiSubId ?: "0",
            )
        }.sortedBy { it.kind.ordinal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val speedLimitKmh: StateFlow<Int?> = combine(c.navi.speedLimitKmh, c.daeri.demoLive) { real, demo -> demo?.limitKmh ?: real }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val remaining: StateFlow<Remaining?> = combine(c.navi.remaining, c.daeri.demoLive) { real, demo -> demo?.remaining ?: real }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Live route of the current trip as (x = longitude, y = latitude), for the sketch. */
    val routeSketch: StateFlow<List<Offset>> = c.daeri.routePoints.map { it.toSketch() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val carModel: StateFlow<CarModel> = c.carModel

    val themeMode: StateFlow<ThemeMode> = c.settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val demoMode: StateFlow<Boolean> = c.settings.demoMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { c.settings.setThemeMode(mode) }
    }

    fun setDemoMode(on: Boolean) {
        viewModelScope.launch { c.settings.setDemoMode(on) }
    }

    init {
        c.navi.refresh()
    }

    fun needsSpeedPermission() = !c.car.hasSpeedPermission()

    fun onSpeedPermissionResult() = c.car.onPermissionsChanged()

    fun refreshNavi() = c.navi.refresh()

    fun resetAll() {
        c.daeri.resetAll()
        RecordingService.cancelArrival(getApplication())
    }

    /** No 집 saved yet: open the navigation app's "add Home" screen. */
    fun openAddHome() = c.navi.openAddHome()
}

/** Recorded points with a position, as sketch coordinates (x = longitude, y = latitude). */
fun List<RoutePoint>.toSketch(): List<Offset> =
    mapNotNull { p -> if (p.lat != null && p.lon != null) Offset(p.lon.toFloat(), p.lat.toFloat()) else null }
