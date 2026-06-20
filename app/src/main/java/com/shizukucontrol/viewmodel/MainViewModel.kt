package com.shizukucontrol.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shizukucontrol.data.PreferencesManager
import com.shizukucontrol.data.model.ShizukuStatus
import com.shizukucontrol.data.model.SensorState
import com.shizukucontrol.data.model.TargetApp
import com.shizukucontrol.service.AppDetectionService
import com.shizukucontrol.util.ShizukuHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val RECOVERY_DELAY_MS = 5000L
        private const val DEBOUNCE_MS = 500L
    }

    private val app: Application = application
    private val prefs = PreferencesManager(app)
    val shizukuHelper = ShizukuHelper(app, viewModelScope)

    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.LOADING)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    private val _sensorState = MutableStateFlow(SensorState.UNKNOWN)
    val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()

    private val _currentForegroundApp = MutableStateFlow<String?>(null)
    val currentForegroundApp: StateFlow<String?> = _currentForegroundApp.asStateFlow()

    private val _targetApp = MutableStateFlow<TargetApp?>(null)
    val targetApp: StateFlow<TargetApp?> = _targetApp.asStateFlow()

    private val _isA11yRunning = MutableStateFlow(false)
    val isA11yRunning: StateFlow<Boolean> = _isA11yRunning.asStateFlow()

    private var recoveryJob: Job? = null
    private var lastRestrictTime: Long = 0

    init {
        setupShizukuCallbacks()
        setupForegroundDetection()
        loadSavedTarget()
    }

    // ── Shizuku Setup ──────────────────────────────────────────────

    private fun setupShizukuCallbacks() {
        shizukuHelper.setOnStatusChanged { status ->
            viewModelScope.launch { _shizukuStatus.emit(status) }
            if (status == ShizukuStatus.READY) {
                shizukuHelper.bindUserService()
            }
        }
        shizukuHelper.setOnServiceConnected {
            Log.d(TAG, "UserService bound")
            shizukuHelper.shouldAutoRebind = true
            viewModelScope.launch {
                _shizukuStatus.emit(ShizukuStatus.READY)
                refreshSensorState()
            }
        }
        shizukuHelper.setOnServiceDisconnected {
            Log.w(TAG, "UserService lost")
            viewModelScope.launch { _sensorState.emit(SensorState.UNKNOWN) }
        }
    }

    fun checkShizukuAndRequest() {
        val status = shizukuHelper.checkStatus()
        _shizukuStatus.value = status
        when (status) {
            ShizukuStatus.NEED_PERMISSION -> shizukuHelper.requestPermission()
            ShizukuStatus.READY -> shizukuHelper.bindUserService()
            else -> {}
        }
    }

    // ── Foreground Detection via AccessibilityService ─────────────

    private fun setupForegroundDetection() {
        AppDetectionService.onForegroundAppChanged = { packageName ->
            viewModelScope.launch {
                handleForegroundChange(packageName)
            }
        }
    }

    private suspend fun handleForegroundChange(packageName: String?) {
        _isA11yRunning.emit(AppDetectionService.isRunning)

        val target = _targetApp.value ?: return
        if (packageName == null) return
        val prevApp = _currentForegroundApp.value
        _currentForegroundApp.emit(packageName)

        if (packageName == app.packageName) return

        val now = System.currentTimeMillis()
        if (now - lastRestrictTime < DEBOUNCE_MS) return

        val currentIsTarget = packageName == target.packageName

        if (currentIsTarget) {
            recoveryJob?.cancel()
            Log.d(TAG, "Target app entered: $packageName → restricting, auto-recovery in ${RECOVERY_DELAY_MS}ms")
            restrictSensors()
            lastRestrictTime = now
        }
    }

    // ── Sensor Control ─────────────────────────────────────────────

    private fun restrictSensors() {
        viewModelScope.launch {
            try {
                val target = _targetApp.value ?: return@launch
                val whitelist = prefs.getWhitelistForCommand()
                val packages = buildString {
                    append(target.packageName)
                    if (whitelist.isNotEmpty()) append("|").append(whitelist)
                }
                val result = shizukuHelper.restrictSensorsRemote(packages, RECOVERY_DELAY_MS.toInt())
                Log.d(TAG, "restrictSensorsRemote result: $result")
                if (result != null && result.startsWith("OK")) {
                    _sensorState.emit(SensorState.RESTRICTED)
                } else {
                    _sensorState.emit(SensorState.ERROR)
                }
            } catch (e: Exception) {
                Log.e(TAG, "restrictSensors failed", e)
                _sensorState.emit(SensorState.ERROR)
            }
        }
    }

    fun enableSensors() {
        viewModelScope.launch {
            try {
                val result = shizukuHelper.executeShell("dumpsys sensorservice enable")
                if (result != null && !result.startsWith("ERROR")) {
                    _sensorState.emit(SensorState.NORMAL)
                } else {
                    _sensorState.emit(SensorState.ERROR)
                }
            } catch (e: Exception) {
                _sensorState.emit(SensorState.ERROR)
            }
        }
    }

    suspend fun refreshSensorState() {
        try {
            val result = shizukuHelper.executeShell("dumpsys sensorservice")
            if (result != null) {
                val isNormal = result.contains("Mode : NORMAL")
                val isRestricted = result.contains("Mode : RESTRICTED")
                Log.d(TAG, "refreshSensorState: isNormal=$isNormal, isRestricted=$isRestricted")
                when {
                    isNormal -> _sensorState.emit(SensorState.NORMAL)
                    isRestricted -> _sensorState.emit(SensorState.RESTRICTED)
                    result.startsWith("ERROR") -> _sensorState.emit(SensorState.ERROR)
                    else -> _sensorState.emit(SensorState.UNKNOWN)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "refreshSensorState failed", e) }
    }

    // ── Target App ─────────────────────────────────────────────────

    private fun loadSavedTarget() {
        viewModelScope.launch {
            val pkg = prefs.targetAppPackage.first()
            val name = prefs.targetAppName.first()
            if (!pkg.isNullOrEmpty()) {
                _targetApp.emit(TargetApp(pkg, name ?: ""))
            }
        }
    }

    fun setTargetApp(packageName: String, appName: String) {
        viewModelScope.launch {
            _targetApp.emit(TargetApp(packageName, appName))
            prefs.setTargetApp(packageName, appName)
        }
    }

    fun clearTargetApp() {
        viewModelScope.launch {
            _targetApp.emit(null)
            enableSensors()
        }
    }

    // ── A11y Service ───────────────────────────────────────────────

    fun checkA11yRunning(): Boolean = AppDetectionService.isRunning

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    // ── Refresh ─────────────────────────────────────────────────────

    fun manualRestrict() {
        viewModelScope.launch {
            try {
                val target = _targetApp.value
                if (target == null) return@launch
                val whitelist = prefs.getWhitelistForCommand()
                val packages = buildString {
                    append(target.packageName)
                    if (whitelist.isNotEmpty()) append("|").append(whitelist)
                }
                val result = shizukuHelper.executeShell("dumpsys sensorservice restrict $packages")
                Log.d(TAG, "Manual restrict result: $result")
                if (result != null && !result.startsWith("ERROR")) {
                    _sensorState.emit(SensorState.RESTRICTED)
                } else {
                    _sensorState.emit(SensorState.ERROR)
                }
            } catch (e: Exception) {
                Log.e(TAG, "manualRestrict failed", e)
                _sensorState.emit(SensorState.ERROR)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshSensorState()
        }
    }

    override fun onCleared() {
        super.onCleared()
        recoveryJob?.cancel()
        shizukuHelper.shouldAutoRebind = false
        shizukuHelper.destroy()
    }
}
