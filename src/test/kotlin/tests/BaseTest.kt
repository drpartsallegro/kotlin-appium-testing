package tests

import driver.DriverFactory
import io.appium.java_client.android.AndroidDriver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import support.FailureEvidence

// Wspólny start i stop sesji. Podklasa podaje tylko aplikację.
// FailureEvidence zapisuje zrzut zanim quit() zamknie sesję.
@ExtendWith(FailureEvidence::class)
abstract class BaseTest {

    protected lateinit var driver: AndroidDriver

    internal fun startedDriver(): AndroidDriver? {
        return if (::driver.isInitialized) driver else null
    }

    protected abstract val appPackage: String
    protected abstract val appActivity: String

    // Przed każdym testem jedna sesja. Capabilities buduje DriverFactory.
    @BeforeEach
    fun startDriver() {
        driver = DriverFactory.start(appPackage, appActivity)
    }

    // Po teście zawsze zamykamy sesję, także gdy start się nie udał.
    @AfterEach
    fun stopDriver() {
        try {
            if (::driver.isInitialized) {
                runCatching { driver.quit() }
            }
        } finally {
            DriverFactory.quit()
        }
    }
}
