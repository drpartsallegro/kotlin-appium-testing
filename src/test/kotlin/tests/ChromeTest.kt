package tests

import config.AndroidConfig
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.RegisterExtension
import pages.ChromePage
import java.io.File
import java.util.concurrent.TimeUnit

class ChromeTest : BaseTest() {

    companion object {
        // Sprawdzenie jest przed startem sesji. Brak Chrome pomija test, nie wywala całej klasy.
        @JvmField
        @RegisterExtension
        val requireChrome: BeforeEachCallback = BeforeEachCallback {
            Assumptions.assumeTrue(chromeInstalled()) {
                "Chrome is not installed on this emulator"
            }
        }

        private fun chromeInstalled(): Boolean {
            val sdk = System.getenv("ANDROID_HOME")
                ?: System.getenv("ANDROID_SDK_ROOT")
                ?: "D:/Android/Sdk"
            val exe = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
                "adb.exe"
            } else {
                "adb"
            }
            val adb = File(sdk, "platform-tools/$exe")
            val command = if (adb.exists()) adb.absolutePath else "adb"
            val process = ProcessBuilder(command, "shell", "pm", "path", AndroidConfig.CHROME_PACKAGE)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return false
            }
            return output.contains("package:")
        }
    }

    override val appPackage = AndroidConfig.CHROME_PACKAGE
    override val appActivity = AndroidConfig.CHROME_ACTIVITY

    @Test
    fun addressBarShowsTypedQuery() {
        val chrome = ChromePage(driver)

        val welcomeButtons = listOf(
            "Use without an account",
            "No thanks",
            "Accept & continue",
            "Got it"
        )

        // Ekrany powitalne Chrome. Klikamy pierwszy podpis z listy, który naprawdę widać.
        for (step in 1..4) {
            val tapped = chrome.waitForWelcomeButton(welcomeButtons) ?: break
            assertTrue(chrome.tapButtonWithText(tapped)) {
                "Krok $step: nie udało się kliknąć '$tapped'"
            }
            assertTrue(chrome.waitUntilButtonGone(tapped)) {
                "Krok $step: przycisk '$tapped' został na ekranie"
            }
        }

        chrome.focusAddressBar()
        chrome.typeQuery("appium")
        val typed = chrome.waitUntilAddressBarContains("appium")

        assertTrue(typed.contains("appium", ignoreCase = true)) {
            "Pasek adresu powinien zawierać 'appium', a jest: '$typed'"
        }
    }
}
