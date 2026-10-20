package tests

import config.AndroidConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pages.SettingsPage
import support.Waits

class SettingsTest : BaseTest() {

    override val appPackage = AndroidConfig.SETTINGS_PACKAGE
    override val appActivity = AndroidConfig.SETTINGS_ACTIVITY

    @Test
    fun settingsShowsPackageAndTitle() {
        val settings = SettingsPage(driver)

        assertEquals(AndroidConfig.SETTINGS_PACKAGE, settings.waitForSettingsPackage())

        val title = settings.visibleSearchLabel()
        assertTrue(title.contains("Settings")) {
            "Na ekranie powinien być napis Settings, a jest: '$title'"
        }

        // Pauza jest po to, żeby popatrzeć na emulator. Test jest stabilny dzięki jawnemu czekaniu, nie dzięki pauzie.
        Waits.pauseSeconds(3)

        assertTrue(settings.visibleSearchLabel().contains("Settings"))
    }

    @Test
    fun walkVisibleSettingsRows() {
        val settings = SettingsPage(driver)

        settings.waitForSettingsPackage()
        settings.backToTop()

        repeat(3) { step ->
            val before = settings.visibleRowTitles()
            assertTrue(before.isNotEmpty()) { "Krok $step: brak wierszy ustawień" }

            settings.scrollDown()
            val after = settings.waitUntilRowsChange(before)
            assertTrue(after != before) {
                "Krok $step: scroll nie zmienił listy. Było $before, jest $after"
            }
        }
    }

    @Test
    fun scrollReachesAboutDevice() {
        val settings = SettingsPage(driver)

        settings.waitForSettingsPackage()
        settings.backToTop()

        val firstScreen = settings.visibleRowTitles()
        assertFalse(firstScreen.contains("About emulated device")) {
            "About emulated device ma być poza pierwszym ekranem. Widać: $firstScreen"
        }

        settings.scrollUntilRow("About emulated device")

        assertEquals("About emulated device", settings.visibleRow("About emulated device"))
    }

    @Test
    fun openNetworkRowAndGoBack() {
        val settings = SettingsPage(driver)

        settings.waitForSettingsPackage()
        settings.openRow("Network & internet")
        settings.waitUntilHomeSearchGone()

        assertEquals("Network & internet", settings.toolbarDescription())
        assertEquals("Airplane mode", settings.visibleRow("Airplane mode"))

        settings.goBack()
        val title = settings.visibleSearchLabel()
        assertTrue(title.contains("Settings")) {
            "Po cofnięciu powinien wrócić napis Search Settings, a jest: '$title'"
        }
    }
}
