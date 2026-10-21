package tests

import driver.DriverFactory
import io.appium.java_client.android.AndroidDriver
import io.qameta.allure.Allure
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
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
    // DEVICE i API_LEVEL są tylko w macierzy CI. Bez nich nazwa testu zostaje jak była.
    @BeforeEach
    fun startDriver(info: TestInfo) {
        labelAllureMatrixSession(info)
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

    private fun labelAllureMatrixSession(info: TestInfo) {
        val device = System.getenv("DEVICE")?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val api = System.getenv("API_LEVEL")?.trim()?.takeIf { it.isNotEmpty() } ?: return
        Allure.parameter("device", device)
        Allure.parameter("api", api)
        val method = info.testMethod.map { it.name }.orElseGet {
            info.displayName.removeSuffix("()")
        }
        Allure.getLifecycle().updateTestCase { result ->
            result.name = "$method [$device API $api]"
        }
    }
}
