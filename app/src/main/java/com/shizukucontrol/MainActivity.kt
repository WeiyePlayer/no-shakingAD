package com.shizukucontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.shizukucontrol.data.model.SensorState
import com.shizukucontrol.ui.screens.MainScreen
import com.shizukucontrol.ui.screens.SplashScreen
import com.shizukucontrol.ui.theme.ShizukuControlTheme
import com.shizukucontrol.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ShizukuControlTheme {
                val viewModel: MainViewModel = viewModel()
                var showMain by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                val shizukuStatus by viewModel.shizukuStatus.collectAsState()
                val sensorState by viewModel.sensorState.collectAsState()
                val targetApp by viewModel.targetApp.collectAsState()
                val foregroundApp by viewModel.currentForegroundApp.collectAsState()
                val isServiceRunning by viewModel.isServiceRunning.collectAsState()

                if (!showMain) {
                    SplashScreen(
                        shizukuStatus = shizukuStatus,
                        onCheckStatus = { viewModel.checkShizukuAndRequest() },
                        onRequestPermission = {
                            viewModel.shizukuHelper.requestPermission()
                        },
                        onNavigateToMain = { showMain = true }
                    )
                } else {
                    MainScreen(
                        sensorState = sensorState,
                        targetApp = targetApp,
                        currentForegroundApp = foregroundApp,
                        isServiceRunning = isServiceRunning,
                        onSelectTargetApp = { app ->
                            viewModel.setTargetApp(app.packageName, app.appName)
                        },
                        onClearTargetApp = { viewModel.clearTargetApp() },
                        onToggleSensor = {
                            when (sensorState) {
                                SensorState.RESTRICTED -> viewModel.enableSensors()
                                else -> {
                                    val app = targetApp
                                    if (app != null) {
                                        scope.launch {
                                            viewModel.refreshSensorState()
                                        }
                                        viewModel.setTargetApp(app.packageName, app.appName)
                                    }
                                }
                            }
                        },
                        onRefreshState = {
                            scope.launch { viewModel.refreshSensorState() }
                        },
                        onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
