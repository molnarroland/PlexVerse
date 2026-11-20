package hu.molnarroland.plexverse.ui.theme

import android.content.Context
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.embergabor.simplex.core.plex.PlexClient
import com.embergabor.simplex.core.ui.Palette
import com.embergabor.simplex.core.ui.ThemeMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONObject

/** A place on Earth, for sunrise and sunset. */
data class Place(val name: String, val lat: Double, val lng: Double)

/** Theme mode, and where the TV is for Auto: from its IP address, or a place set by hand. Kept on the TV. */
class ThemePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    var mode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: ThemeMode.Auto
        set(value) { prefs.edit().putString("theme", value.name).apply() }

    /** The place set by hand; null means "from the IP address". */
    var manual: Place?
        get() = read("manual")
        set(value) = write("manual", value)

    /** The place the IP address was last found at, and when (ms). */
    var fromIp: Place?
        get() = read("ip")
        set(value) { write("ip", value); prefs.edit().putLong("ip.at", System.currentTimeMillis()).apply() }

    val fromIpAt get() = prefs.getLong("ip.at", 0)

    /** Where Auto follows the sun: the place set by hand, else the IP's. */
    val place get() = manual ?: fromIp

    private fun read(key: String): Place? = if (!prefs.contains("$key.lat")) null
    else Place(prefs.getString("$key.name", null) ?: "", prefs.getFloat("$key.lat", 0f).toDouble(), prefs.getFloat("$key.lng", 0f).toDouble())

    private fun write(key: String, place: Place?) {
        prefs.edit().apply {
            if (place == null) remove("$key.lat").remove("$key.lng").remove("$key.name")
            else putFloat("$key.lat", place.lat.toFloat()).putFloat("$key.lng", place.lng.toFloat()).putString("$key.name", place.name)
        }.apply()
    }
}

/** Night or day colours for [mode] now. */
fun colorsFor(prefs: ThemePrefs, mode: ThemeMode) =
    if (isDark(mode, prefs.place?.let { it.lat to it.lng }, Instant.now(), ZoneId.systemDefault())) Look.Night else Look.Day

/** Sets [Palette.current] from the saved mode, before anything is drawn. */
fun applyTheme(context: Context) {
    val prefs = ThemePrefs(context)
    Palette.mode = prefs.mode
    Palette.current = colorsFor(prefs, Palette.mode)
}

/**
 * Keeps the palette in step with [Palette.mode]. Auto finds the place from the IP address (once a day,
 * kept for when the lookup fails) and follows the sun. The switch is a 400 ms fade, and only while the
 * app is in front ([resumed]): never during a video, it waits for the next screen.
 */
@Composable
fun FollowTheme(plex: PlexClient, resumed: State<Boolean>) {
    val context = LocalContext.current
    val mode = Palette.mode
    LaunchedEffect(mode) {
        val prefs = ThemePrefs(context)
        prefs.mode = mode
        if (mode == ThemeMode.Auto && prefs.manual == null && System.currentTimeMillis() - prefs.fromIpAt > 24 * 3600_000L) {
            // City-level is plenty for sunset. Offline: the last place found, or dark 19:00–07:00.
            runCatching { lookUpIp(plex) }.getOrNull()?.let { prefs.fromIp = it }
        }
        while (true) {
            if (!resumed.value) snapshotFlow { resumed.value }.first { it }
            val target = colorsFor(prefs, mode)
            if (target !== Palette.current) {
                val from = Palette.current
                animate(0f, 1f, animationSpec = tween(400)) { t, _ -> Palette.current = from.lerp(target, t) }
                Palette.current = target
            }
            if (mode != ThemeMode.Auto) break
            // ponytail: checks once a minute (or on coming back to the app) rather than sleeping until the
            // next sunrise or sunset; also covers a TV that slept through the switch.
            withTimeoutOrNull(60_000) { snapshotFlow { resumed.value }.first { !it } }
            delay(1)
        }
    }
}

/** Where the TV's IP address is, roughly: ipapi.co's city. */
suspend fun lookUpIp(plex: PlexClient): Place {
    val j = JSONObject(plex.call(Request.Builder().url("https://ipapi.co/json/").build()))
    val name = listOf(j.optString("city"), j.optString("country_code")).filter { it.isNotBlank() }.joinToString(", ")
    return Place(name, j.getDouble("latitude"), j.getDouble("longitude"))
}

/** Dark before sunrise and after sunset; without a place, from 19:00 to 07:00. */
fun isDark(mode: ThemeMode, place: Pair<Double, Double>?, now: Instant, zone: ZoneId): Boolean = when (mode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.Auto -> {
        val local = now.atZone(zone)
        if (place == null) local.hour >= 19 || local.hour < 7
        else sunTimes(local.toLocalDate(), place.first, place.second).let { (rise, set) -> now < rise || now >= set }
    }
}

/**
 * Sunrise and sunset on [date] (the sunrise equation, good to a minute or two). Where the sun doesn't
 * rise (polar night), both are noon, so it's dark all day; where it doesn't set, they're a day apart.
 */
fun sunTimes(date: LocalDate, lat: Double, lng: Double): Pair<Instant, Instant> {
    fun rad(d: Double) = d * PI / 180
    val j = date.toEpochDay() - 10957 - lng / 360 // days since J2000, at local solar noon
    val m = (357.5291 + 0.98560028 * j) % 360
    val c = 1.9148 * sin(rad(m)) + 0.02 * sin(rad(2 * m)) + 0.0003 * sin(rad(3 * m))
    val lambda = (m + c + 180 + 102.9372) % 360
    val transit = 2451545.0 + j + 0.0053 * sin(rad(m)) - 0.0069 * sin(rad(2 * lambda))
    val decl = asin(sin(rad(lambda)) * sin(rad(23.4397)))
    val cosW = (sin(rad(-0.833)) - sin(rad(lat)) * sin(decl)) / (cos(rad(lat)) * cos(decl))
    val halfDay = acos(cosW.coerceIn(-1.0, 1.0)) / (2 * PI) // in days
    fun instant(julian: Double) = Instant.ofEpochMilli(((julian - 2440587.5) * 86_400_000).toLong())
    return instant(transit - halfDay) to instant(transit + halfDay)
}

/** Places to pick from when the IP address puts the TV in the wrong city. */
object Places {
    val all = listOf(
        Place("Amsterdam, NL", 52.37, 4.90), Place("Athens, GR", 37.98, 23.73), Place("Auckland, NZ", -36.85, 174.76),
        Place("Bangkok, TH", 13.76, 100.50), Place("Barcelona, ES", 41.39, 2.17), Place("Belgrade, RS", 44.79, 20.45),
        Place("Berlin, DE", 52.52, 13.40), Place("Bratislava, SK", 48.15, 17.11), Place("Bucharest, RO", 44.43, 26.10),
        Place("Budapest, HU", 47.50, 19.04), Place("Buenos Aires, AR", -34.60, -58.38), Place("Cape Town, ZA", -33.92, 18.42),
        Place("Chicago, US", 41.88, -87.63), Place("Copenhagen, DK", 55.68, 12.57), Place("Debrecen, HU", 47.53, 21.63),
        Place("Dublin, IE", 53.35, -6.26), Place("Győr, HU", 47.69, 17.63), Place("Helsinki, FI", 60.17, 24.94),
        Place("Istanbul, TR", 41.01, 28.98), Place("Lisbon, PT", 38.72, -9.14), Place("Ljubljana, SI", 46.06, 14.51),
        Place("London, GB", 51.51, -0.13), Place("Los Angeles, US", 34.05, -118.24), Place("Madrid, ES", 40.42, -3.70),
        Place("Miskolc, HU", 48.10, 20.78), Place("Montreal, CA", 45.50, -73.57), Place("Munich, DE", 48.14, 11.58),
        Place("New York, US", 40.71, -74.01), Place("Oslo, NO", 59.91, 10.75), Place("Paris, FR", 48.86, 2.35),
        Place("Pécs, HU", 46.07, 18.23), Place("Prague, CZ", 50.08, 14.44), Place("Reykjavík, IS", 64.15, -21.94),
        Place("Rome, IT", 41.90, 12.50), Place("São Paulo, BR", -23.55, -46.63), Place("Seoul, KR", 37.57, 126.98),
        Place("Stockholm, SE", 59.33, 18.07), Place("Sydney, AU", -33.87, 151.21), Place("Szeged, HU", 46.25, 20.15),
        Place("Tokyo, JP", 35.68, 139.69), Place("Toronto, CA", 43.65, -79.38), Place("Tromsø, NO", 69.65, 18.96),
        Place("Vienna, AT", 48.21, 16.37), Place("Warsaw, PL", 52.23, 21.01), Place("Zagreb, HR", 45.81, 15.98),
        Place("Zürich, CH", 47.37, 8.54),
    )
}
