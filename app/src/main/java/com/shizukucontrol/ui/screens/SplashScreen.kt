package com.shizukucontrol.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shizukucontrol.data.model.ShizukuStatus

@Composable
fun SplashScreen(
    shizukuStatus: ShizukuStatus,
    onCheckStatus: () -> Unit,
    onRequestPermission: () -> Unit,
    onNavigateToMain: () -> Unit
) {
    LaunchedEffect(shizukuStatus) {
        if (shizukuStatus == ShizukuStatus.READY) {
            onNavigateToMain()
        }
    }

    LaunchedEffect(Unit) {
        onCheckStatus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // App icon
            Icon(
                imageVector = Icons.Filled.SensorsOff,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "传感器控制",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "基于 Shizuku 的传感器权限管理",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            when (shizukuStatus) {
                ShizukuStatus.LOADING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在检查 Shizuku 服务...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ShizukuStatus.NOT_RUNNING -> {
                    StatusCard(
                        icon = Icons.Filled.CloudOff,
                        title = "Shizuku 未运行",
                        description = "请先安装并启动 Shizuku 应用\n\n1. 下载安装 Shizuku\n2. 通过无线调试或 Root 启动\n3. 返回本应用继续",
                        iconTint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onCheckStatus) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("重新检测")
                    }
                }

                ShizukuStatus.NEED_PERMISSION -> {
                    StatusCard(
                        icon = Icons.Filled.Shield,
                        title = "需要授权",
                        description = "本应用需要 Shizuku 权限来执行传感器控制命令",
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onRequestPermission) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("授权 Shizuku 权限")
                    }
                }

                ShizukuStatus.READY -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = "授权成功，进入主页...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ShizukuStatus.ERROR -> {
                    StatusCard(
                        icon = Icons.Filled.ErrorOutline,
                        title = "服务异常",
                        description = "Shizuku 连接出现错误，请重试",
                        iconTint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onCheckStatus) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    iconTint: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = iconTint
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}
