package com.shizukucontrol.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shizukucontrol.data.model.InstalledApp
import com.shizukucontrol.data.model.SensorState
import com.shizukucontrol.data.model.TargetApp
import com.shizukucontrol.ui.components.AppSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sensorState: SensorState,
    targetApp: TargetApp?,
    currentForegroundApp: String?,
    isServiceRunning: Boolean,
    onSelectTargetApp: (InstalledApp) -> Unit,
    onClearTargetApp: () -> Unit,
    onToggleSensor: () -> Unit,
    onRefreshState: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    var showAppSelector by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("传感器控制", fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = onRefreshState) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新状态")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Sensor Status Card ──────────────────────────────
            SensorStatusCard(
                sensorState = sensorState,
                onToggleSensor = onToggleSensor
            )

            // ── Current Foreground App ──────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "当前前台应用",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentForegroundApp ?: "未知",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ── Target App Section ──────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "目标应用",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (targetApp != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = targetApp.appName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = targetApp.packageName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onClearTargetApp) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "取消选择",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        Text(
                            "未选择目标应用",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showAppSelector = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (targetApp != null) "更换目标应用" else "选择目标应用")
                    }
                }
            }

            // ── Service Status ──────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Accessibility,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "前台监测服务",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            shape = CircleShape,
                            color = if (isServiceRunning)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            modifier = Modifier.size(10.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isServiceRunning) "正在监测前台应用切换" else "服务未启用，无法自动控制传感器",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isServiceRunning) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.SettingsAccessibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("启用无障碍服务")
                        }
                    }
                }
            }

            // ── Info Cards ──────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Info,
                    text = "目标应用前台时自动限制传感器",
                    color = MaterialTheme.colorScheme.primary
                )
                InfoChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Timer,
                    text = "退出后 5 秒自动恢复",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // App Selector Dialog
        if (showAppSelector) {
            AppSelector(
                currentTarget = targetApp?.packageName,
                onAppSelected = { app ->
                    onSelectTargetApp(app)
                    showAppSelector = false
                },
                onDismiss = { showAppSelector = false }
            )
        }
    }
}

@Composable
private fun SensorStatusCard(
    sensorState: SensorState,
    onToggleSensor: () -> Unit
) {
    val (bgColor, icon, title, subtitle, buttonText) = when (sensorState) {
        SensorState.NORMAL -> listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            Icons.Filled.Sensors,
            "传感器：正常",
            "所有应用均可访问传感器",
            "手动限制"
        )
        SensorState.RESTRICTED -> listOf(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            Icons.Filled.SensorsOff,
            "传感器：已限制",
            "仅目标应用和白名单应用可访问传感器",
            "恢复访问"
        )
        SensorState.UNKNOWN -> listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            Icons.Filled.HelpOutline,
            "传感器：状态未知",
            "正在获取传感器状态...",
            "刷新"
        )
        SensorState.ERROR -> listOf(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            Icons.Filled.ErrorOutline,
            "传感器：错误",
            "无法获取或设置传感器状态",
            "重试"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor as androidx.compose.ui.graphics.Color)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title as String,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle as String,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onToggleSensor,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (sensorState == SensorState.RESTRICTED) Icons.Filled.Sensors else Icons.Filled.SensorsOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = buttonText as String)
            }
        }
    }
}

@Composable
private fun InfoChip(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = color
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}
