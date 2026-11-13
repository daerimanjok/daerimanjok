package app.daerimanjok.feature.daeri.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.daerimanjok.feature.daeri.demo.DemoDrive
import app.daerimanjok.ui.theme.Dm
import app.daerimanjok.ui.theme.DmColors
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

/** 시연 모드's planned route and river as sketch coordinates (x = longitude, y = latitude). */
val demoRoute: List<Offset> = DemoDrive.route.map { (lat, lon) -> Offset(lon.toFloat(), lat.toFloat()) }
private val demoRiver: List<Offset> = DemoDrive.river.map { (lat, lon) -> Offset(lon.toFloat(), lat.toFloat()) }

/**
 * Our own route drawing: recorded points in blue, start ring, event markers, the car.
 *
 * With [planned] the frame is fixed to the whole route (it doesn't rescale as the car moves) and
 * the rest of the route shows ahead. [mockMap] adds a made-up city underneath (blocks, streets,
 * the Han river): only for 시연 모드, whose route is made up too. Turn-by-turn stays in navigation.
 * Points are (x = longitude, y = latitude).
 */
@Composable
fun RouteSketch(
    points: List<Offset>,
    modifier: Modifier = Modifier,
    markers: List<Pair<Offset, Color>> = emptyList(),
    planned: List<Offset> = emptyList(),
    mockMap: Boolean = false,
    padding: Dp = 0.dp,
) {
    val c = Dm.colors
    val frame = if (planned.size >= 2) planned + points else points
    // Under ~30 m of spread there is no route to draw yet (or the GPS isn't moving).
    val spread = if (frame.size < 2) 0.0 else {
        val lat = frame.first().y.toDouble()
        val dx = (frame.maxOf { it.x } - frame.minOf { it.x }) * 111_320.0 * cos(Math.toRadians(lat))
        val dy = (frame.maxOf { it.y } - frame.minOf { it.y }) * 110_574.0
        maxOf(dx, dy)
    }
    Canvas(modifier) {
        val pad = padding.toPx()
        if (spread < 30.0) {
            val a = Offset(pad + (size.width - 2 * pad) * 0.15f, pad + (size.height - 2 * pad) * 0.85f)
            val b = Offset(pad + (size.width - 2 * pad) * 0.85f, pad + (size.height - 2 * pad) * 0.15f)
            drawLine(c.t3, a, b, strokeWidth = 8f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 24f)))
            drawCircle(c.surface, 18f, a); drawCircle(c.t1, 18f, a, style = Stroke(6f))
            drawCircle(c.accent, 20f, b)
            return@Canvas
        }
        // Equirectangular projection, longitudes shrunk by cos(latitude) so shapes keep their proportions.
        val kx = cos(Math.toRadians(frame.first().y.toDouble())).toFloat()
        val minX = frame.minOf { it.x } * kx; val maxX = frame.maxOf { it.x } * kx
        val minY = frame.minOf { it.y }; val maxY = frame.maxOf { it.y }
        val spanX = (maxX - minX).coerceAtLeast(1e-6f); val spanY = (maxY - minY).coerceAtLeast(1e-6f)
        val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val ox = (size.width - spanX * scale) / 2; val oy = (size.height - spanY * scale) / 2
        // Latitude grows northwards, screen y grows downwards.
        fun map(p: Offset) = Offset(ox + (p.x * kx - minX) * scale, size.height - (oy + (p.y - minY) * scale))
        val pxPerMetre = scale / 110_574f

        if (mockMap) drawMockCity(c, ::map, kx, scale, ox, oy, minX, minY, pxPerMetre)
        if (planned.size >= 2) {
            if (mockMap) drawLine(planned.map(::map), c.mapRoad, 26f)
            drawLine(planned.map(::map), c.info.copy(alpha = 0.35f), 12f)
        }
        if (points.size >= 2) drawLine(points.map(::map), c.info, 12f)

        val start = map((planned.firstOrNull() ?: points.first()))
        drawCircle(c.surface, 18f, start); drawCircle(c.t1, 18f, start, style = Stroke(6f))
        planned.lastOrNull()?.let { dest -> val d = map(dest); drawCircle(c.surface, 22f, d); drawCircle(c.accent, 16f, d) }
        markers.forEach { (p, col) -> val m = map(p); drawCircle(c.surface, 16f, m); drawCircle(col, 11f, m) }
        points.lastOrNull()?.let { val car = map(it); drawCircle(c.surface, 20f, car); drawCircle(c.car, 15f, car) }
    }
}

private fun DrawScope.drawLine(pts: List<Offset>, color: Color, width: Float) {
    if (pts.size < 2) return
    val path = Path().apply { pts.forEachIndexed { i, m -> if (i == 0) moveTo(m.x, m.y) else lineTo(m.x, m.y) } }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * A made-up city for 시연 모드: blocks, a street grid with a few wider avenues, and the Han river.
 * The grid is laid on map coordinates so it pans and scales with the route.
 */
private fun DrawScope.drawMockCity(
    c: DmColors,
    map: (Offset) -> Offset,
    kx: Float,
    scale: Float,
    ox: Float,
    oy: Float,
    minX: Float,
    minY: Float,
    pxPerMetre: Float,
) {
    drawRect(c.mapBlock)
    // Visible area in map coordinates (inverse of the projection).
    val lonAt = { x: Float -> ((x - ox) / scale + minX) / kx }
    val latAt = { y: Float -> (size.height - y - oy) / scale + minY }
    val lon0 = lonAt(0f); val lon1 = lonAt(size.width)
    val lat0 = latAt(size.height); val lat1 = latAt(0f)
    val stepLat = 0.0032f; val stepLon = 0.0040f
    var i = floor(lat0 / stepLat).toInt()
    while (i <= ceil(lat1 / stepLat).toInt()) {
        val y = map(Offset(lon0, i * stepLat)).y
        drawLine(c.mapRoad, Offset(0f, y), Offset(size.width, y), strokeWidth = if (i % 4 == 0) 12f else 5f)
        i++
    }
    var j = floor(lon0 / stepLon).toInt()
    while (j <= ceil(lon1 / stepLon).toInt()) {
        val x = map(Offset(j * stepLon, lat0)).x
        drawLine(c.mapRoad, Offset(x, 0f), Offset(x, size.height), strokeWidth = if (j % 4 == 0) 12f else 5f)
        j++
    }
    // The river, about 800 m wide.
    drawLine(demoRiver.map(map), c.mapWater, (800f * pxPerMetre).coerceAtLeast(24f))
}
