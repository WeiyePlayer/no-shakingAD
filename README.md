# NoShakeAD (传感器控制)

基于 Shizuku + AccessibilityService 的 Android 传感器权限定时控制应用。自动限制"摇一摇"广告等利用传感器触发的恶意跳转。

## 核心原理

通过 `dumpsys sensorservice` 命令控制系统传感器服务，进入受限模式后所有应用（白名单除外）无法访问加速度计、陀螺仪等运动传感器。

```
┌──────────────────┐     ┌────────────────────┐     ┌─────────────────────┐
│ AccessibilitySvc │ ──▶ │   MainViewModel    │ ──▶ │ Shizuku UserService │
│ 实时检测前台切换  │     │ 判断是否为目标应用  │     │ dumpsys 命令执行    │
└──────────────────┘     └────────────────────┘     └─────────────────────┘
                                                           │
                                                           ▼
                                                  ┌──────────────────┐
                                                  │ SensorService    │
                                                  │ RESTRICTED 模式  │
                                                  └──────────────────┘
```

## 功能

- **目标应用检测** — 通过 AccessibilityService 实时检测前台应用切换
- **自动限制传感器** — 切换到目标应用时立即执行 `dumpsys sensorservice restrict`
- **5 秒自动恢复** — 进入目标应用 5 秒后自动执行 `dumpsys sensorservice enable`（在 UserService 进程中执行，不依赖 UI 进程存活）
- **白名单** — 始终保留 `com.android.systemui` 的传感器访问权限
- **手动控制** — 传感器状态卡片支持手动限制/恢复切换

## 技术栈

| 组件 | 选型 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 权限桥接 | Shizuku API 13 (UserService) |
| 前台检测 | AccessibilityService |
| 数据持久化 | DataStore Preferences |
| 最低 SDK | 26 (Android 8.0) |

## 权限要求

1. **Shizuku API** — 用于执行 `dumpsys sensorservice` 等系统级 shell 命令
2. **无障碍服务** — 用于实时检测前台应用切换（需在设置中手动开启一次）

## 使用方式

1. 安装 [Shizuku](https://shizuku.rikka.app/) 并通过无线调试或 Root 启动
2. 安装本应用，打开后在 Shizuku 授权弹窗中确认
3. 进入设置启用无障碍服务（仅首次需要）
4. 选择目标应用
5. 切换到目标应用时传感器将自动受限

> 💡 建议在 MIUI 系统中给予本应用"自启动"和"无限制电池"权限以确保无障碍服务稳定运行。

## 项目结构

```
app/src/main/java/com/shizukucontrol/
├── MainActivity.kt                # 入口 Activity
├── ShizukuControlApp.kt           # Application
├── service/
│   ├── AppDetectionService.kt     # AccessibilityService 前台检测
│   └── SensorControlUserService.kt # Shizuku UserService 命令执行
├── viewmodel/
│   └── MainViewModel.kt           # 核心逻辑
├── data/
│   ├── PreferencesManager.kt      # DataStore 持久化
│   └── model/TargetApp.kt         # 数据模型
├── util/
│   └── ShizukuHelper.kt           # Shizuku API 封装
└── ui/
    ├── theme/Theme.kt             # Material 3 主题
    ├── screens/
    │   ├── SplashScreen.kt        # 开屏/权限检测
    │   └── MainScreen.kt          # 主界面
    └── components/
        └── AppSelector.kt         # 应用选择器
```

## 构建

```bash
./gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

## 常见问题

**Q: 重启后无障碍服务被关闭？**  
A: Android 系统安全策略，重启后需重新开启无障碍服务。Shizuku 授权不受影响。

**Q: 传感器状态显示未知？**  
A: Shizuku UserService 绑定后会自动查询。可点击右上角刷新按钮手动查询。

**Q: 小米手机后台被杀？**  
A: 请开启"自启动"权限，并在电池策略中选择"无限制"。
