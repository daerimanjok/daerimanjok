package app.daerimanjok.feature.daeri.record

/** Kinds of moment the report marks on the timeline. */
enum class EventType { OVERSPEED, HARD_ACCEL, HARD_BRAKE, DOOR_OPEN }

/** One detected moment. For 과속, [endMs] and [maxKmh] cover the whole episode. */
data class TripEvent(
    val type: EventType,
    val atMs: Long,
    val speedKmh: Float,
    val limitKmh: Int? = null,
    val endMs: Long? = null,
    val maxKmh: Float? = null,
)

/** Thresholds from the tech list. Tune on the emulator and in a real car. */
object TripConfig {
    /** 과속: more than this over the road's limit... */
    const val OVERSPEED_MARGIN_KMH = 10
    /** ...or above this when the limit is unknown... */
    const val OVERSPEED_NO_LIMIT_KMH = 110
    /** ...for at least this long. */
    const val OVERSPEED_MIN_MS = 3_000L

    /** 급가속 / 급제동: speed change of at least this within about one second (≈ 0.3 g). */
    const val HARSH_DELTA_KMH = 11f
    /** Compare against a sample between these ages, so bursts of events can't fake a spike. */
    const val HARSH_WINDOW_MIN_MS = 700L
    const val HARSH_WINDOW_MAX_MS = 1_500L
    /** Ignore braking from below this speed (parking manoeuvres). */
    const val HARSH_MIN_SPEED_KMH = 15f
    /** One event per manoeuvre. */
    const val HARSH_COOLDOWN_MS = 3_000L
}

/**
 * Turns speed samples into 과속, 급가속 and 급제동 events. Pure logic, unit-tested.
 * Feed samples in time order; collect what each call returns, then call [finish].
 */
class EventDetector {

    private val recent = ArrayDeque<Pair<Long, Float>>()
    private var lastAccelAt: Long? = null
    private var lastBrakeAt: Long? = null
    /** Inside one long hard acceleration / braking: counted once, until it eases off. */
    private var accelOngoing = false
    private var brakeOngoing = false

    private var overSince: Long? = null
    private var overLimit: Int? = null
    private var overMax = 0f
    private var overConfirmed = false
    private var lastAt = 0L

    /**
     * @param limitKmh road limit from navigation, null when unknown
     * @param brakePressed brake pedal state, null when the car doesn't report it
     */
    fun onSample(atMs: Long, kmh: Float, limitKmh: Int?, brakePressed: Boolean?): List<TripEvent> {
        val out = mutableListOf<TripEvent>()
        lastAt = atMs
        harsh(atMs, kmh, brakePressed)?.let(out::add)
        overspeed(atMs, kmh, limitKmh)?.let(out::add)
        return out
    }

    /** End of trip: closes a 과속 episode that is still running. */
    fun finish(): List<TripEvent> = listOfNotNull(closeOverspeed(lastAt))

    private fun harsh(atMs: Long, kmh: Float, brake: Boolean?): TripEvent? {
        recent.addLast(atMs to kmh)
        while (recent.isNotEmpty() && atMs - recent.first().first > TripConfig.HARSH_WINDOW_MAX_MS) recent.removeFirst()
        val ref = recent.firstOrNull { atMs - it.first >= TripConfig.HARSH_WINDOW_MIN_MS } ?: return null
        val delta = kmh - ref.second
        val hardAccel = delta >= TripConfig.HARSH_DELTA_KMH
        val hardBrake = -delta >= TripConfig.HARSH_DELTA_KMH && ref.second >= TripConfig.HARSH_MIN_SPEED_KMH && brake != false
        // An episode ends when the change drops below half the threshold.
        if (delta < TripConfig.HARSH_DELTA_KMH / 2) accelOngoing = false
        if (-delta < TripConfig.HARSH_DELTA_KMH / 2) brakeOngoing = false
        return when {
            hardAccel && !accelOngoing && cooled(lastAccelAt, atMs) -> {
                lastAccelAt = atMs
                accelOngoing = true
                TripEvent(EventType.HARD_ACCEL, atMs, kmh)
            }
            hardBrake && !brakeOngoing && cooled(lastBrakeAt, atMs) -> {
                lastBrakeAt = atMs
                brakeOngoing = true
                TripEvent(EventType.HARD_BRAKE, atMs, ref.second)
            }
            else -> null
        }
    }

    private fun cooled(last: Long?, now: Long) = last == null || now - last >= TripConfig.HARSH_COOLDOWN_MS

    private fun overspeed(atMs: Long, kmh: Float, limit: Int?): TripEvent? {
        val threshold = limit?.let { it + TripConfig.OVERSPEED_MARGIN_KMH } ?: TripConfig.OVERSPEED_NO_LIMIT_KMH
        if (kmh > threshold) {
            if (overSince == null) {
                overSince = atMs
                overMax = kmh
                overLimit = limit
            }
            overMax = maxOf(overMax, kmh)
            if (atMs - overSince!! >= TripConfig.OVERSPEED_MIN_MS) overConfirmed = true
            return null
        }
        return closeOverspeed(atMs)
    }

    private fun closeOverspeed(atMs: Long): TripEvent? {
        val since = overSince ?: return null
        val confirmed = overConfirmed
        val event = TripEvent(EventType.OVERSPEED, since, overMax, overLimit, endMs = atMs, maxKmh = overMax)
        overSince = null
        overConfirmed = false
        return event.takeIf { confirmed }
    }
}
