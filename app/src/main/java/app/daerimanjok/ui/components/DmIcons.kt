package app.daerimanjok.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Outline icons from the UI prototype (24-unit grid, round caps and joins).
 * Tint them with `Icon(…, tint = …)`; the stroke colour here is only a placeholder.
 */
object DmIcons {
    val Check = icon(2.6f, "M5 12.5l4.5 4.5L19 7.5")
    val Info = icon(2f, circle(12f, 12f, 9f), "M12 11v5.5", "M12 7.6h.01")
    val Back = icon(2.2f, "M15 5l-7 7 7 7")
    val ArrowRight = icon(2.4f, "M5 12h14", "M13 6l6 6-6 6")
    val Home = icon(2f, "M4 11l8-6.5 8 6.5", "M6 9.5V19.5h12V9.5", "M10 19.5v-5h4v5")
    val Parking = icon(2f, rect(4.5f, 3.5f, 15f, 17f, 3.5f), "M10 16.5v-9h3a2.75 2.75 0 0 1 0 5.5h-3")
    val Guide = icon(1.9f, rect(4f, 5f, 16f, 14f, 3f), "M8 10h8M8 14h5")
    val Pin = icon(1.9f, "M12 21s-6.5-6-6.5-11a6.5 6.5 0 0 1 13 0c0 5-6.5 11-6.5 11z", circle(12f, 10f, 2.3f))
    val Lock = icon(1.9f, rect(5f, 10.5f, 14f, 10f, 2.5f), "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5")
    val Image = icon(1.8f, rect(3.5f, 5f, 17f, 14f, 3f), circle(9f, 10f, 1.6f), "M20.5 15.5l-4.5-4.5-8 8")
    val TurnRight = icon(2.4f, "M8 20v-8.5a2.5 2.5 0 0 1 2.5-2.5H18", "M14.5 5.5L18 9l-3.5 3.5")

    /** Not in the prototype: edit (pencil) and settings, drawn in the same style. */
    val Edit = icon(2f, "M4 20h4L18.5 9.5a2.1 2.1 0 0 0-4-4L4 16v4z", "M13.5 6.5l4 4")
    val Settings = icon(2f, "M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f))
}

private fun icon(stroke: Float, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    paths.forEach { d ->
        b.addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

/** SVG `<circle>` as path data. */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

/** SVG `<rect rx>` as path data. */
private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
        "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"
