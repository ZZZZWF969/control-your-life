# 构建与安装详解

> 三种方式任选其一：**方式一** Android Studio（推荐给想看代码的人）· **方式二** 命令行 Gradle（熟悉终端的人）· **方式三** 直接安装 APK（只想用的人）。
> 每一步都写明：要输入什么、看到什么算成功、可能遇到什么问题以及怎么解决。
> 装好后的上手指南见 [getting-started.md](getting-started.md)。

## 0. 环境要求（三种方式通用）

| 组件 | 要求 | 说明 |
|---|---|---|
| 操作系统 | Windows / macOS / Linux 均可 | 本文命令以 Windows（Git Bash）为主，其他系统同理 |
| JDK | 17 | 构建工具链要求；Android Studio 可自带，命令行方式需单独安装（`java -version` 应显示 17.x） |
| Android SDK | Platform 37 + Build-Tools 36+ | Android Studio 的 SDK Manager 可装；命令行方式也可用 `sdkmanager` 装 |
| 设备系统 | **Android 15（API 35）及以上** | 本应用 minSdk=35，更低版本无法安装（见方式二的问题 6） |
| 磁盘 | 首次构建需约 2~4 GB 缓存 | Gradle 发行版 + 依赖包，之后增量构建很快 |

> **一键自检**：双击仓库里的 `script/check-env.bat`（或在 Git Bash 执行 `bash script/check-env.sh`），一次检查上表各项——已装的报版本号，缺的/版本不符的给出「需要 vs 现有」对比、安装命令与本文对应章节指引。脚本只读，不改动任何文件与设置；结论仅供参考，以实际构建为准。

---

## 方式一：Android Studio 打开运行（推荐）

### 第 1 步：安装 Android Studio

从[官网](https://developer.android.com/studio)下载安装（国内网络若慢，可自行选择可信的镜像站下载安装包，注意校验文件哈希）。安装时按默认选项即可，首次启动向导会自动装好所需 SDK 组件。

### 第 2 步：打开工程

菜单 File → Open… → 选择**仓库根目录**（含 `settings.gradle.kts` 的那层，不是 `app/` 子目录）→ OK。

### 第 3 步：等待 Gradle Sync

打开后右下角会出现 Gradle 同步进度条。

- **看到什么算成功**：进度条结束，无红色报错；左侧工程树可展开 `app/src/main/java/...`。
- **首次同步要下载几百 MB**（Gradle 发行版 + AndroidX 依赖），耐心等。
- **可能的问题**：
	- **长时间卡在 "Downloading gradle-9.x"**：国内网络对 Gradle 官方源（实际重定向 GitHub Releases）连接重置。解决：手动从镜像（如腾讯 `https://mirrors.cloud.tencent.com/gradle/`）下载对应版本 zip，放入 `~/.gradle/wrapper/dists/gradle-9.x-<hash>/<hash>/` 目录（哈希目录名保持向导生成的结构），重新 Sync。（开发环境的详细踩坑记录：[env-pitfalls.md](env-pitfalls.md) #4）
	- **Sync 报 "Failed to find JDK 17" / 卡在 Starting Gradle Daemon**：File → Settings → Build Tools → Gradle 检查 Gradle JDK 指向 17；仓库内 `gradle-daemon-jvm.properties` 的 toolchainVersion 必须与本机 JDK 一致（当前为 17）。
	- **报 "Failed to install platform android-37"**：Settings → Appearance & Behavior → System Settings → Android SDK → SDK Platforms 勾选 Android API 37 安装。

### 第 4 步：手机开启开发者选项与 USB 调试

1. 设置 → 我的设备 → 全部参数与信息 → 连续点「OS 版本」7 次 → 提示「您已处于开发者模式」
2. 设置 → 更多设置 → 开发者选项 → 打开「USB 调试」
3. 数据线连电脑（传文件模式）→ 手机弹「允许 USB 调试吗？」→ 勾选「一律允许使用这台计算机」→ 允许

### 第 5 步：运行

顶部设备选择器选中你的手机 → 点绿色 **Run ▶**。

- **看到什么算成功**：构建完成后手机自动安装并打开应用，停在「今日」页。
- **可能的问题**：
	- **设备列表为空 / 显示 unauthorized**：检查第 4 步的授权弹窗是否点了允许；换一根数据线（有些线只能充电）。
	- **HyperOS 提示「USB 安装」被拒**：开发者选项里另有「USB 安装」开关，打开并按手机提示确认。
	- **报 INSTALL_FAILED_OLDER_SDK**：手机系统低于 Android 15，本应用装不上（见环境要求）。
	- **报 INSTALL_FAILED_UPDATE_INCOMPATIBLE**：手机上已装有不同签名的版本。**先在旧版里导出备份**（设置 → 备份与恢复 → 导出全部数据），卸载旧版后重新 Run，再从备份恢复。

---

## 方式二：命令行构建 + adb 安装

### 第 1 步：构建

在仓库根目录执行：

```bash
./gradlew assembleDebug        # macOS/Linux；Windows CMD 用 gradlew.bat
```

- **看到什么算成功**：末尾输出 `BUILD SUCCESSFUL`，产物在 `app/build/outputs/apk/debug/app-debug.apk`（约 35 MB）。
- **可能的问题**：
	- 下载类问题同方式一第 3 步（镜像 / JDK）。
	- **报 "SDK location not found"**：根目录没有 `local.properties`。新建一行写入 `sdk.dir=<你的 SDK 路径>`（Windows 示例：`sdk.dir=E\:\\Android\\Sdk`，注意转义；或用 Android Studio 打开一次工程自动生成）。

### 第 2 步：安装到手机

手机按方式一第 4 步开好 USB 调试并连接，然后：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- **看到什么算成功**：输出 `Success`。
- **可能的问题**：
	- **`adb: command not found`**：adb 不在 PATH。用全路径执行，Windows 默认在 `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`。
	- **Git Bash 下报 "No such file or directory" 但文件明明存在**：Git Bash 会把 `/e/...` 风格路径改写。两种解法：路径写 Windows 风格 `E:/desktop/.../app-debug.apk`，或先 `export MSYS_NO_PATHCONV=1`。
	- **`more than one device/emulator`**：电脑连了多台设备。用 `adb devices` 列出序列号，然后 `adb -s <序列号> install -r <apk路径>`。
	- **签名冲突 / 低于 Android 15**：同方式一第 5 步。

### 第 3 步：启动

```bash
adb shell am start -n com.dailycontrol.app/.MainActivity
```

应用打开停在「今日」页即成功。

---

## 方式三：直接安装 APK（普通用户）

当前阶段：APK 暂未挂网，可从源码按方式一/二自行构建，或向项目所有者获取 `app-debug.apk`。**未来正式版会挂在 GitHub Releases 页**，下载后直接安装。

手机安装步骤：

1. 把 `app-debug.apk` 传到手机（网盘 / 数据线 / 聊天工具均可）
2. 文件管理器里点开它 → 系统提示「不允许安装未知应用」→ 按提示给你使用的文件管理器开启「安装未知应用」权限 → 返回重试
3. 装完打开即停在「今日」页

- **可能的问题**：
	- **提示「解析包出现问题」**：文件传输过程损坏（部分聊天工具会压缩文件），换网盘或数据线重传。
	- **升级旧版本时提示签名不一致**：说明旧版与新版签名不同（例如从 debug 版换正式签名版）。**先在旧版里导出备份**，卸载旧版 → 装新版 → 从备份恢复全部数据。

---

## 升级与数据说明

- **同签名覆盖安装（`adb install -r` 或 AS 直接 Run）**：数据与设置全部保留，数据库自动迁移。
- **卸载重装**：应用内数据会清空（安卓沙箱规则），但**下载目录里的每周自动备份不会被删**——重装后在设置 → 备份与恢复 → 从备份恢复即可找回。数据安全策略详见 [data-storage.md](data-storage.md)。
- 装好后第一次怎么用？看 [getting-started.md](getting-started.md)。
