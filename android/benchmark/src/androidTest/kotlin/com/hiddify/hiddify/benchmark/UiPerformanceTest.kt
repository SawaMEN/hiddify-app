package com.hiddify.hiddify.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val APP = "app.vetroff.client"

@RunWith(AndroidJUnit4::class)
class UiPerformanceTest {
    @get:Rule val benchmark = MacrobenchmarkRule()
    @Test fun coldStartup() = benchmark.measureRepeated(APP,
        listOf(StartupTimingMetric()), compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.COLD, iterations = 5) {
        pressHome()
        startActivityAndWait()
    }
    @Test fun navigationFrames() = benchmark.measureRepeated(APP,
        listOf(FrameTimingMetric()), compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = 5, setupBlock = { pressHome(); startActivityAndWait() }) {
        for (destination in listOf("settings", "privacy", "home")) {
            val item = device.wait(Until.findObject(By.res("nav_$destination")), 5000)
            checkNotNull(item) { "Navigation destination not found: $destination" }.click()
            assertTrue(device.wait(Until.hasObject(By.res("page_$destination")), 5000))
            device.waitForIdle()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val profile = BaselineProfileRule()
    @Test fun startupAndNavigation() = profile.collect(APP, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        for (destination in listOf("settings", "privacy", "home")) {
            val item = device.wait(Until.findObject(By.res("nav_$destination")), 5000)
            checkNotNull(item).click()
            assertTrue(device.wait(Until.hasObject(By.res("page_$destination")), 5000))
            device.waitForIdle()
        }
    }
}
