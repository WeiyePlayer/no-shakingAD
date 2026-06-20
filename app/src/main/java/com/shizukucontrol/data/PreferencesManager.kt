package com.shizukucontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sensor_control")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_TARGET_APP_PACKAGE = stringPreferencesKey("target_app_package")
        private val KEY_TARGET_APP_NAME = stringPreferencesKey("target_app_name")
        private val KEY_WHITELIST_PACKAGES = stringPreferencesKey("whitelist_packages")
        private val KEY_SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        private val KEY_AUTO_RECOVERY_MS = stringPreferencesKey("auto_recovery_ms")

        const val DEFAULT_RECOVERY_MS = 5000L
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }

    val targetAppPackage: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_TARGET_APP_PACKAGE]
    }

    val targetAppName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_TARGET_APP_NAME]
    }

    val whitelistPackages: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_WHITELIST_PACKAGES] ?: SYSTEM_UI_PACKAGE
    }

    val autoRecoveryMs: Flow<Long> = context.dataStore.data.map { prefs ->
        (prefs[KEY_AUTO_RECOVERY_MS]?.toLongOrNull()) ?: DEFAULT_RECOVERY_MS
    }

    suspend fun setTargetApp(packageName: String, appName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TARGET_APP_PACKAGE] = packageName
            prefs[KEY_TARGET_APP_NAME] = appName
        }
    }

    suspend fun setWhitelistPackages(packages: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WHITELIST_PACKAGES] = packages
        }
    }

    suspend fun setAutoRecoveryMs(ms: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_RECOVERY_MS] = ms.toString()
        }
    }

    /**
     * Get whitelist as a list of pipe-separated package names for dumpsys command.
     * Always includes com.android.systemui.
     */
    suspend fun getWhitelistForCommand(): String {
        val packages = whitelistPackages.first()
        val list = packages.split("|").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (!list.contains(SYSTEM_UI_PACKAGE)) {
            list.add(SYSTEM_UI_PACKAGE)
        }
        return list.joinToString("|")
    }
}
