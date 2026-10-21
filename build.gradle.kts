plugins {
    kotlin("jvm") version "2.2.20"
}

repositories {
    mavenCentral()
}

dependencies {
    // Client 10.1.1 is built for Selenium 4.42 and Appium 3.
    // Latest 9.x (9.5.0) does not compile on Selenium 4.42.
    testImplementation("io.appium:java-client:10.1.1")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.42.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // 2.35.5 is the Allure 2 JUnit 5 adapter. The allure-junit5 coordinate
    // relocates to allure-jupiter; the listener is the published SPI, not AspectJ.
    testImplementation("io.qameta.allure:allure-junit5:2.35.5")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    // One emulator, one Appium session. Gradle must not fork a second JVM
    // while UiAutomator2 is still starting on emulator-5554.
    maxParallelForks = 1
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
    systemProperty(
        "allure.results.directory",
        project.layout.buildDirectory.dir("allure-results").get().asFile.absolutePath
    )
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
