package pages

import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebElement
import support.Waits

class ChromePage(driver: AndroidDriver) : BasePage(driver) {

    private val searchBox = AppiumBy.id("com.android.chrome:id/search_box_text")
    private val urlBar = AppiumBy.id("com.android.chrome:id/url_bar")

    fun waitForWelcomeButton(buttonLabels: List<String>): String? {
        return try {
            val found = Waits.untilNotNull(driver, timeoutSeconds = 8) {
                when {
                    browserBarVisible() -> BROWSER_READY
                    else -> buttonLabels.firstOrNull { findByExactText(it) != null }
                }
            }
            found.takeUnless { it == BROWSER_READY }
        } catch (_: TimeoutException) {
            null
        }
    }

    fun tapButtonWithText(text: String): Boolean {
        val button = findByExactText(text) ?: return false
        tapCenter(button)
        return true
    }

    fun waitUntilButtonGone(text: String): Boolean {
        return try {
            Waits.waitUntilGone(
                driver,
                AppiumBy.androidUIAutomator("new UiSelector().text(\"$text\")"),
                timeoutSeconds = 8
            )
            true
        } catch (_: TimeoutException) {
            false
        }
    }

    fun focusAddressBar() {
        val opener = Waits.untilNotNull(driver) {
            findDisplayed(urlBar) ?: findDisplayed(searchBox)
        }
        val alreadyFocused = opener.getAttribute("resource-id").orEmpty().endsWith("url_bar") &&
            opener.getAttribute("focused") == "true"
        if (!alreadyFocused) {
            // Zwykły click() na pasku Chrome często nic nie robi. Klikamy środek elementu.
            tapCenter(opener)
        }
        try {
            waitUntilVisible(urlBar)
        } catch (_: TimeoutException) {
            error("Po kliknięciu nie ma paska adresu. Widzę: ${visibleTexts()}")
        }
    }

    private fun tapCenter(element: WebElement) {
        val area = element.rect
        driver.executeScript(
            "mobile: clickGesture",
            mapOf(
                "x" to area.x + area.width / 2,
                "y" to area.y + area.height / 2
            )
        )
    }

    private fun visibleTexts(): String {
        return driver.findElements(AppiumBy.className("android.widget.TextView"))
            .mapNotNull { runCatching { it.text.trim() }.getOrNull() }
            .filter { it.isNotEmpty() }
            .take(8)
            .joinToString(" | ")
    }

    fun typeQuery(query: String) {
        val bar = waitUntilVisible(urlBar)
        bar.click()
        bar.clear()
        bar.sendKeys(query)
    }

    fun addressBarText(): String {
        return waitUntilVisible(urlBar).text.orEmpty()
    }

    fun waitUntilAddressBarContains(query: String): String {
        return Waits.untilNotNull(driver) {
            val text = runCatching { addressBarText() }.getOrDefault("")
            if (text.contains(query, ignoreCase = true)) text else null
        }
    }

    private fun browserBarVisible(): Boolean {
        return findDisplayed(searchBox) != null || findDisplayed(urlBar) != null
    }

    private fun findByExactText(text: String) = findDisplayed(
        AppiumBy.androidUIAutomator("new UiSelector().text(\"$text\")")
    )

    private companion object {
        const val BROWSER_READY = "__browser__"
    }
}
