package pages

import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import support.Waits

abstract class BasePage(
    protected val driver: AndroidDriver
) {
    protected fun waitUntilVisible(locator: By): WebElement {
        return Waits.waitUntilVisible(driver, locator)
    }

    protected fun waitUntilGone(locator: By) {
        Waits.waitUntilGone(driver, locator)
    }

    protected fun waitUntilTextPresent(locator: By, text: String): WebElement {
        return Waits.waitUntilTextPresent(driver, locator, text)
    }

    protected fun findDisplayed(locator: By): WebElement? {
        return driver.findElements(locator).firstOrNull { element ->
            runCatching { element.isDisplayed }.getOrDefault(false)
        }
    }
}
