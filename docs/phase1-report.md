# 第一阶段「工程骨架」验证报告

- 日期：2026-10-15
- 结论：**五项检查全部通过**，工程骨架可用，全链路（构建 → 安装 → 界面 → 数据 → 通知）验证畅通
- 验证环境：Windows + Git Bash，JDK 17.0.12，Android SDK（platform 37 / build-tools 36.0.0），模拟器 AVD `Medium_Phone_API_37.0`

## 一、工具链版本

| 组件 | 版本 | 说明 |
|---|---|---|
| Gradle | 9.8.1 | wrapper 已生成，指向官方标准地址 |
| AGP | 9.4.1 | 安卓官方构建插件 |
| Kotlin | 2.3.21 | 经 AGP 9 内置 Kotlin 机制编译 |
| KSP | 2.3.12 | Room 注解处理器 |
| Compose BOM | 2026.09.00 | 界面框架版本集 |
| material-icons-core | 1.7.8 | 图标库（维护模式，冻结版） |
| Room | 2.8.5 | 本地数据库 |
| minSdk / targetSdk / compileSdk | 35 / 37 / 37 | 真机 Android 15 为下限 |

## 二、检查项结果

| 检查项 | 方法 | 结果 |
|---|---|---|
| 构建通过 | `gradlew assembleDebug` | ✅ BUILD SUCCESSFUL，产出 `app-debug.apk`（约 31.7MB） |
| 安装与冷启动 | adb 安装并 `am start -W` | ✅ Status: ok，冷启动 3.0s（复测 3.7s） |
| 页面导航 | 逐页点击 + 截图 + UI 层级转储核对 | ✅ 五页（今日/记录/提醒/AI/设置）均正常渲染与切换 |
| 数据链路 | 设置页「数据库自检」按钮 | ✅ 回显「自检通过：当前饮食记录 1 条、运动记录 1 条」，Room 建表与读写正常，结构已导出至 `app/schemas/` |
| 通知 | 「发送测试通知」+ 权限弹窗授权 | ✅ 权限申请流程正常，通知出现在通知栏 |
| 稳定性 | logcat 全程监控 | ✅ 无 FATAL EXCEPTION / 崩溃 |

## 三、证据文件（docs/screenshots/phase1/）

- `01-today.png` ~ `05-settings.png`：五个页面截图
- `06-db-check.png`：数据库自检结果回显
- `07-notify-shade.png`：通知栏中的测试通知
- `phase1-demo.mp4`：完整操作演示录屏（冷启动 → 五页切换 → 数据库自检 → 发通知 → 通知栏）

## 四、过程中遇到并解决的问题

1. **AGP 9 不允许单独应用 kotlin-android 插件**：AGP 9.0 起内置 Kotlin 支持，已按报错指引移除该插件声明，Kotlin 编译选项由 `compileOptions` 统一对齐（Java/Kotlin 均 17）。
2. **当前 Material3 不再传递依赖图标库**：`Icons.Filled.*` 编译报找不到，显式添加 `material-icons-core:1.7.8` 解决；顺带将 `List` 图标换成 RTL 安全的 `AutoMirrored` 版本。
3. **官方 Gradle 下载被网络重置**：`services.gradle.org` 实际重定向到 GitHub Releases，国内连接被重置。改从腾讯镜像下载（SHA-256 与官方校验值一致），并预置进本机 `~/.gradle/wrapper/dists/` 缓存。**对仓库无影响**：wrapper 仍指向官方地址，本机 `gradlew` 无需再次下载。
4. **录屏与 UI 转储的路径转义问题**：Git Bash 会把 `/sdcard` 转成 Windows 路径传给 adb，加 `MSYS_NO_PATHCONV=1` 解决（首次录屏因此失败，已补录）。

## 五、遗留说明

- 测试通知渠道 `channel_general` 已注册（IMPORTANCE_HIGH），第三阶段会按功能拆分渠道。
- 模拟器为接近原生的 Android，HyperOS 真机上的通知/自启动管控差异留待第三阶段真机验证（届时提供一次性权限设置清单）。
- 构建产生的 `build-log.txt` 为临时日志，已清理；`build/`、`.gradle/` 等目录已被 .gitignore 排除。
