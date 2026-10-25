# 环境陷阱清单（交付验收前核对）

> 记录开发/交付过程中踩过的**非代码类**环境坑与规避方法。
> 这些问题不属于工程技术问题，但会直接污染验收体验；踩到新坑必须追加。
> 更新时间：2026-10-19

## 1. 模拟器 Quickboot 快照回滚（★ 最高危）

- **症状**：AS 里启动模拟器后看到旧版应用/数据凭空消失/新装版本不见。
- **原因**：模拟器退出时会自动保存快照；下次 Quick Boot 加载快照会把整个虚拟机（含磁盘）回滚到快照时刻。曾两次造成影响（测试数据消失、用户看不到运动功能）。
- **规避**：
	- 开发期无界面启动一律 `-no-snapshot`；
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

## 9. dumpsys notification 的 grep 中文匹配不可靠

- **症状**：`dumpsys notification --noredact | grep 测试待办` 计数为 0，但通知实际已在通知栏。
- **规避**：验证通知是否到达，用 `cmd statusbar expand-notifications` + uiautomator dump（或截图），不要信 dumpsys 的中文 grep。

## 10. 模拟器测试定时提醒会被 Doze/App Standby 延迟

- **症状**：应用退后台数分钟后，排好的闹钟到点不触发（广播被系统延迟到解冻）。
- **规避**：测试定时提醒前执行 `adb shell dumpsys deviceidle disable` + `adb shell am set-standby-bucket com.dailycontrol.app active`。真机上对应解法就是权限清单里的「电池无限制」。

## 11. 精确闹钟权限默认未授予（Android 14+ 模拟器与真机 alike）

- **症状**：dumpsys alarm 显示 window=+1h（退化为窗口闹钟，触发可漂移）。
- **规避**：应用内设置页有「去授权」入口；模拟器上可直接 `adb shell appops set com.dailycontrol.app SCHEDULE_EXACT_ALARM allow`，授权后 window=0 准点。

## 12. Android 11+ 包可见性：不声明 queries 查不到别的应用

- **症状**：`getLaunchIntentForPackage("com.android.chrome")` 应用内返回 null，但 adb shell 的 resolve-activity 能解析（shell 不受可见性限制）——背单词跳转按钮曾因此不出现。
- **规避**：Manifest 声明 `<queries>`（MAIN/LAUNCHER intent-filter 即可覆盖全部可启动应用）。诊断此类问题时用 `dumpsys notification` 看 actions 字段（构建侧真相），别只信 UI 转储。

## 13. adb 广播打不到未导出的 Receiver

- **症状**：`am broadcast -n 包名/.Receiver --ei ... 3001` 返回 result=0（看似成功）但 onReceive 根本不执行——外部（含 shell）显式广播到 `exported=false` 的组件会被静默拦截。
- **规避**：不要用 am broadcast 测内部 Receiver；用真实路径——调试按钮排一个 30 秒/3 分钟后的真实闹钟（PendingIntent 触发），或等真实定时。

## 14. uiautomator dump 的陈旧缓存与滚动时机

- **症状**：dump 读到的还是几轮前的页面（甚至桌面）内容，误导排查方向；小步滚动后立刻 dump 常拿不到更新后的布局。
- **规避**：每次 dump 前 `rm /sdcard/ui.xml` 再 dump；滚动后等布局稳定（1.5s+）再 dump；大步滚动（1000px+）比多次小滚动可靠。
- **补充（2026-10-23）**：除了「整页陈旧」，还存在「节点文本陈旧」——状态文本明明已渲染（截图可见），dump 里该节点却仍是旧内容或缺失（曾把 401 错误提示误判为未渲染）。断言「某段动态文本是否出现」时，dump 两次不中就改用 `exec-out screencap -p` 截图肉眼/视觉模型核验，别反复 dump。

## 15. 模拟器 deskclock 不支持 SET_ALARM 的 SKIP_UI 静默创建

- **症状**：应用与 adb 注入 SET_ALARM（SKIP_UI=true）都返回成功，但闹钟列表不出现新闹钟——模拟器内置时钟应用（旧版 deskclock）不完整支持该协议。
- **规避**：模拟器上只验证 intent 构造正确；真实写入效果留真机（HyperOS 时钟）验证，失败时有 Log.w 日志可查。

## 16. HyperOS 通知需按渠道逐个开启三件套（真机实测 2026-10-17）

- **症状**：通知权限已授予、通知能发出且出现在通知栏，但锁屏不显示、无悬浮横幅。
- **原因**：HyperOS 的 设置→通知与状态栏→通知管理→应用详情页**最底部按通知渠道（三餐询问/背单词/规划/待办）逐个列出**，每个渠道都要手动开「允许通知 + 悬浮通知 + 锁屏显示通知」三个开关。
- **规避**：真机权限清单必须包含这一步；应用层方案见 goal.md TODO（首次启动引导）。

## 17. force-stop 清空全部闹钟；升级安装（install -r）残留旧闹钟（模拟器实测 2026-10-17）

- **症状**：`am force-stop` 后 `dumpsys alarm` 里本应用闹钟全部消失；而 `install -r` 覆盖安装后旧版本排的闹钟仍在（requestCode 还是旧代码段的）。
- **影响**：force-stop 后提醒全哑，须等应用下次启动自愈或开机广播；升级安装后旧码闹钟会一直挂着（若新版本改过 requestCode 分段，这些旧闹钟会落进无效分支静默吞掉）。
- **规避**：测试期用「重启应用进程（正常退出再进）」而非 force-stop 观察自愈；改过 requestCode 分段后，真机升级安装应打开应用一次让自愈重排新码段，或重存一次规划。

## 18. Kotlin when 区间分支会吞掉后面更窄的区间（代码层，2026-10-17 起床通知全哑根因）

- **症状**：起床唤醒闹钟准点触发（接收器日志正常），但通知从未发出、无任何报错——真机上用户也因此从未真正感受过起床渠道的震动。
- **原因**：接收器 when 分发里「待办」分支是宽区间 4100..104099，排在前面；旧起床码段 5000-5009 整段落入该区间，起床分支成死代码，按不存在的任务 id 查询后静默跳过。
- **规避**：多个 `in 范围 ->` 分支必须按「窄在前、宽兜底在后」排序；requestCode 分段设计时就保证互不相交（现方案：待办 4100+自增 id，起床 200000+，见 ReminderReason 注释）。

## 19. `adb shell cat` 重定向保存二进制会被 CRLF 翻译损坏（2026-10-18 门面图未渲染根因）

- **症状**：`adb shell cat /sdcard/xx.png > 本地.png` 保存的截图在 Markdown 里不渲染；PNG 头部变成 `...0d 0d0a 1a`（正常应为 `0d 0a 1a`）。
- **原因**：旧式 adb shell 的伪终端把输出里的每个 `\n` 翻译成 `\r\n`，二进制数据被整体污染；MSYS 的 `adb pull` 因路径转换问题被误当成替代方案时同样踩坑（pull 必须配 `MSYS_NO_PATHCONV=1` 与 Windows 风格本地路径）。
- **规避**：取二进制一律用 `MSYS_NO_PATHCONV=1 adb pull 设备路径 C:/.../本地.png`。已损坏文件可无损逆转：`\r\r\n`→`\r\n`、`\r\n`→`\n`（本项目四张 phase4 截图已按此修复并过 PNG 块结构校验）。

## 20. SAF 文件选择器「最近」视图不显示 MediaStore 新建文件；真机与模拟器默认根目录不同（2026-10-19 真机）

- **症状**：应用经 MediaStore 写入下载目录的备份，用户随后打开系统文件选择器（SAF）却「看不到」——模拟器上选择器默认就开在 Download 根目录一屏可见，真机（HyperOS）默认停在「最近」视图，新建文件不在其中，须手动导航 内部存储→Download。
- **规避**：涉及「选自己应用产生的文件」的场景，优先做应用内清单（如 v0.3.2 的恢复备份列表：MediaStore 按命名前缀检索自家文件，无需权限），SAF 仅作「从其他位置选择」的兜底；不要依赖选择器的默认位置。

## 21. 无界面模拟器会被外部 Actor 回退数据分区（2026-10-19 页面整合批次实测 ★）

- **症状**：走查进行中，应用界面此前明明显示已保存的记录/待办，几分钟后数据库整体回退到旧状态（文件 mtime 停留在更早时刻）；期间模拟器 uptime 连续、应用进程 PID 稳定、无崩溃——数据却从活着的进程里"蒸发"。另伴随设备时钟 NTP 回跳（04:26→04:18）与莫名回到桌面等怪象。
- **原因**：同一 AVD 的磁盘镜像被另一个实例/工具（如用户侧 Android Studio、强杀后重启的模拟器进程、`-wipe-data` 参数重复生效）介入，QEMU 惰性刷盘与外部写入互相覆盖。
- **规避**：
	- 同一 AVD 同时只跑一个实例；会话中断后恢复工作时，先用 `adb shell uptime` + 数据库文件 mtime 交叉确认环境连续性，异常即弃用重开（`-no-snapshot -wipe-data` 一步到位）；
	- 关键行为验证用「短连招」一次跑完（创建→断言→销毁全链路数分钟内），减小被外部干扰的窗口；
	- 测试定时提醒前顺手 `settings put global auto_time 0` 关掉 NTP，杜绝时钟回跳污染时间算术。

## 22. uiautomator dump 抓不到 Compose 的 FAB 与底部弹层语义（2026-10-19）

- **症状**：`uiautomator dump` 的 XML 里找不到悬浮按钮「记一笔」的文本、ModalBottomSheet 内的卡片文字，但页面上明明渲染着；按 dump 定位点击会点空。
- **规避**：Compose 部分组件（FAB、弹层内容）不进无障碍树，验证与定位以截图为准；dump 适合拿普通文本节点与 Checkbox（checkable/checked 属性可靠）。另注意 `dumpsys alarm | grep -c` 会把每条闹钟后的摘要行（`type=RTC_WAKEUP tag=...`）一并计入，计数会虚高 1，精确断言用 origWhen 值过滤。

## 23. API 37 模拟器：应用访问 10.0.2.2 卡死在 SYN-SENT（2026-10-23 AI 接口联调 ★）

- **症状**：应用内 HTTP 请求 `http://10.0.2.2:18080/...` 永远超时；`adb shell ss -tn` 显示该连接停留在 `SYN-SENT`（目标是 `[::ffff:10.0.2.2]`——IPv4 映射的 IPv6 套接字）。而同一地址用 shell 里的 `nc`（纯 IPv4 套接字）一打就通，极易误导成「网络正常、代码有 bug」。
- **原因**：Android 应用的所有 socket 都是 v4-mapped IPv6；这台模拟器的 QEMU 用户态网络对「映射 v6 套接字 → 10.0.2.2 宿主别名」不做 NAT（对普通公网地址正常），SYN 出去就石沉大海。
- **规避**：改用 **adb 端口反向转发**——`adb reverse tcp:18080 tcp:18080`，应用内地址填 `http://127.0.0.1:18080/v1`（经 adb 隧道直达宿主机，稳定可靠）；`network_security_config.xml` 的明文放行清单需同时含 `10.0.2.2` 与 `127.0.0.1`。另：跑本地 mock 服务用 `python -u` 无缓冲启动，否则日志文件迟迟不落盘，会误判「请求没到」。

## 24. HyperOS 真机：普通 USB 调试禁止 adb 注入输入（2026-10-24 真机验收实测）

- **症状**：真机（HyperOS 2 / Android 15）已授权 USB 调试，`adb shell input tap/swipe/keyevent` 全部抛 `SecurityException: Injecting input events requires ... INJECT_EVENTS`；锁屏状态下同样被拒。读操作（screencap / uiautomator dump / install / logcat）不受影响。
- **原因**：MIUI/HyperOS 把输入注入划入「USB 调试（安全设置）」的额外授权，需登录小米账号并在开发者选项单独开启；普通 USB 调试只放行只读与安装类操作。
- **规避**：真机验收/走查不必去开「安全设置」——改用**分工模式**：用户手动操作手机，adb 只做只读核验（截图 + uiautomator dump 断言界面文本 + logcat 验证接收器触发）。需要全自动 UI 驱动的场景留给无界面模拟器（不受此限制）。

## 25. cmd 批处理含中文 UTF-8 会被 GBK 双字节配对撕碎（2026-10-24 自检脚本实测）

- **症状**：UTF-8 编码的 `.bat` 在 cmd 里执行，中文行附近的命令被撕成乱码命令（如 `bash.exe` 变成 `'ash.exe' 不是内部或外部命令`）。
- **原因**：cmd 用系统 ANSI 码页（GBK）逐行解析批处理，UTF-8 中文字节的高位字节两两配对时会吞掉行尾换行符或下一行行首字符；且 `chcp 65001` 在含非 ASCII 内容的批处理中还会引发文件偏移错乱（cmd 按字节记位置，码页切换后重新对位失败）。
- **规避**：`.bat` 一律**纯 ASCII**（含 rem 注释）；中文输出交给 bash/python 子进程打印，入口处 `chcp 65001` 只为让子进程的 UTF-8 输出正常显示——纯 ASCII 文件下 chcp 是安全的。参考 `script/check-env.bat`。

## 26. MSYS 的 grep 在部分挂载点会先剥掉 \r 再匹配（2026-10-24 回归测试实测）

- **症状**：`grep -q $'\r' 文件` 在仓库盘路径（/e/...）能命中 CRLF 文件，在 /tmp 下永远不命中；同一文件用 `tr -dc '\r' | wc -c` 却能数出 CR 字节。
- **原因**：MSYS 挂载点分文本/二进制模式，文本模式挂载下（如 /tmp）grep 读文件先做 CRLF→LF 转换；cygdrive 挂载默认二进制模式不做转换。
- **规避**：检测文件是否含 CR 不要用 grep，用 `tr -dc '\r' < 文件 | wc -c`（走 shell 重定向，不受挂载模式影响）。参考 `script/check-env.sh` 的 `gradlew_is_crlf`。

## 27. wsl.exe 的参数重组会吞内层引号、错层级展开 $?（2026-10-24 退出码排查实测）

- **症状**：`wsl bash -c 'bash -c "exit 1"; echo $?'` 之类的嵌套调用拿到错误退出码（明明内层退出 1 却显示 0）；含 `$?` 的 -c 字符串在错误的层级被展开；MSYS 还会把以 / 开头的参数改写成 `C:/Program Files/Git/...`（同第 2 条路径改写家族）。
- **原因**：`wsl bash -c "..."` 的参数经「Git Bash argv → Windows 命令行 → wsl.exe → Linux」多轮拆装，内层双引号可能丢失、`$?` 可能在中间层提前展开。
- **规避**：验证 WSL 内命令的真实退出码，不要用 `-c` 内嵌 `$?`——写个临时脚本文件（LF 行尾）在里面把 `rc=$?` 落地到结果文件，外面 `MSYS_NO_PATHCONV=1 wsl bash /mnt/c/.../脚本.sh` 执行后读文件。

