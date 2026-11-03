package com.reggiesoft.dbmeter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reggiesoft.dbmeter.ui.SoundCaliperTheme
import com.reggiesoft.dbmeter.ui.MeterApp

class MainActivity : AppCompatActivity() {
    private val model: MeterViewModel by viewModels()
    private val preferences by lazy { getSharedPreferences("meter", MODE_PRIVATE) }
    private var permissionRequested = false
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.setPermission(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionRequested = savedInstanceState?.getBoolean("permissionRequested")
            ?: preferences.getBoolean("microphone_permission_requested", false)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val state by model.uiState.collectAsStateWithLifecycle()
            DisposableEffect(state.settings.keepAwake, state.capturing) {
                if (state.settings.keepAwake && state.capturing) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            SoundCaliperTheme {
                MeterApp(
                    state = state,
                    onTab = model::selectTab,
                    onPause = model::togglePause,
                    onReset = model::reset,
                    onSpeed = model::setSpeed,
                    onEnable = ::requestMicrophone,
                    onRetry = model::retry,
                    onInput = model::selectInput,
                    onKeepAwake = model::setKeepAwake,
                    onCalibrate = model::calibrate,
                    onResetCalibration = model::resetCalibration,
                    onSelectCalibrationUri = model::loadCalibrationUri,
                    onRemoveCalibrationFile = model::removeCalibrationFile
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.setVisible(true)
    }

    override fun onResume() {
        super.onResume()
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        model.setPermission(granted)
    }

    override fun onPostResume() {
        super.onPostResume()
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted && !permissionRequested) {
            requestMicrophone()
        }
    }

    override fun onStop() {
        model.setVisible(false)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permissionRequested", permissionRequested)
        super.onSaveInstanceState(outState)
    }

    private fun requestMicrophone() {
        if (permissionRequested && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        } else {
            permissionRequested = true
            preferences.edit().putBoolean("microphone_permission_requested", true).apply()
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
