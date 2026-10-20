package driver

import config.AndroidConfig
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import java.time.Duration

object DriverFactory {
    private var driver: AndroidDriver? = null

    fun start(appPackage: String, appActivity: String): AndroidDriver {
        // Jedna sesja na raz. Druga w tym samym czasie psuje instrumentację.
        quit()

        val options = UiAutomator2Options()
        options.setPlatformName("Android")
        options.setAutomationName("UiAutomator2")
        options.setDeviceName(AndroidConfig.DEVICE_NAME)
        options.setUdid(AndroidConfig.UDID)
        options.setAppPackage(appPackage)
        options.setAppActivity(appActivity)
        // Chrome first-run i okno Ustawień nie nazywają się tak jak aktywność, którą uruchamiamy.
        options.setAppWaitActivity("*")
        options.setAppWaitDuration(Duration.ofSeconds(40))
        options.setNoReset(true)
        // noReset zostawia proces aplikacji. Bez tego Appium nie otworzy aktywności,
        // gdy proces już żyje. -S leci dopiero po starcie serwera (ensureAppStarts).
        options.setCapability("appium:forceAppLaunch", true)
        options.setNewCommandTimeout(Duration.ofSeconds(AndroidConfig.NEW_COMMAND_TIMEOUT_SECONDS))
        options.setUiautomator2ServerLaunchTimeout(Duration.ofSeconds(AndroidConfig.SERVER_LAUNCH_TIMEOUT_SECONDS))
        options.setUiautomator2ServerInstallTimeout(Duration.ofSeconds(AndroidConfig.SERVER_LAUNCH_TIMEOUT_SECONDS))
        options.setAdbExecTimeout(Duration.ofSeconds(60))
        options.setIgnoreHiddenApiPolicyError(true)

        var created: AndroidDriver? = null
        try {
            created = AndroidDriver(AndroidConfig.serverUrl(), options)
            // Bez implicit wait. Czekamy tylko tam, gdzie widać to w kodzie (Waits).
            created.manage().timeouts().implicitlyWait(Duration.ZERO)
            driver = created
            return created
        } catch (error: Exception) {
            runCatching { created?.quit() }
            driver = null
            throw error
        }
    }

    fun quit() {
        val current = driver
        driver = null
        runCatching { current?.quit() }
    }
}
