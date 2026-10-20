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

        val title = settings.visibleHomeTitle()
        assertTrue(settings.isHomeTitle(title)) {
            "Na ekranie powinien być napis Settings albo wiersz ustawień, a jest: '$title'"
        }

        // Pauza jest po to, żeby popatrzeć na emulator. Test jest stabilny dzięki jawnemu czekaniu, nie dzięki pauzie.
        Waits.pauseSeconds(3)

        assertTrue(settings.isHomeTitle(settings.visibleHomeTitle()))
    }

    @Test
    fun walkVisibleSettingsRows() {
        val settings = SettingsPage(driver)

        settings.waitForSettingsPackage()
        settings.backToTop()
        assertTrue(settings.waitUntilHomeRows().isNotEmpty()) {
            "Brak wierszy ustawień na ekranie głównym"
        }

        repeat(3) { step ->
            val before = settings.visibleHomeRows()
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

        val firstScreen = settings.waitUntilHomeRows()
        assertFalse(firstScreen.any { it.startsWith("About") }) {
            "About ma być poza pierwszym ekranem. Widać: $firstScreen"
        }

        val about = settings.scrollUntilAbout()
        assertTrue(about.startsWith("About")) {
            "Po przewinięciu powinno być About, a jest: '$about'"
        }
    }

    @Test
    fun openNetworkRowAndGoBack() {
        val settings = SettingsPage(driver)

        settings.waitForSettingsPackage()
        val network = settings.openNetworkRow()
        val detail = settings.visibleNetworkDetail()
        assertTrue(detail.isNotEmpty()) {
            "Po otwarciu '$network' powinna być widoczna pozycja sieci, a jest: '$detail'"
        }

        settings.goBack()
        val title = settings.visibleHomeTitle()
        assertTrue(settings.isHomeTitle(title)) {
            "Po cofnięciu powinien wrócić ekran główny ustawień, a jest: '$title'"
        }
    }
}
