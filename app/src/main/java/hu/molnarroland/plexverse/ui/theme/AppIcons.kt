package hu.molnarroland.plexverse.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons for icon-only buttons that simplex-core's Icons doesn't have, drawn in the same 24-unit stroke style. */
object AppIcons {
    private const val STAR = "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8L3.5 9.7l5.9-.9z"

    /** Pin to the main menu (not pinned). */
    val Star = stroke(STAR)

    /** Pinned to the main menu. */
    val StarFilled: ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(addPathNodes(STAR), fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f, strokeLineJoin = StrokeJoin.Round)
        .build()

    val Trash = stroke("M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13 M10 11v6 M14 11v6")
    val Up = stroke("M12 19V5 M6 11l6-6 6 6")
    val Down = stroke("M12 5v14 M6 13l6 6 6-6")
    val Plus = stroke("M12 5v14 M5 12h14", width = 2f)
    val Lock = stroke("M8 11V8a4 4 0 0 1 8 0v3 M6 11h12v9H6z", width = 2.2f)

    private fun stroke(d: String, width: Float = 1.6f) = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(d),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}
