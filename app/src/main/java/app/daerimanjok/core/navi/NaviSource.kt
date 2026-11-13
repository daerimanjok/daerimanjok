package app.daerimanjok.core.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import app.daerimanjok.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The nav app always reports HOME and OFFICE slots, even when empty: an empty slot
 * has lat/lon 0,0 and a blank name. Routing to one fails with CAUSE_SEARCH_ROUTE.
 */
val BookmarkInfo.isSet: Boolean get() = !(latitude == 0.0 && longitude == 0.0)

/** Things that happen in the navigation app that a feature may react to. */
sealed interface NaviEvent {
    data class RouteStarted(val info: RouteStartInfo) : NaviEvent
    data object RouteCancelled : NaviEvent
    data object RouteEnded : NaviEvent
    data class Arrived(val info: DestinationArrivedInfo) : NaviEvent
    data class Error(val code: NaviErrorCode) : NaviEvent
}

/**
 * Wraps the Pleos [NaviHelper] SDK.
 *
 * NaviHelper's "get…" calls return nothing directly: results arrive later through
 * [NaviHelperEventListener]. This class turns those callbacks into StateFlows so the
 * UI can just observe [bookmarks], [location] and [routeState].
 *
 * Requires a navigation app set as default on the car (the Pleos emulator has one).
 */
class NaviSource(context: Context) : NaviHelperEventListener {

    private val helper = NaviHelper(context.applicationContext)
    @Volatile private var initialized = false

    private val _bookmarks = MutableStateFlow<List<BookmarkInfo>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkInfo>> = _bookmarks.asStateFlow()

    private val _location = MutableStateFlow<CurrentLocationInfo?>(null)
    val location: StateFlow<CurrentLocationInfo?> = _location.asStateFlow()

    private val _routeState = MutableStateFlow<RouteStateInfo?>(null)
    val routeState: StateFlow<RouteStateInfo?> = _routeState.asStateFlow()

    private val _speedLimitKmh = MutableStateFlow<Int?>(null)
    /** Speed limit of the road the car is on; null when unknown. */
    val speedLimitKmh: StateFlow<Int?> = _speedLimitKmh.asStateFlow()

    private val _remaining = MutableStateFlow<Remaining?>(null)
    /** Distance and time left to the destination while guiding; null when not guiding. */
    val remaining: StateFlow<Remaining?> = _remaining.asStateFlow()

    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NaviEvent> = _events.asSharedFlow()

    fun initialize(): Result<Unit> = runCatching {
        if (!initialized) {
            helper.initialize()
            helper.addListener(this)
            initialized = true
        }
    }.onFailure { Log.e(TAG, "NaviHelper initialize failed", it) }

    fun release() {
        if (!initialized) return
        runCatching {
            helper.removeListener(this)
            helper.release()
        }.onFailure { Log.w(TAG, "NaviHelper release failed", it) }
        initialized = false
    }

    /** Ask the nav app for fresh bookmarks / location / route state. Results arrive via the flows. */
    fun refresh() = call {
        helper.getBookmarkInfo()
        helper.getCurrentLocationInfo()
        helper.getRouteStateInfo()
    }

    /** Start guidance to a coordinate. Use the bookmark's POI ids when known, else "0". */
    fun routeTo(latitude: Double, longitude: Double, name: String, address: String, poiId: String = "0", poiSubId: String = "0") = call {
        // poiSubId "0" = main POI, as in the official sample
        helper.requestRoute(
            RouteInfo(latitude = latitude, longitude = longitude, poiId = poiId, poiName = name, poiSubId = poiSubId, address = address)
        )
    }

    fun cancelRoute() = call { helper.cancelRoute() }

    /** Ask for the current position; it arrives in [location]. */
    fun requestLocation() = call { helper.getCurrentLocationInfo() }

    /** Opens the navigation app's "add Home" screen, for an owner who hasn't saved one. */
    fun openAddHome() = call { helper.requestNavigationScreen(NavigationScreenRequest.AddHomeFavorite) }

    private inline fun call(block: () -> Unit): Boolean {
        if (!initialized) return false
        return runCatching(block).onFailure { Log.w(TAG, "NaviHelper call failed", it) }.isSuccess
    }

    // --- NaviHelperEventListener (all methods have default no-op implementations) ---

    override fun onBookmarkInfo(bookmarkInfo: List<BookmarkInfo>) {
        trace("onBookmarkInfo", bookmarkInfo)
        _bookmarks.value = bookmarkInfo
    }

    override fun onBookMarkChanged() {
        trace("onBookMarkChanged", null)
        call { helper.getBookmarkInfo() }
    }

    override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
        trace("onCurrentLocationInfo", currentLocationInfo)
        _location.value = currentLocationInfo
    }

    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
        trace("onRouteStateInfo", routeStateInfo)
        _routeState.value = routeStateInfo
    }

    override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
        trace("onRouteStarted", routeStartInfo)
        _events.tryEmit(NaviEvent.RouteStarted(routeStartInfo))
        call { helper.getRouteStateInfo() }
    }

    override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
        trace("onCurrentRoadSpeedLimit", speedLimit)
        _speedLimitKmh.value = speedLimit.takeIf { it > 0 }
    }

    override fun onDrivingInfo(drivingInfo: DrivingInfo) {
        trace("onDrivingInfo", drivingInfo)
        val d = drivingInfo.destination
        _remaining.value = Remaining(distanceM = d.distance, durationSec = d.duration)
    }

    override fun onRouteCancelled() {
        trace("onRouteCancelled", null)
        _remaining.value = null
        _events.tryEmit(NaviEvent.RouteCancelled)
        call { helper.getRouteStateInfo() }
    }

    // onRouteEnded() is deprecated in NaviHelper 3.x in favour of this.
    override fun onRoutingCompleted() {
        trace("onRoutingCompleted", null)
        _remaining.value = null
        _events.tryEmit(NaviEvent.RouteEnded)
        call { helper.getRouteStateInfo() }
    }

    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
        trace("onDestinationArrived", destinationArrivedInfo)
        _events.tryEmit(NaviEvent.Arrived(destinationArrivedInfo))
    }

    // Not used by the app yet; traced in debug builds to see what the car's navigation sends.
    override fun onTBTInfo(tbtInfo: List<TBTInfo>) = trace("onTBTInfo", tbtInfo)
    override fun onTripInfo(tripInfo: TripInfo) = trace("onTripInfo", tripInfo)
    override fun onNavigationStatus(navigationStatus: NavigationStatus) = trace("onNavigationStatus", navigationStatus)
    override fun onDestinationInfo(destinationInfo: DestinationInfo) = trace("onDestinationInfo", destinationInfo)

    override fun onError(naviErrorCode: NaviErrorCode) {
        trace("onError", naviErrorCode)
        _events.tryEmit(NaviEvent.Error(naviErrorCode))
    }

    /** Debug builds only: every callback with its fields, to learn the SDK's real data. */
    private fun trace(callback: String, value: Any?) {
        if (BuildConfig.DEBUG) Log.d(TAG, "$callback ${value ?: ""}")
    }

    private companion object {
        const val TAG = "NaviSource"
    }
}

/** What is left of the current route, from the navigation app. */
data class Remaining(val distanceM: Int, val durationSec: Int)
