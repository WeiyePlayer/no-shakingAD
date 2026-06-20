package com.shizukucontrol.viewmodel

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.util.Log
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

    // State holders
    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.LOADING)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    private val _sensorState = MutableStateFlow(SensorState.UNKNOWN)
    val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()

    private val _currentForegroundApp = MutableStateFlow<String?>(null)
    val currentForegroundApp: StateFlow<String?> = _currentForegroundApp.asStateFlow()

    private val _targetApp = MutableStateFlow<TargetApp?>(null)
    val targetApp: StateFlow<TargetApp?> = _targetApp.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

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
            Log.d(TAG, "UserService bound, checking sensor state")
            shizukuHelper.shouldAutoRebind = true
            viewModelScope.launch {
                _shizukuStatus.emit(ShizukuStatus.READY)
                refreshSensorState()
            }
        }
        shizukuHelper.setOnServiceDisconnected {
            Log.w(TAG, "UserService lost")
            viewModelScope.launch {
                _sensorState.emit(SensorState.UNKNOWN)
            }
        }
    }

    fun checkShizukuAndRequest() {
        val status = shizukuHelper.checkStatus()
        _shizukuStatus.value = status
        when (status) {
            ShizukuStatus.NEED_PERMISSION -> shizukuHelper.requestPermission()
            ShizukuStatus.READY -> shizukuHelper.bindUserService()
            else -> { /* Will show install guide in UI */ }
        }
    }

    // ── Foreground Detection ───────────────────────────────────────

    private fun setupForegroundDetection() {
        AppDetectionService.onForegroundAppChanged = { packageName ->
            viewModelScope.launch {
                handleForegroundChange(packageName)
            }
        }
    }

    private suspend fun handleForegroundChange(packageName: String?) {
        val prevApp = _currentForegroundApp.value
        _currentForegroundApp.emit(packageName)
        _isServiceRunning.emit(AppDetectionService.isRunning)

        val target = _targetApp.value ?: return
        if (packageName == null) return

        val prevIsTarget = prevApp == target.packageName
        val currentIsTarget = packageName == target.packageName
        val isSelf = packageName == app.packageName

        if (isSelf) return

        val now = System.currentTimeMillis()
        if (now - lastRestrictTime < DEBOUNCE_MS) {
            Log.d(TAG, "Debounced: too soon since last restrict")
            return
        }

        when {
            currentIsTarget && !prevIsTarget -> {
                recoveryJob?.cancel()
                Log.d(TAG, "Target app entered: $packageName → restricting sensors")
                restrictSensors()
                lastRestrictTime = now
            }
            !currentIsTarget && prevIsTarget -> {
                Log.d(TAG, "Target app left: $prevApp → scheduling recovery in ${RECOVERY_DELAY_MS}ms")
                scheduleRecovery()
            }
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
                val result = shizukuHelper.executeShell("dumpsys sensorservice restrict $packages")
                Log.d(TAG, "Restrict result: $result")
                if (result != null && !result.startsWith("ERROR")) {
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

    private fun scheduleRecovery() {
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            delay(RECOVERY_DELAY_MS)
            Log.d(TAG, "Recovery timer fired → enabling sensors")
            enableSensors()
        }
    }

    fun enableSensors() {
        viewModelScope.launch {
            try {
                recoveryJob?.cancel()
                recoveryJob = null
                val result = shizukuHelper.executeShell("dumpsys sensorservice enable")
                Log.d(TAG, "Enable result: $result")
                if (result != null && !result.startsWith("ERROR")) {
                    _sensorState.emit(SensorState.NORMAL)
                } else {
                    _sensorState.emit(SensorState.ERROR)
                }
            } catch (e: Exception) {
                Log.e(TAG, "enableSensors failed", e)
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
            } else {
                Log.e(TAG, "refreshSensorState: result was null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshSensorState failed", e)
        }
    }

    // ── Target App Management ──────────────────────────────────────

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
            if (_currentForegroundApp.value == packageName) {
                restrictSensors()
            }
        }
    }

    fun clearTargetApp() {
        viewModelScope.launch {
            _targetApp.emit(null)
            enableSensors()
        }
    }

    // ── Service Status ─────────────────────────────────────────────

    fun isAccessibilityServiceEnabled(): Boolean {
        return AppDetectionService.isRunning
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    // ── Cleanup ────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        recoveryJob?.cancel()
        shizukuHelper.shouldAutoRebind = false
        shizukuHelper.destroy()
    }
}
