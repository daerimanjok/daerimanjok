package app.daerimanjok.feature.daeri.demo

import app.daerimanjok.core.car.Gear
import app.daerimanjok.feature.daeri.record.TripRecorder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 시연 모드 drive: a made-up evening trip in Seoul (마포 → 강변북로 → 한남대교 → 강남),
 * simulated second by second so speeds, limits and positions look like a real recording.
 *
 * The emulator's GPS never moves, so this is the only way to show the driving screen and a
 * full report there. Frames go through the same recorder as a real car.
 *
 * Built in: one red light, one 급가속 when joining 강변북로, no 과속 (top ≈ 78 in an 80 zone).
 */
object DemoDrive {

    /** One step of the drive. [tMs] is drive time since the start (not wall-clock). */
    data class Frame(
        val tMs: Long,
        val lat: Double,
        val lon: Double,
        val kmh: Float,
        val limitKmh: Int?,
        val road: String,
        val remainingM: Int,
        val gear: Gear,
    )

    /** Where the demo drive starts, shown as the trip's start in the report. */
    const val START_ADDRESS = "마포구 합정동"

    /** Drive time between frames. */
    const val STEP_MS = 800L
    /** Played this many times faster than real time: ~10 min of driving in ~30 s. */
    const val PLAYBACK = 20

    private class Leg(
        val road: String,
        val limitKmh: Int,
        val cruiseKmh: Float,
        val points: List<Pair<Double, Double>>,
        /** Stop at the end of this leg (red light) for this long. */
        val stopMs: Long = 0,
        /** A quick surge on joining this leg (급가속). */
        val surgeOnEntry: Boolean = false,
    )

    private val LEGS = listOf(
        Leg("일반도로", 50, 44f, listOf(37.5502 to 126.9320, 37.5466 to 126.9362), stopMs = 12_000),
        Leg("강변북로", 80, 73f, listOf(37.5446 to 126.9388, 37.5395 to 126.9480, 37.5340 to 126.9620, 37.5300 to 126.9780, 37.5262 to 126.9950, 37.5248 to 127.0090), surgeOnEntry = true),
        Leg("한남대교", 60, 55f, listOf(37.5230 to 127.0130, 37.5185 to 127.0152, 37.5140 to 127.0170)),
        Leg("일반도로", 50, 42f, listOf(37.5095 to 127.0200)),
        Leg("일반도로", 30, 22f, listOf(37.5082 to 127.0212, 37.5074 to 127.0222)),
    )

    /** The planned route as (latitude, longitude), for the demo's map. */
    val route: List<Pair<Double, Double>> get() = LEGS.flatMap { it.points }

    /**
     * Course of the Han river next to the route, for the demo's mock map: south of 강변북로,
     * crossed by 한남대교. Drawn, not navigated.
     */
    val river: List<Pair<Double, Double>> = listOf(
        37.5420 to 126.9180, 37.5378 to 126.9330, 37.5338 to 126.9460, 37.5285 to 126.9610,
        37.5245 to 126.9770, 37.5205 to 126.9940, 37.5188 to 127.0090, 37.5160 to 127.0230,
        37.5120 to 127.0380,
    )

    /** The whole drive, precomputed (a few thousand small frames). */
    val frames: List<Frame> by lazy { simulate() }

    private class Vertex(val lat: Double, val lon: Double, val s: Double, val leg: Int)

    private fun simulate(): List<Frame> {
        // Flatten the legs into one polyline with distance along it (s, metres).
        val verts = mutableListOf<Vertex>()
        LEGS.forEachIndexed { i, leg ->
            leg.points.forEach { (la, lo) ->
                val s = verts.lastOrNull()?.let { it.s + TripRecorder.metersBetween(it.lat, it.lon, la, lo) } ?: 0.0
                verts += Vertex(la, lo, s, i)
            }
        }
        val total = verts.last().s
        val legEnd = LEGS.indices.map { i -> verts.last { it.leg == i }.s }

        val out = mutableListOf<Frame>()
        val dt = STEP_MS / 1000.0
        var t = 0L
        var s = 0.0
        var v = 0.0 // m/s
        var waitUntil: Long? = null
        val stopped = mutableSetOf<Int>()
        val surged = mutableSetOf<Int>()

        fun frame(gear: Gear) {
            val (la, lo, legIdx) = positionAt(verts, s)
            val leg = LEGS[legIdx]
            out += Frame(t, la, lo, (v * 3.6).toFloat(), leg.limitKmh, leg.road, (total - s).toInt().coerceAtLeast(0), gear)
        }

        // A few seconds standing in P, then D.
        repeat(4) { frame(Gear.PARK); t += STEP_MS }
        while (s < total - 0.5) {
            val legIdx = positionAt(verts, s).third
            val leg = LEGS[legIdx]
            val wait = waitUntil
            if (wait != null) {
                v = 0.0
                if (t >= wait) waitUntil = null
            } else {
                // Gentle variation around the cruise speed, like real traffic.
                val cruise = (leg.cruiseKmh + 3.5 * sin(t / 23_000.0) + 1.5 * sin(t / 7_000.0)) / 3.6
                // Brake for the next stop: the leg's red light, or the destination.
                val stopAt = LEGS.indices.firstOrNull { i -> legEnd[i] > s && (LEGS[i].stopMs > 0 && i !in stopped) }
                    ?.let { legEnd[it] } ?: total
                val toStop = stopAt - s
                // Plan the stop at 70 % of full braking, so the car is crawling when it reaches the line.
                val brakeV = sqrt(max(0.0, 2 * DECEL * 0.7 * (toStop - 1)))
                // Slow down before a lower limit, like a careful driver.
                val nextSlower = LEGS.indices.filter { i -> legEnd[i] > s && i + 1 < LEGS.size }
                    .minOfOrNull { i -> sqrt(sq(LEGS[i + 1].limitKmh / 3.6 - 2.0) + 2 * DECEL * 0.7 * max(0.0, legEnd[i] - s - 20)) }
                    ?: Double.MAX_VALUE
                val target = min(min(cruise, brakeV), nextSlower)
                v = if (v < target) min(target, v + ACCEL * dt) else max(target, v - DECEL * dt)
                if (leg.surgeOnEntry && legIdx !in surged && v > 30 / 3.6) {
                    v += 13 / 3.6 // 급가속 joining the expressway
                    surged += legIdx
                }
                // Red light: come to rest just before the line (the braking curve leaves only a crawl).
                if (stopAt < total && s + v * dt >= stopAt - 1) {
                    val i = LEGS.indices.first { legEnd[it] == stopAt }
                    stopped += i
                    s = stopAt - 1
                    v = 0.0
                    waitUntil = t + LEGS[i].stopMs
                }
                if (v < 0.4 && toStop < 6 && stopAt >= total) {
                    s = total; v = 0.0
                }
                s = min(total, s + v * dt)
            }
            frame(Gear.DRIVE)
            t += STEP_MS
        }
        v = 0.0
        repeat(3) { frame(Gear.DRIVE); t += STEP_MS }
        repeat(3) { frame(Gear.PARK); t += STEP_MS }
        return out
    }

    private fun positionAt(verts: List<Vertex>, s: Double): Triple<Double, Double, Int> {
        val i = verts.indexOfLast { it.s <= s }.coerceIn(0, verts.size - 2)
        val a = verts[i]; val b = verts[i + 1]
        val f = if (b.s > a.s) ((s - a.s) / (b.s - a.s)).coerceIn(0.0, 1.0) else 0.0
        return Triple(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f, b.leg)
    }

    private fun sq(x: Double) = x * x

    /** m/s²: normal acceleration (≈ 6 km/h per second) and braking. */
    private const val ACCEL = 1.6
    private const val DECEL = 1.8
}
