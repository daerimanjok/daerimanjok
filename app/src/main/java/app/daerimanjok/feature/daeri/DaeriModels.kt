package app.daerimanjok.feature.daeri

/** Kind of saved place, so the UI can say "집" instead of the bookmark's raw name. */
enum class PlaceKind { HOME, OFFICE, OTHER }

/** A destination picked from the navigation app's bookmarks. */
data class Place(
    val kind: PlaceKind,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val poiId: String,
    val poiSubId: String,
)

/**
 * What the owner sets up once (대리 준비) and reuses for every trip.
 * The PIN itself is never stored, only a salted hash.
 */
data class OwnerSetup(
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val destination: Place? = null,
    val parkingSpot: String = "",
    val note: String = "",
    val contact: String = "",
) {
    val hasPin: Boolean get() = pinHash != null && pinSalt != null
    val complete: Boolean get() = hasPin && destination != null
}

/**
 * One trip with a designated driver:
 *
 *   IDLE ─대리 모드 시작▶ BRIEFING ─운행 시작▶ DRIVING ─arrival▶ ARRIVED ─운행 종료▶ ENDED ─기록 삭제▶ IDLE
 *
 * ENDED keeps the report locked behind the owner's PIN until it is deleted.
 */
enum class DaeriPhase { IDLE, BRIEFING, DRIVING, ARRIVED, ENDED }

/** Everything about the current trip that must survive the app being closed mid-trip. */
data class DaeriTrip(
    val phase: DaeriPhase = DaeriPhase.IDLE,
    val stats: TripStats? = null,
    /** Where the trip started, as the navigation app names it. */
    val startAddress: String? = null,
    /** Destination of this trip (a copy, so later setup changes don't rewrite the report). */
    val destination: Place? = null,
    val parkingSpot: String = "",
    val arrivedAtMs: Long? = null,
    /** Played back by 시연 모드, not recorded from the car. */
    val demo: Boolean = false,
) {
    /** Key of this trip's route points and events in the database. */
    val tripId: Long? get() = stats?.startedAtMs
}

