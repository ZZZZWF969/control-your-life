# 环境陷阱清单（交付验收前核对）

> 记录开发/交付过程中踩过的**非代码类**环境坑与规避方法。
> 这些问题不属于工程技术问题，但会直接污染验收体验；踩到新坑必须追加。
> 更新时间：2026-10-16

## 1. 模拟器 Quickboot 快照回滚（★ 最高危）

- **症状**：AS 里启动模拟器后看到旧版应用/数据凭空消失/新装版本不见。
- **原因**：模拟器退出时会自动保存快照；下次 Quick Boot 加载快照会把整个虚拟机（含磁盘）回滚到快照时刻。曾两次造成影响（测试数据消失、用户看不到运动功能）。
- **规避**：
	- 我方无界面启动一律 `-no-snapshot`；
	- 交付前清理 `~/.android/avd/Medium_Phone.avd/snapshots/default_boot/`（注意：该模拟器优雅退出后仍会再生快照文件，需要每次清理）；
	- 用户侧建议在 AS Device Manager 将设备启动选项设为 **Cold Boot**（一劳永逸，尚未设置）；
	- 最可靠的验收方式永远是 AS 里 Run ▶（当场构建安装）。

## 2. adb 不认识 Git Bash 的 MSYS 路径

- **症状**：`adb install /e/desktop/...` 报 "No such file or directory"；`adb shell cat /sdcard/...` 变成读 `C:/Program Files/Git/sdcard/...`。
- **规避**：传给 adb 的本地路径用 Windows 风格（`E:/desktop/...`）；设备侧路径前加 `export MSYS_NO_PATHCONV=1`。

## 3. Windows 输入法无法向模拟器输入中文

- **症状**：物理键盘打中文，模拟器输入框无反应。
- **原因**：模拟器直通物理键盘，Windows IME 的候选/上屏过程不传入。
- **规避**：用户侧在模拟器内用软键盘（需为 GBoard 添加中文语言）；或 AVD 关闭「启用键盘输入」强制弹软键盘（未做，等用户决定）。

## 4. Gradle 官方下载源在国内被重置

- **症状**：`services.gradle.org` 实际重定向 GitHub Releases，连接被 reset，构建无限挂起。
- **规避**：用腾讯镜像下载（校验 SHA-256），并预置到 `~/.gradle/wrapper/dists/` 对应哈希目录；工程内 wrapper 保持官方地址不变。

## 5. gradle-daemon-jvm.properties 要求不存在的 JDK

- **症状**：守护进程启动挂起（实为联网下载 JDK），构建卡在 "Starting a Gradle Daemon"。
- **规避**：该文件 `toolchainVersion` 必须与本机 JDK（17）一致，已修正入库。

## 6. 后台任务通知的"狼来了"

- **症状**：模拟器启动任务报 failed/completed，但设备实际正常运行。
- **规避**：以 `adb devices` + `getprop sys.boot_completed` 实证为准，不信任外壳通知。

## 7. git merge 不支持标准输入传信息

- **症状**：`git merge -F -` 报 "could not read file '-'"。
- **规避**：提交信息写入临时文件再 `-F <file>`（commit 支持 stdin，merge 不支持）。

## 8. 首次构建需联网下载依赖

- 约几百 MB（Gradle 发行版 + AGP + AndroidX）。当前全部已入本机缓存；若换机器/清缓存需重新下载，届时按第 4 条走镜像。
