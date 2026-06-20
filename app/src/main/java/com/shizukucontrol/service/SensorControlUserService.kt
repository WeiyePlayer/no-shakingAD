package com.shizukucontrol.service

import android.content.Context
import android.util.Log
import com.shizukucontrol.ISensorControlService
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku UserService that executes shell commands with ADB/shell (UID 2000) privileges.
 * This runs in a separate process managed by Shizuku.
 */
class SensorControlUserService : ISensorControlService.Stub {

    companion object {
        private const val TAG = "SensorControlUserSvc"
    }

    // Constructor with Context for Shizuku v13+
    @Suppress("unused")
    constructor(context: Context) {
        Log.d(TAG, "UserService created with Context")
    }

    // Default constructor for older Shizuku versions
    constructor() {
        Log.d(TAG, "UserService created (legacy)")
    }

    override fun executeCommand(command: String): String {
        Log.d(TAG, "Executing: $command")
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            process.waitFor()
            val result = if (stdout.isNotBlank()) stdout else stderr
            Log.d(TAG, "Result: ${result.take(200)}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Command failed", e)
            "ERROR: ${e.message}"
        }
    }

    override fun destroy() {
        Log.d(TAG, "destroy() called, exiting process")
        System.exit(0)
    }
}
