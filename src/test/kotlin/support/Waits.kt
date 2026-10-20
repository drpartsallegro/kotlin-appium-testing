package support

import config.AndroidConfig
import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.By
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

// Małe API czekania. Implicit wait zostaje 0 — tu widać, na co test czeka.
object Waits {

    fun pauseSeconds(seconds: Long) {
        // Sen na sztywno. Nic nie sprawdza. Zostaw to jednej demonstracji.
        Thread.sleep(Duration.ofSeconds(seconds).toMillis())
    }

    fun waitUntilVisible(
        driver: AndroidDriver,
        locator: By,
        timeoutSeconds: Long = AndroidConfig.EXPLICIT_WAIT_SECONDS
    ): WebElement {
        // Element jest na ekranie. Jak go nie ma, test pada po EXPLICIT_WAIT_SECONDS.
        return newWait(driver, timeoutSeconds)
            .until(ExpectedConditions.visibilityOfElementLocated(locator))
    }

    fun waitUntilGone(
        driver: AndroidDriver,
        locator: By,
        timeoutSeconds: Long = AndroidConfig.EXPLICIT_WAIT_SECONDS
    ) {
        // Element zniknął albo wcale go nie ma w drzewie.
        newWait(driver, timeoutSeconds)
            .until(ExpectedConditions.invisibilityOfElementLocated(locator))
    }

    fun waitUntilTextPresent(
        driver: AndroidDriver,
        locator: By,
        text: String,
        timeoutSeconds: Long = AndroidConfig.EXPLICIT_WAIT_SECONDS
    ): WebElement {
        // Ten sam id bywa na wielu wierszach. Czekamy na wiersz z tym tekstem.
        return newWait(driver, timeoutSeconds).until {
            driver.findElements(locator).firstOrNull { element ->
                runCatching {
                    element.isDisplayed && element.text.trim() == text
                }.getOrDefault(false)
            }
        }
    }

    fun <T : Any> untilNotNull(
        driver: AndroidDriver,
        timeoutSeconds: Long = AndroidConfig.EXPLICIT_WAIT_SECONDS,
        condition: () -> T?
    ): T {
        // Własny warunek, na przykład nazwa pakietu. Null znaczy „jeszcze nie”.
        return newWait(driver, timeoutSeconds).until { condition() }
    }

    private fun newWait(driver: AndroidDriver, timeoutSeconds: Long): WebDriverWait {
        return WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).apply {
            ignoring(StaleElementReferenceException::class.java)
        }
    }
}
