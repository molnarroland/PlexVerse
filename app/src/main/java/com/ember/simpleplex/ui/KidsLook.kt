package com.ember.simpleplex.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * PlexVerse's own additions to the shared look in simplex-core ([com.embergabor.simplex.core.ui.Palette]
 * and [com.embergabor.simplex.core.ui.Icons]): Kids' colour and the icons only PlexVerse uses.
 */
object KidsLook {
    /** The Kids button, the same in both themes, with dark text. */
    val Fill = Color(0xFFFFB340)

    val Smile: ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(addPathNodes("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M8.5 14a4 4 0 0 0 7 0 M9 9.5h.01 M15 9.5h.01"), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        .build()

    /** A die showing five: the frame, then the pips as round dots. */
    val Dice: ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(addPathNodes("M7.5 3.5h9a4 4 0 0 1 4 4v9a4 4 0 0 1-4 4h-9a4 4 0 0 1-4-4v-9a4 4 0 0 1 4-4z"), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round)
        .addPath(addPathNodes("M8.5 8.5h.01 M15.5 8.5h.01 M12 12h.01 M8.5 15.5h.01 M15.5 15.5h.01"), stroke = SolidColor(Color.Black), strokeLineWidth = 2.8f, strokeLineCap = StrokeCap.Round)
        .build()
}
