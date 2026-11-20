package hu.molnarroland.plexverse.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the app with the D-pad the way it's used: start, move along Home's rows, open what's focused
 * and scroll it, then a category from the main menu and its grid. Blind key presses, so it works on any library;
 * signed out, it only covers start-up and the sign-in screen.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = "hu.molnarroland.plexverse", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        fun press(times: Int, key: () -> Unit) = repeat(times) { key(); Thread.sleep(150) }

        // Home: along the rows and back.
        press(1) { device.pressDPadDown() }
        press(8) { device.pressDPadRight() }
        press(8) { device.pressDPadLeft() }
        press(2) { device.pressDPadDown() }
        press(4) { device.pressDPadRight() }

        // Into whatever is focused (a library or a show), scroll it, back out.
        device.pressDPadCenter(); Thread.sleep(1500)
        press(12) { device.pressDPadDown() }
        press(4) { device.pressDPadRight() }
        press(12) { device.pressDPadUp() }
        device.pressBack(); Thread.sleep(800)

        // A category from the main menu, and its grid.
        press(6) { device.pressDPadUp() }
        press(4) { device.pressDPadLeft() }
        press(1) { device.pressDPadRight() }
        device.pressDPadCenter(); Thread.sleep(3000)
        press(3) { device.pressDPadDown() }
        device.pressDPadCenter(); Thread.sleep(2000)
        press(15) { device.pressDPadDown() }
        press(15) { device.pressDPadUp() }
        device.pressBack(); Thread.sleep(800)
        device.pressBack()
    }
}
