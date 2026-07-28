# ExpressApp - Android 快递查询 App

## 构建
- 单模块 Android 项目，Gradle Kotlin DSL
- `./gradlew assembleDebug` — 构建 debug APK
- `./gradlew assembleRelease` — 构建 release APK（签名：`app/release.jks`，密码/别名均为 `123456`）
- `java.sourceCompatibility = VERSION_1_8`，`jvmTarget = "1.8"`
- compileSdk=34, minSdk=26, targetSdk=34

## 架构
- **无 DI / 无测试 / 无 CI**
- `MainActivity.kt` — 入口，Compose UI + 查询逻辑
- `ExpressTracker.kt` — 核心追踪引擎（百度 API 爬取 + 顺丰本地数据解析）
- `SFTrackingActivity.kt` — 顺丰专用 WebView Activity（加载 SmartApps 页面，从 iframe 提取轨迹）
- `HistoryStorage.kt` — SharedPreferences 持久化（最多 20 条）
- 数据模型在 `tracker/` 和 `history/`，UI 组件在 `ui/`

## 关键流程
1. 非顺丰单号：在隐藏 WebView 中加载百度搜索页 → JS 提取 `dataUrl` → OkHttp 调用快递 API → 解析 JSON
2. 顺丰单号：启动 `SFTrackingActivity` → WebView 加载 SmartApps 页面 → 2s 后读取 iframe 内容 → 解析文本格式轨迹
3. JD 开头单号会弹窗要求输入手机号后 4 位

## 注意事项
- `usesCleartextTraffic=true` + `network_security_config.xml` — 允许明文 HTTP
- 隐藏 WebView（`alpha=0f, translationX=-10000f`）仅用于 JS 执行，不渲染
- 调试包 applicationId 带 `.debug` 后缀
- SDK 路径：`/data/data/com.termux/files/home/android-sdk`（local.properties）
- 导入支持 `.json`（标准格式）和 `.txt`（顺丰本地数据）两种格式
- 导出使用 `FileProvider` + `Intent.ACTION_SEND`
- 无 `README`、无 `AGENTS.md` 历史、无 CI 配置文件
