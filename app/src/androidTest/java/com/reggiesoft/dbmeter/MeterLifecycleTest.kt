package com.reggiesoft.dbmeter

import android.Manifest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.reggiesoft.dbmeter.data.SettingsStore
import com.reggiesoft.dbmeter.model.MeterState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MeterLifecycleTest {
    @get:Rule
    val microphonePermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test
    fun backgroundStopsCaptureAndRecreationPreservesPause() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: MeterViewModel
            scenario.onActivity { model = ViewModelProvider(it)[MeterViewModel::class.java] }
            model.await { it.capturing && it.hasReading }
            scenario.moveToState(Lifecycle.State.CREATED)
            model.await { !it.visible && !it.capturing }
            scenario.moveToState(Lifecycle.State.RESUMED)
            model.await { it.visible && it.capturing }
            scenario.onActivity { model.togglePause() }
            val paused = model.await { it.paused && !it.capturing }
            scenario.recreate()
            scenario.onActivity { model = ViewModelProvider(it)[MeterViewModel::class.java] }
            assertTrue(model.uiState.value.paused)
            assertFalse(model.uiState.value.capturing)
            assertEquals(paused.measurement, model.uiState.value.measurement)
            scenario.onActivity { model.togglePause() }
            model.await { it.capturing }
        }
    }

    @Test
    fun calibrationPinsActualInputPersistsAndResetsTogether() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: MeterViewModel
            scenario.onActivity { model = ViewModelProvider(it)[MeterViewModel::class.java] }
            scenario.onActivity { model.selectInput("auto") }
            model.await { it.capturing && it.hasReading && it.actualInputKey != "auto" }
            var expectedOffset = 0.0
            var calibratedInput = ""
            var previousReset = 0
            scenario.onActivity {
                val before = model.uiState.value
                expectedOffset = before.settings.offsetDb + 70.0 - before.measurement.aDb
                calibratedInput = before.actualInputKey
                previousReset = before.resetVersion
                model.calibrate(70.0)
                val after = model.uiState.value
                assertEquals(expectedOffset, after.settings.offsetDb, 0.0001)
                assertEquals(calibratedInput, after.settings.inputKey)
                assertTrue(after.settings.calibrated)
                assertEquals(previousReset + 1, after.resetVersion)
                assertFalse(after.hasReading)
            }
            val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
            val persisted = SettingsStore(application).load()
            assertEquals(expectedOffset, persisted.offsetDb, 0.001)
            assertEquals(calibratedInput, persisted.inputKey)
            model.await { it.capturing && it.hasReading }
            scenario.onActivity { model.selectInput("auto") }
            assertFalse(model.uiState.value.settings.calibrated)
            scenario.onActivity { model.selectInput(calibratedInput) }
            assertEquals(expectedOffset, model.uiState.value.settings.offsetDb, 0.001)
            scenario.onActivity {
                model.resetCalibration()
                model.selectInput("auto")
            }
            assertFalse(model.uiState.value.settings.calibrated)
            assertEquals(110.0, model.uiState.value.settings.offsetDb, 0.001)
            model.await { it.capturing && it.hasReading }
        }
    }

    @Test
    fun calibrationFileLoadsAppliesSensFactorAndPersists() {
        val sampleCal = """"Sens Factor =-3.2dB, SERNO: 7012345"
10.0 -2.5
20.0 -1.0
1000.0 0.0
20000.0 1.2
"""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: MeterViewModel
            scenario.onActivity { model = ViewModelProvider(it)[MeterViewModel::class.java] }
            scenario.onActivity {
                model.loadCalibrationContent("7012345.txt", sampleCal)
            }
            val state = model.uiState.value
            assertEquals("7012345.txt", state.settings.calibrationFileName)
            assertEquals(4, state.settings.calibrationPoints.size)
            assertEquals(97.2, state.settings.offsetDb, 0.001)
            assertTrue(state.settings.calibrated)

            val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
            val persisted = SettingsStore(application).load()
            assertEquals("7012345.txt", persisted.calibrationFileName)
            assertEquals(4, persisted.calibrationPoints.size)
            assertEquals(97.2, persisted.offsetDb, 0.001)

            scenario.onActivity {
                model.removeCalibrationFile()
            }
            val cleared = model.uiState.value
            assertTrue(cleared.settings.calibrationFileName.isEmpty())
            assertTrue(cleared.settings.calibrationPoints.isEmpty())

            val persistedCleared = SettingsStore(application).load()
            assertTrue(persistedCleared.calibrationFileName.isEmpty())
            assertTrue(persistedCleared.calibrationPoints.isEmpty())
        }
    }
}

private fun MeterViewModel.await(predicate: (MeterState) -> Boolean): MeterState {
    return runBlocking {
        withTimeout(15_000) { uiState.first(predicate) }
    }
}
