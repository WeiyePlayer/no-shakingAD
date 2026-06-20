package com.shizukucontrol.util

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import com.shizukucontrol.ISensorControlService
import com.shizukucontrol.data.model.ShizukuStatus
import com.shizukucontrol.service.SensorControlUserService

/**
 * Helper class for Shizuku integration.
 * Manages permission lifecycle and UserService binding.
 */
class ShizukuHelper(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {

    companion object {
        private const val TAG = "ShizukuHelper"
        private const val USER_SERVICE_TAG = "sensor_control_service"
        private const val USER_SERVICE_VERSION = 1
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var userService: ISensorControlService? = null
    private var isBound = false
    private var onStatusChanged: ((ShizukuStatus) -> Unit)? = null
    private var onServiceConnected: (() -> Unit)? = null
    private var onServiceDisconnected: (() -> Unit)? = null

    /** Stored UserServiceArgs for unbind calls. */
    private var userServiceArgs: Shizuku.UserServiceArgs? = null

    /**
     * Rebind the UserService with exponential backoff (max 8 attempts, 1s-64s delay).
     */
    var shouldAutoRebind = false
    private var rebindAttempt = 0
    private val maxRebindAttempts = 8
    private var rebindJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "UserService connected")
            userService = ISensorControlService.Stub.asInterface(service)
            isBound = true
            rebindAttempt = 0
            onServiceConnected?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "UserService disconnected")
            userService = null
            isBound = false
            onServiceDisconnected?.invoke()
            if (shouldAutoRebind) {
                scheduleRebind()
            }
        }
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        Log.d(TAG, "Permission result: code=$requestCode, granted=$grantResult")
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onStatusChanged?.invoke(ShizukuStatus.READY)
        } else {
            onStatusChanged?.invoke(ShizukuStatus.NEED_PERMISSION)
        }
    }

    init {
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }

    fun setOnStatusChanged(callback: (ShizukuStatus) -> Unit) {
        this.onStatusChanged = callback
    }

    fun setOnServiceConnected(callback: () -> Unit) {
        this.onServiceConnected = callback
    }

    fun setOnServiceDisconnected(callback: () -> Unit) {
        this.onServiceDisconnected = callback
    }

    /**
     * Check Shizuku status and return the initial state.
     */
    fun checkStatus(): ShizukuStatus {
        return when {
            !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
            Shizuku.isPreV11() || Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                ShizukuStatus.READY
            }
            else -> ShizukuStatus.NEED_PERMISSION
        }
    }

    /**
     * Request Shizuku permission (shows system dialog).
     */
    fun requestPermission() {
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0)
        }
    }

    /**
     * Bind the UserService to execute shell commands.
     * Always dispatches to the main thread to ensure ServiceConnection callbacks work.
     */
    fun bindUserService() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { bindUserService() }
            return
        }
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context, SensorControlUserService::class.java)
            )
                .tag(USER_SERVICE_TAG)
                .version(USER_SERVICE_VERSION)
                .daemon(false)
                .processNameSuffix(":sensor_control")

            userServiceArgs = args
            Shizuku.bindUserService(args, serviceConnection)
            Log.d(TAG, "bindUserService called on main thread")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind UserService", e)
        }
    }

    /**
     * Unbind the UserService and trigger cleanup.
     */
    fun unbindUserService() {
        try {
            userService?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "UserService destroy failed", e)
        }
        try {
            val args = userServiceArgs
            if (args != null) {
                Shizuku.unbindUserService(args, serviceConnection, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "unbindUserService failed", e)
        }
        userService = null
        isBound = false
    }

    /**
     * Execute a shell command with Shizuku privileges.
     * Returns the command output or null if not connected.
     */
    fun executeShell(command: String): String? {
        return try {
            userService?.executeCommand(command)
        } catch (e: Exception) {
            Log.e(TAG, "executeShell failed: $command", e)
            null
        }
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        shouldAutoRebind = false
        rebindJob?.cancel()
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        if (isBound) {
            unbindUserService()
        }
    }

    private fun scheduleRebind() {
        if (rebindAttempt >= maxRebindAttempts) {
            Log.e(TAG, "Max rebind attempts reached")
            onStatusChanged?.invoke(ShizukuStatus.ERROR)
            return
        }
        rebindAttempt++
        val delayMs = Math.min((1L shl rebindAttempt) * 1000L, 64000L)
        Log.d(TAG, "Rebind attempt $rebindAttempt in ${delayMs}ms")

        rebindJob?.cancel()
        rebindJob = coroutineScope.launch {
            delay(delayMs)
            if (shouldAutoRebind && Shizuku.pingBinder()) {
                bindUserService()
            }
        }
    }
}
