package support

import io.appium.java_client.android.AndroidDriver
import io.qameta.allure.Allure
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.openqa.selenium.OutputType
import org.opentest4j.TestAbortedException
import tests.BaseTest
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Po metodzie testu, a przed @AfterEach. quit() jest dopiero później, więc sesja jeszcze żyje.
class FailureEvidence : AfterTestExecutionCallback {

    override fun afterTestExecution(context: ExtensionContext) {
        val failure = context.executionException.orElse(null) ?: return
        if (failure is TestAbortedException) {
            return
        }
        try {
            val test = context.testInstance.orElse(null) as? BaseTest
            save(context, failure, test?.startedDriver())
        } catch (error: Exception) {
            System.err.println("Nie udało się zapisać śladu błędu: ${error.message}")
        }
    }

    private fun save(context: ExtensionContext, failure: Throwable, driver: AndroidDriver?) {
        val className = context.requiredTestClass.simpleName
        val methodName = context.requiredTestMethod.name
        val stamp = LocalDateTime.now().format(STAMP)
        val dir = File("build/failures/$className")
        dir.mkdirs()
        val baseName = "$methodName-$stamp"
        val image = File(dir, "$baseName.png")

        val screenshotNote = if (driver == null) {
            "Sesja nie została utworzona, więc nie ma zrzutu ekranu."
        } else {
            try {
                val shot = driver.getScreenshotAs(OutputType.FILE)
                shot.copyTo(image, overwrite = true)
                "Zrzut ekranu zapisany."
            } catch (error: Exception) {
                "Zrzut ekranu nie powiódł się: ${error.javaClass.simpleName}: ${error.message}"
            }
        }

        val trace = StringWriter()
        failure.printStackTrace(PrintWriter(trace))
        val firstLines = trace.toString().lineSequence().take(15).joinToString("\n").trimEnd()
        // Bez źródła UI. Na Pages idzie zrzut ekranu i komunikat wyjątku, nie page source.
        val session = sessionNote(driver)

        val description = """
            Klasa: $className
            Metoda: $methodName
            Wyjątek: ${failure.javaClass.simpleName}: ${failure.message ?: "(brak komunikatu)"}
            $screenshotNote

            $firstLines

            $session
            """.trimIndent() + "\n"
        File(dir, "$baseName.txt").writeText(description)
        attachToAllure(image, description)
    }

    private fun attachToAllure(image: File, description: String) {
        try {
            if (image.isFile) {
                image.inputStream().use { stream ->
                    Allure.addAttachment("Zrzut ekranu", "image/png", stream, "png")
                }
            }
            Allure.addAttachment("Opis błędu", "text/plain", description, "txt")
        } catch (error: Exception) {
            System.err.println("Nie udało się dołączyć śladu do Allure: ${error.message}")
        }
    }

    private fun sessionNote(driver: AndroidDriver?): String {
        if (driver == null) {
            return "Sesja nie została utworzona."
        }
        val pkg = runCatching {
            driver.executeScript("mobile: getCurrentPackage")?.toString()
        }.getOrNull() ?: "(brak)"
        val activity = runCatching { driver.currentActivity() }.getOrNull() ?: "(brak)"
        return "Pakiet: $pkg\nAktywność: $activity"
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    }
}
