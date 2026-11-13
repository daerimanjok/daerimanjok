package app.daerimanjok

import android.app.Application
import android.util.Log
import app.daerimanjok.core.car.CarSource
import app.daerimanjok.core.data.AppPrefs
import app.daerimanjok.core.data.AppSettings
import app.daerimanjok.core.navi.NaviSource
import app.daerimanjok.core.vehicle.VehicleSource
import app.daerimanjok.feature.daeri.CarGuide
import app.daerimanjok.feature.daeri.DaeriController
import app.daerimanjok.feature.daeri.DaeriStore
import app.daerimanjok.feature.daeri.RecordingService
import app.daerimanjok.feature.daeri.record.TripDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class DaeriApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * One instance of each car/SDK wrapper per process.
 *
 * The SDKs are initialised here, not in a screen, because trip recording must keep
 * running while the driver has the navigation app in front. They live as long as the
 * process; the OS releases the bindings when the process dies.
 */
class AppContainer(app: Application) {
    /** Lives as long as the process; used for work that must outlive any screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val vehicle = VehicleSource(app)
    val car = CarSource(app)
    val navi = NaviSource(app)
    val prefs = AppPrefs(app)
    val settings = AppSettings(prefs)

    val vehicleReady = vehicle.initialize().isSuccess
    val naviReady = navi.initialize().isSuccess

    val daeri = DaeriController(
        scope = appScope,
        store = DaeriStore(app),
        dao = TripDatabase.create(app).trips(),
        vehicle = vehicle,
        car = car,
        navi = navi,
        demoMode = settings.demoMode.stateIn(appScope, SharingStarted.Eagerly, false),
        onRecordingChanged = { on -> if (on) RecordingService.start(app) else RecordingService.stop(app) },
        // The app shows the parking spot itself when it's on screen; notify only when it's behind navigation.
        onArrived = { spot -> if (!MainActivity.visible) RecordingService.notifyArrival(app, spot) },
    )

    /** Car model for the driver's control guide, read once from the VIN. */
    val carModel = MutableStateFlow(CarGuide.DEFAULT_MODEL).also { model ->
        vehicle.vin { vin ->
            // Only the maker + model part, never the full VIN (it identifies the car).
            Log.i("DaeriApp", "VIN model code: ${vin?.take(5) ?: "unavailable"}")
            model.value = CarGuide.modelFromVin(vin)
        }
    }
}
