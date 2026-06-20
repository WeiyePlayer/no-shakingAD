package com.shizukucontrol.data.model

data class TargetApp(
    val packageName: String,
    val appName: String = ""
)

data class InstalledApp(
    val packageName: String,
    val appName: String
)

enum class SensorState {
    NORMAL,
    RESTRICTED,
    UNKNOWN,
    ERROR
}

enum class ShizukuStatus {
    LOADING,
    NOT_RUNNING,
    NEED_PERMISSION,
    READY,
    ERROR
}
