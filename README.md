# 不许跳转 (NoShakeAD)

基于 Shizuku + 无障碍服务的 Android 传感器控制应用。自动限制"摇一摇"广告等利用传感器触发的恶意跳转，让你安心刷手机。

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

## 解决痛点

很多需要陀螺仪、加速度传感器功能的 App，本身离不开传感器——比如大学生跑步打卡用的「闪动校园」，它需要传感器来记录你的运动轨迹。但这些 App 偏偏又塞了摇一摇开屏广告，每次打开都得**狼狈地原地静止**，生怕稍微动一下就跳到广告页。

如果直接关掉传感器的系统权限，那这些 App 的正常功能就废了。关也不是，不关也不是，进退两难。

**「不许跳转」完美解决这个矛盾：** 只在开屏广告那几秒限制传感器，之后就自动恢复，App 该记步记步、该测速测速，毫不影响。从此打开闪动校园、Keep 这类 App，不必再担心摇一摇广告跳转，优雅开始运动。同样适用于淘宝、京东、抖音等一切带摇一摇广告的 App。

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

## 使用说明

### 这个应用是干什么的？

现在很多 App（淘宝、京东、抖音等）的开屏广告会利用手机的**运动传感器**（加速度计、陀螺仪）来检测你是否"摇了一下手机"。只要你稍微动一下手机，就会自动跳转到广告页面，非常烦人。

**"不许跳转"** 会在你打开目标 App 时，**临时关闭它的传感器权限 5 秒钟**。5 秒过后，传感器自动恢复，App 恢复正常使用。这个 "5 秒窗口" 正好覆盖了开屏广告的展示时间——广告播完了，你动手机也不会跳转了。

### 安装步骤

#### 第一步：安装 Shizuku（必需前置）

本应用依赖 [Shizuku](https://shizuku.rikka.app/) 来执行系统级命令。请先完成：

1. 从 [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) 或 [GitHub](https://github.com/RikkaApps/Shizuku/releases) 下载安装 Shizuku
2. 打开 Shizuku，根据你的手机选择启动方式：
   - **无线调试**（推荐）：需要 Android 11+，在"开发者选项"中开启"无线调试"，然后在 Shizuku 中点击"配对"→ 输入配对码 → 点击"启动"
   - **Root**：如果手机已 Root，直接在 Shizuku 中授权即可
3. 确认 Shizuku 主页显示 "Shizuku 正在运行"

#### 第二步：安装"不许跳转"

1. 下载并安装本应用的 APK
2. 打开应用，会弹出 **Shizuku 授权弹窗**，点击"允许"
3. 应用会提示你开启**无障碍服务**：
   - 点击提示跳转到系统设置
   - 找到"不许跳转"，打开开关
   - 返回应用

#### 第三步：选择要限制的应用

1. 在应用主界面，点击 **"选择目标应用"**
2. 从列表中找到你想限制的 App（如淘宝、京东、抖音）
3. 勾选后返回

### 日常使用

设置完成之后，你什么都不用管了。**后台杀掉也无所谓，5 秒后会自动恢复。**

每次你打开选中的 App 时：
1. 无障碍服务检测到你打开了目标 App
2. 自动限制该 App 的传感器访问
3. 5 秒后自动恢复传感器（大约就是开屏广告结束的时间）

如果想**手动恢复**传感器，拉下通知栏，点击"不许跳转"的通知，在应用中点击"恢复传感器"即可。

### 常见问题

**Q: 为什么是 5 秒？能不能调？**  
A: 大多数开屏广告持续 3-5 秒，5 秒是覆盖绝大部分场景的安全值。如果你觉得不够或太长，可以在应用设置中调整。

**Q: 会不会影响正常使用？**  
A: 不会。传感器只在目标 App 刚打开的前 5 秒被限制，之后自动恢复。正常使用时完全不受影响。

**Q: 为什么需要无障碍服务权限？**  
A: 无障碍服务用来实时检测"你正在打开哪个 App"。没有这个权限，应用就不知道你什么时候打开了目标 App，也就无法在正确的时间限制传感器。本应用**不会**读取你的输入内容或屏幕内容，仅检测前台应用包名。

**Q: 重启手机后需要重新设置吗？**  
A: 需要重新开启**无障碍服务**（Android 系统安全策略，重启后需手动重开）。Shizuku 也需要重新启动。应用内的设置（选择的目标应用等）会保留。

**Q: 小米手机后台被杀？**  
A: 请进入"设置 → 应用设置 → 应用管理 → 不许跳转"，开启"自启动"权限；在"省电策略"中选择"无限制"。

**Q: 传感器状态显示"未知"？**  
A: 确保 Shizuku 正在运行且已授权。也可点击应用右上角刷新按钮手动查询状态。

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

## 打赏

如果本程序对你有帮助，希望能得到你的打赏支持，感谢！

![打赏码](images/打赏码.jpg)
