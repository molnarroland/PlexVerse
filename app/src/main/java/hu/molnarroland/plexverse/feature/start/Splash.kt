@file:OptIn(ExperimentalTvMaterial3Api::class)

package hu.molnarroland.plexverse.feature.start

import hu.molnarroland.plexverse.ui.preview.PreviewTheme
import hu.molnarroland.plexverse.ui.preview.TvPreviews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.embergabor.simplex.core.ui.Palette
import hu.molnarroland.plexverse.ui.theme.Logo
import hu.molnarroland.plexverse.ui.theme.Look
import hu.molnarroland.plexverse.ui.theme.Wordmark

/**
 * The app's own splash, taking over from the system one with the mark in the same place: the planet
 * pulses and its satellites orbit while servers answer, with a dot filling for each ("2 of 3 servers").
 */
@Composable
fun Splash(answered: Int, total: Int) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Logo(Modifier.size(220.dp))
        Wordmark(40.sp, Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 18.dp).size(260.dp, 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            if (total > 0) {
                repeat(minOf(total, 8)) { i -> Box(Modifier.size(8.dp).background(if (i < answered) Look.AccentSoft else Palette.Control, CircleShape)) }
                Text("$answered of $total servers", fontSize = 12.sp, color = Palette.Secondary, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@TvPreviews
@Composable
internal fun SplashPreview() = PreviewTheme { Splash(answered = 2, total = 3) }

