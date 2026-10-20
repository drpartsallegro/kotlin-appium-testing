package config

import java.net.URI
import java.net.URL

// One place to change the emulator or the Appium address.
object AndroidConfig {
    const val APPIUM_URL = "http://127.0.0.1:4723"
    const val UDID = "emulator-5554"
    const val DEVICE_NAME = "Android Emulator"

    const val NEW_COMMAND_TIMEOUT_SECONDS = 180L
    const val SERVER_LAUNCH_TIMEOUT_SECONDS = 120L

    // Named timeout for Waits.waitUntilVisible, waitUntilGone and waitUntilTextPresent.
    // Implicit wait stays 0 in DriverFactory, so a missing element is not hidden by a long sleep.
    const val EXPLICIT_WAIT_SECONDS = 15L

    const val SETTINGS_PACKAGE = "com.android.settings"
    const val SETTINGS_ACTIVITY = "com.android.settings.Settings"

    const val CHROME_PACKAGE = "com.android.chrome"
    const val CHROME_ACTIVITY = "com.google.android.apps.chrome.Main"

    fun serverUrl(): URL = URI(APPIUM_URL).toURL()
}
