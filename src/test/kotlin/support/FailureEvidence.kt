package support

import io.appium.java_client.android.AndroidDriver
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
                "Zrzut: ${image.path}"
            } catch (error: Exception) {
                "Zrzut ekranu nie powiódł się: ${error.javaClass.simpleName}: ${error.message}"
            }
        }

        val trace = StringWriter()
        failure.printStackTrace(PrintWriter(trace))
        val firstLines = trace.toString().lineSequence().take(15).joinToString("\n").trimEnd()
        val uiDump = uiDump(driver)

        File(dir, "$baseName.txt").writeText(
            """
            Klasa: $className
            Metoda: $methodName
            Wyjątek: ${failure.javaClass.simpleName}: ${failure.message ?: "(brak komunikatu)"}
            $screenshotNote

            $firstLines

            $uiDump
            """.trimIndent() + "\n"
        )
    }

    private fun uiDump(driver: AndroidDriver?): String {
        if (driver == null) {
            return "Źródło UI: sesja nie powstała."
        }
        val pkg = runCatching {
            driver.executeScript("mobile: getCurrentPackage")?.toString()
        }.getOrNull()
        val activity = runCatching { driver.currentActivity() }.getOrNull()
        val source = try {
            driver.pageSource.orEmpty()
        } catch (error: Exception) {
            return """
                Pakiet: $pkg
                Aktywność: $activity
                Źródło UI nie powiodło się: ${error.javaClass.simpleName}: ${error.message}
            """.trimIndent()
        }
        val body = if (source.length > MAX_UI_DUMP_CHARS) {
            source.take(MAX_UI_DUMP_CHARS) + "\n… (obcięte, ${source.length} znaków)"
        } else {
            source
        }
        return """
            Pakiet: $pkg
            Aktywność: $activity
            Źródło UI:
            $body
        """.trimIndent()
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        const val MAX_UI_DUMP_CHARS = 5000
    }
}
