package pages

import config.AndroidConfig
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebElement
import support.Waits

// Ekran główny Ustawień. Świeży obraz google_apis nie ma id z Pixela
// (search_bar_title, android:id/title), więc szukamy tekstów z AOSP.
class SettingsPage(driver: AndroidDriver) : BasePage(driver) {

    fun waitForSettingsPackage(): String {
        return Waits.untilNotNull(driver) {
            val pkg = foregroundPackage()
            if (pkg == AndroidConfig.SETTINGS_PACKAGE) pkg else null
        }
    }

    // Napis z paska szukania, jeśli ten obraz go ma. Inaczej pierwszy znany wiersz.
    fun visibleHomeTitle(): String {
        return try {
            Waits.untilNotNull(driver) { homeTitleOrNull() }
        } catch (_: TimeoutException) {
            scrollUntilAny(HOME_ROWS)
        }
    }

    fun isHomeTitle(title: String): Boolean {
        if (title.contains("Settings", ignoreCase = true)) {
            return true
        }
        return HOME_ROWS.any { it == title }
    }

    fun visibleHomeRows(): List<String> {
        return candidatesOnScreen(HOME_ROWS)
    }

    fun waitUntilHomeRows(): List<String> {
        try {
            return Waits.untilNotNull(driver) {
                visibleHomeRows().takeIf { it.isNotEmpty() }
            }
        } catch (_: TimeoutException) {
            // Duży nagłówek albo pusty pierwszy kadr: zjedź do listy i wróć na górę.
            scrollUntilAny(HOME_ROWS)
            repeat(SCROLL_ATTEMPTS) { drag(fromYFraction = 0.35, toYFraction = 0.75) }
        }
        return try {
            Waits.untilNotNull(driver, timeoutSeconds = 5) {
                visibleHomeRows().takeIf { it.isNotEmpty() }
            }
        } catch (_: TimeoutException) {
            error("Brak wierszy ustawień. Szukane etykiety: $HOME_ROWS")
        }
    }

    fun waitUntilRowsChange(previous: List<String>): List<String> {
        var last = previous
        return try {
            Waits.untilNotNull(driver) {
                last = visibleHomeRows()
                if (last.isNotEmpty() && last != previous) last else null
            }
        } catch (_: TimeoutException) {
            error("Lista się nie zmieniła. Było $previous, jest $last")
        }
    }

    fun scrollUntilAbout(): String {
        return scrollUntilAny(ABOUT_ROWS)
    }

    fun openNetworkRow(): String {
        val label = scrollUntilAny(NETWORK_ROWS)
        tapLabel(label)
        return label
    }

    // Pozycja, która jest na stronie sieci, a nie na liście głównej.
    fun visibleNetworkDetail(): String {
        return try {
            Waits.untilNotNull(driver) {
                candidatesOnScreen(NETWORK_DETAILS).firstOrNull()
            }
        } catch (_: TimeoutException) {
            scrollUntilAny(NETWORK_DETAILS)
        }
    }

    fun goBack() {
        driver.navigate().back()
        Waits.untilNotNull(driver) {
            if (candidatesOnScreen(NETWORK_DETAILS).isNotEmpty()) {
                null
            } else {
                homeTitleOrNull()
            }
        }
    }

    fun scrollUntilAny(candidates: List<String>): String {
        repeat(SCROLL_ATTEMPTS) {
            candidatesOnScreen(candidates).firstOrNull()?.let { return it }
            scrollDown()
        }
        return try {
            Waits.untilNotNull(driver, timeoutSeconds = 5) {
                candidatesOnScreen(candidates).firstOrNull()
            }
        } catch (_: TimeoutException) {
            error("Nie widać żadnej z etykiet: $candidates")
        }
    }

    fun backToTop() {
        repeat(3) { drag(fromYFraction = 0.35, toYFraction = 0.75) }
    }

    fun scrollDown() {
        // Palec w górę: pod spodem widać kolejne wiersze.
        drag(fromYFraction = 0.75, toYFraction = 0.35)
    }

    private fun homeTitleOrNull(): String? {
        optionalSearchText()?.let { return it }
        return candidatesOnScreen(HOME_ROWS).firstOrNull { label ->
            NETWORK_ROWS.none { network -> network == label }
        }
    }

    // search_bar_title jest na Pixelu. Na czystym AOSP pasek bywa ukryty albo ma inny id.
    private fun optionalSearchText(): String? {
        val byId = findDisplayed(SEARCH_TITLE_ID)?.let { elementText(it) }.orEmpty()
        if (byId.contains("Settings", ignoreCase = true)) {
            return byId
        }
        return candidatesOnScreen(SEARCH_PHRASES).firstOrNull {
            it.contains("Settings", ignoreCase = true)
        }
    }

    private fun candidatesOnScreen(candidates: List<String>): List<String> {
        // Tekst z TextView to to samo, co widzi UiSelector.textContains, bez osobnego
        // zapytania na każdą etykietę.
        val fromNodes = pick(candidates, visibleNodeTexts())
        if (fromNodes.isNotEmpty()) {
            return fromNodes
        }
        return pick(candidates, selectorTexts(textMatches(candidates)) + selectorTexts(descriptionMatches(candidates)))
    }

    private fun pick(candidates: List<String>, shown: List<String>): List<String> {
        val found = mutableListOf<String>()
        for (label in candidates) {
            if (found.any { longer -> longer.contains(label) }) {
                continue
            }
            if (shown.any { it.contains(label) }) {
                found += label
            }
        }
        return found
    }

    private fun visibleNodeTexts(): List<String> {
        return driver.findElements(AppiumBy.className("android.widget.TextView")).mapNotNull { node ->
            runCatching {
                if (!node.isDisplayed) {
                    null
                } else {
                    elementText(node).takeIf { it.isNotEmpty() }
                }
            }.getOrNull()
        }
    }

    private fun selectorTexts(locator: By): List<String> {
        return runCatching {
            driver.findElements(locator).mapNotNull { node ->
                runCatching {
                    if (!node.isDisplayed) {
                        null
                    } else {
                        elementText(node).takeIf { it.isNotEmpty() }
                    }
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun textMatches(candidates: List<String>): By {
        val pattern = candidates.joinToString("|")
        return AppiumBy.androidUIAutomator(
            "new UiSelector().textMatches(\".*(${escapeUi(pattern)}).*\")"
        )
    }

    private fun descriptionMatches(candidates: List<String>): By {
        val pattern = candidates.joinToString("|")
        return AppiumBy.androidUIAutomator(
            "new UiSelector().descriptionMatches(\".*(${escapeUi(pattern)}).*\")"
        )
    }

    private fun tapLabel(label: String) {
        val element = findDisplayed(textContains(label))
            ?: findDisplayed(descriptionContains(label))
            ?: error("Nie ma etykiety '$label'")
        tappable(element).click()
    }

    private fun tappable(element: WebElement): WebElement {
        if (element.getAttribute("clickable") == "true") {
            return element
        }
        return runCatching {
            element.findElement(By.xpath("./ancestor::*[@clickable='true'][1]"))
        }.getOrNull() ?: element
    }

    private fun textContains(label: String): By {
        return AppiumBy.androidUIAutomator(
            "new UiSelector().textContains(\"${escapeUi(label)}\")"
        )
    }

    private fun descriptionContains(label: String): By {
        return AppiumBy.androidUIAutomator(
            "new UiSelector().descriptionContains(\"${escapeUi(label)}\")"
        )
    }

    private fun elementText(element: WebElement): String {
        val text = element.text?.trim().orEmpty()
        if (text.isNotEmpty()) {
            return text
        }
        return element.getAttribute("content-desc")?.trim().orEmpty()
    }

    private fun foregroundPackage(): String? {
        val scripted = runCatching {
            driver.executeScript("mobile: getCurrentPackage")?.toString()?.trim()
        }.getOrNull()
        if (!scripted.isNullOrEmpty() && scripted != "null") {
            return scripted
        }
        return runCatching { driver.currentPackage }.getOrNull()
    }

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

    private fun escapeUi(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private companion object {
        const val SCROLL_ATTEMPTS = 8

        val SEARCH_TITLE_ID: By = AppiumBy.id("com.android.settings:id/search_bar_title")

        val SEARCH_PHRASES = listOf(
            "Search settings",
            "Search Settings"
        )

        // Kolejność: najpierw dłuższa etykieta, żeby "Sound" nie połknęło "Sound & vibration".
        val HOME_ROWS = listOf(
            "Network & internet",
            "Network and internet",
            "Connected devices",
            "Notifications",
            "Battery",
            "Storage",
            "Sound & vibration",
            "Sound",
            "Display",
            "Wallpaper & style",
            "Wallpaper",
            "Accessibility",
            "Security & privacy",
            "Safety & emergency",
            "Passwords, passkeys & accounts",
            "Passwords & accounts",
            "Privacy",
            "Security",
            "Location",
            "System",
            "Modes",
            "Apps",
            "About emulated device",
            "About phone",
            "About device",
            "About tablet",
            "Tips & support"
        )

        val ABOUT_ROWS = listOf(
            "About emulated device",
            "About phone",
            "About device",
            "About tablet"
        )

        val NETWORK_ROWS = listOf(
            "Network & internet",
            "Network and internet"
        )

        val NETWORK_DETAILS = listOf(
            "Airplane mode",
            "Hotspot & tethering",
            "Data Saver",
            "Private DNS",
            "SIMs",
            "Internet",
            "VPN"
        )
    }
}
