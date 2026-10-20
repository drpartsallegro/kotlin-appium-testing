package pages

import config.AndroidConfig
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.TimeoutException
import support.Waits

class SettingsPage(driver: AndroidDriver) : BasePage(driver) {

    private val searchLabel = AppiumBy.id("com.android.settings:id/search_bar_title")
    private val rowTitle = AppiumBy.id("android:id/title")
    private val toolbar = AppiumBy.id("com.android.settings:id/collapsing_toolbar")

    fun waitForSettingsPackage(): String {
        return Waits.untilNotNull(driver) {
            val pkg = driver.currentPackage
            if (pkg == AndroidConfig.SETTINGS_PACKAGE) pkg else null
        }
    }

    // Na Androidzie 16 nie ma osobnego tytułu "Settings". Widać napis "Search Settings".
    fun visibleSearchLabel(): String {
        return waitUntilVisible(searchLabel).text
    }

    fun visibleRowTitles(): List<String> {
        waitUntilVisible(rowTitle)
        return driver.findElements(rowTitle).mapNotNull { row ->
            runCatching { row.text.trim() }.getOrNull()
        }.filter { it.isNotEmpty() }
    }

    fun visibleRow(title: String): String {
        return waitUntilTextPresent(rowTitle, title).text.trim()
    }

    fun openRow(title: String) {
        waitUntilVisible(clickableRow(title)).click()
    }

    fun waitUntilHomeSearchGone() {
        waitUntilGone(searchLabel)
    }

    fun toolbarDescription(): String {
        val bar = waitUntilVisible(toolbar)
        val described = bar.getAttribute("content-desc")?.trim().orEmpty()
        if (described.isNotEmpty()) {
            return described
        }
        return bar.getAttribute("contentDescription")?.trim().orEmpty()
    }

    fun goBack() {
        driver.navigate().back()
        waitUntilVisible(searchLabel)
    }

    fun scrollUntilRow(title: String) {
        val target = titleLocator(title)
        repeat(6) {
            if (findDisplayed(target) != null) {
                waitUntilVisible(target)
                return
            }
            scrollDown()
        }
        waitUntilVisible(target)
    }

    fun backToTop() {
        repeat(3) { drag(fromYFraction = 0.35, toYFraction = 0.75) }
    }

    fun scrollDown() {
        // Palec w górę: pod spodem widać kolejne wiersze.
        drag(fromYFraction = 0.75, toYFraction = 0.35)
    }

    private fun titleLocator(title: String) = AppiumBy.androidUIAutomator(
        "new UiSelector().resourceId(\"android:id/title\").text(\"$title\")"
    )

    private fun clickableRow(title: String) = AppiumBy.xpath(
        "//*[@text='$title']/ancestor::*[@clickable='true'][1]"
    )

    private fun drag(fromYFraction: Double, toYFraction: Double) {
        val size = driver.manage().window().size
        val x = size.width / 2
        driver.executeScript(
            "mobile: dragGesture",
            mapOf(
                "startX" to x,
                "startY" to (size.height * fromYFraction).toInt(),
                "endX" to x,
                "endY" to (size.height * toYFraction).toInt(),
                "speed" to 1200
            )
        )
    }

    fun waitUntilRowsChange(previous: List<String>): List<String> {
        var last = previous
        return try {
            Waits.untilNotNull(driver) {
                last = visibleRowTitles()
                if (last.isNotEmpty() && last != previous) last else null
            }
        } catch (_: TimeoutException) {
            error("Lista się nie zmieniła. Było $previous, jest $last")
        }
    }
}
