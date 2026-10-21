package support

import io.qameta.allure.Allure

// Allure.step, not @Step: the JUnit 5 listener records these without AspectJ.
fun <T> allureStep(name: String, action: () -> T): T {
    return Allure.step(name, Allure.ThrowableRunnable { action() })
}
