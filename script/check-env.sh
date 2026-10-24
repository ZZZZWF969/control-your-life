#!/usr/bin/env bash
# =============================================================================
# 每日掌控 · 构建环境自检脚本（只读：不创建、不修改、不删除任何文件与设置）
#
# 用法（Git Bash 中执行，在仓库任意目录都可以）：
#   bash script/check-env.sh
#
# 「需要」的版本不是拍脑袋，依据工程构建配置：
#   - app/build.gradle.kts                → compileSdk 37、Java 17
#   - gradle/gradle-daemon-jvm.properties → toolchainVersion 17（构建 JDK 必须 17 整）
#   - docs/build-and-install.md           → Build-Tools 36+、首次构建约 2~4 GB 缓存
#
# 退出码：0 = 全部通过；1 = 存在缺失/不符/无法判定（网络预警不计入）
# =============================================================================

# ---- 工程要求的版本常量（工程升级时改这里即可） ----
NEED_JDK_MAJOR=17          # JDK 主版本，必须恰为该值（更高会让 Gradle 尝试联网另下 17）
NEED_PLATFORM=android-37   # compileSdk 对应的 SDK Platform
NEED_BUILD_TOOLS_MAJOR=36  # Build-Tools 最低主版本
NEED_DISK_GB=4             # 首次构建 Gradle 与依赖缓存所需磁盘（GB）

# 本脚本所在仓库的根目录（script/ 的上一级，与本文件位置绑定）
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ---- 报告层：统一的三态输出与计数（通过 / 缺失 / 不符，另加预警与未知） ----
PASS=0
BAD=0
WARN=0

ok()      { printf '[ 通过 ] %s\n' "$1"; PASS=$((PASS + 1)); }
miss()    { printf '[ 缺失 ] %s\n' "$1"; BAD=$((BAD + 1)); }
fail()    { printf '[ 不符 ] %s\n' "$1"; BAD=$((BAD + 1)); }
unknown() { printf '[ 未知 ] %s\n' "$1"; BAD=$((BAD + 1)); }
warn()    { printf '[ 预警 ] %s\n' "$1"; WARN=$((WARN + 1)); }
info()    { printf '         %s\n' "$1"; }   # 条目下的补充行：建议命令、文档指引

# ---- 检测层：只收集事实与判定、不做任何输出（便于测试时单独调用灌样本） ----

# Windows 风格路径转 MSYS 风格；无 cygpath（非 Git Bash 环境）时原样返回
to_msys_path() {
	if command -v cygpath >/dev/null 2>&1; then
		cygpath -u "$1" 2>/dev/null || echo "$1"
	else
		echo "$1"
	fi
}

# 从 java -version 的原始输出中提取版本号（如 17.0.2 / 1.8.0_392）
version_string_of() {
	printf '%s\n' "$1" | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -1
}

# 版本号 → 主版本数字（老式 1.8.0_xxx 的主版本号在第二段）
java_major_of() {
	if [[ "$1" == 1.* ]]; then
		printf '%s\n' "$1" | cut -d. -f2
	else
		printf '%s\n' "$1" | cut -d. -f1
	fi
}

# 定位 java：优先 JAVA_HOME，其次 PATH；结果存全局 JAVA_EXE / JAVA_FROM
detect_java() {
	JAVA_EXE=""
	JAVA_FROM=""
	if [[ -n "${JAVA_HOME:-}" ]]; then
		local jc
		jc="$(to_msys_path "$JAVA_HOME")/bin/java"
		if [[ -x "$jc" ]]; then
			JAVA_EXE="$jc"
			JAVA_FROM="JAVA_HOME"
		fi
	fi
	if [[ -z "$JAVA_EXE" ]] && command -v java >/dev/null 2>&1; then
		JAVA_EXE="$(command -v java)"
		JAVA_FROM="PATH"
	fi
}

# 定位 Android SDK：环境变量 → local.properties → Windows 默认路径；存 SDK_PATH / SDK_FROM
# 注意：候选路径逐个尝试、取第一个有效者，不能用 elif 链——前一支线存在但校验失败时，后面的兜底仍要试
detect_sdk() {
	SDK_PATH=""
	SDK_FROM=""
	local cand="" from=""
	if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME:-}" ]]; then
		cand="$ANDROID_HOME"
		from="环境变量 ANDROID_HOME"
	elif [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT:-}" ]]; then
		cand="$ANDROID_SDK_ROOT"
		from="环境变量 ANDROID_SDK_ROOT"
	fi
	if [[ -z "$cand" && -f "$REPO/local.properties" ]]; then
		# properties 转义还原：\: 是冒号、\\ 是路径分隔符（还原成 / 免去反斜杠歧义）；
		# 行尾兼容 CRLF 与 LF 两种写法
		local raw
		raw="$(sed -n 's/^sdk\.dir=\(.*\)$/\1/p' "$REPO/local.properties" | head -1)"
		raw="$(printf '%s' "$raw" | sed -e 's/\\:/:/g' -e 's/\\\\/\//g')"
		raw="${raw%%$'\r'}"
		if [[ -n "$raw" && -d "$raw" ]]; then
			cand="$raw"
			from="local.properties"
		fi
	fi
	if [[ -z "$cand" && -n "${LOCALAPPDATA:-}" ]]; then
		local def
		def="$(to_msys_path "$LOCALAPPDATA")/Android/Sdk"
		if [[ -d "$def" ]]; then
			cand="$def"
			from="Windows 默认路径"
		fi
	fi
	if [[ -n "$cand" ]]; then
		SDK_PATH="$(to_msys_path "$cand")"
		SDK_FROM="$from"
	fi
}

# 目录是否至少含 platforms / build-tools / platform-tools 之一（判断像不像一个 SDK）
sdk_looks_right() {
	[[ -d "$1/platforms" || -d "$1/build-tools" || -d "$1/platform-tools" ]]
}

# 列出已安装的 platform 目录名（顿号连接），如 android-35、android-37
list_platforms() {
	local out d
	for d in "$1"/platforms/android-*; do
		[[ -d "$d" ]] && out="${out:+$out、}$(basename "$d")"
	done
	printf '%s\n' "$out"
}

# 查 SDK 是否装了指定 platform：android-37 与 android-37.0（新版 SDK 的次版本目录）都算有
# 命中则输出实际目录名，没有输出空串
sdk_has_platform() {
	local d
	for d in "$1/platforms/$2" "$1/platforms/$2".*; do
		[[ -d "$d" ]] && { basename "$d"; return; }
	done
}

# build-tools 目录下的最高版本号（如 36.0.0）；一个都没有则输出空串
max_build_tools() {
	ls "$1/build-tools" 2>/dev/null | sort -V | tail -1
}

# 定位 adb：先在 SDK 的 platform-tools 里找（Windows 是 adb.exe），否则 PATH；存 ADB_PATH / ADB_FROM
detect_adb() {
	ADB_PATH=""
	ADB_FROM=""
	if [[ -n "${1:-}" ]]; then
		local cand="$1/platform-tools/adb.exe"
		[[ -x "$cand" ]] || cand="$1/platform-tools/adb"
		if [[ -x "$cand" ]]; then
			ADB_PATH="$cand"
			ADB_FROM="SDK platform-tools"
		fi
	fi
	if [[ -z "$ADB_PATH" ]] && command -v adb >/dev/null 2>&1; then
		ADB_PATH="$(command -v adb)"
		ADB_FROM="PATH"
	fi
}

# Gradle wrapper 四件套是否齐全；缺失清单存 WRAPPER_MISSING（顿号连接）
judge_wrapper() {
	WRAPPER_MISSING=""
	local f
	for f in gradlew gradlew.bat gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.jar; do
		[[ -s "$REPO/$f" ]] || WRAPPER_MISSING="${WRAPPER_MISSING:+$WRAPPER_MISSING、}$f"
	done
}

# gradlew 是否被 git 换行符转换成 CRLF（会导致 Git Bash 下 ./gradlew 报错）；
# 用 tr 数 CR 字节而不用 grep——MSYS 的 grep 在部分挂载点（如 /tmp）会先剥掉 \r 再匹配
# $1 可选：仓库根目录，缺省用本脚本定位到的 $REPO（留参数是为了测试能注入假仓库）
gradlew_is_crlf() {
	local f="${1:-$REPO}/gradlew"
	[[ -f "$f" && "$(tr -dc '\r' < "$f" 2>/dev/null | wc -c)" -gt 0 ]]
}

# 从 wrapper 配置读 Gradle 发行版版本号（仅用于展示）
gradle_dist_version() {
	sed -n 's/^distributionUrl=.*gradle-\([0-9][0-9.]*\)-.*/\1/p' \
		"$REPO/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | head -1
}

# 路径所在盘的剩余空间（KB）；取不到输出空串
disk_free_kb() {
	df -Pk "$1" 2>/dev/null | awk 'NR==2 {print $4}'
}

# KB → GB，保留一位小数
kb_to_gb() {
	awk -v kb="$1" 'BEGIN { printf "%.1f", kb / 1024 / 1024 }'
}

# 探测 Gradle 官方下载源：0 可直连 / 1 连不上 / 2 无法判定（机器上没有 curl）
probe_gradle_host() {
	command -v curl >/dev/null 2>&1 || { echo 2; return; }
	local code
	code="$(curl -s -o /dev/null --max-time 5 -w '%{http_code}' \
		https://services.gradle.org/distributions/ 2>/dev/null)" || { echo 1; return; }
	[[ "$code" =~ ^[23] ]] && echo 0 || echo 1
}

# ---- 报告项：检测层取事实 → 三态输出 + 建议命令 + 文档指引 ----

report_java() {
	detect_java
	if [[ -z "$JAVA_EXE" ]]; then
		miss "JDK：未找到 java 命令"
		info "需要：JDK $NEED_JDK_MAJOR（推荐 Eclipse Temurin 17）"
		info "安装：winget install EclipseAdoptium.Temurin.17.JDK，或到 https://adoptium.net 下载"
		info "文档：docs/build-and-install.md →「0. 环境要求」与「方式一 第 3 步」"
		return
	fi
	local raw vs major
	raw="$("$JAVA_EXE" -version 2>&1 | head -3)"
	vs="$(version_string_of "$raw")"
	if [[ -z "$vs" ]]; then
		unknown "JDK：无法解析「$JAVA_EXE -version」的输出，请人工确认是否为 17"
		info "文档：docs/build-and-install.md →「0. 环境要求」"
		return
	fi
	major="$(java_major_of "$vs")"
	if [[ "$major" == "$NEED_JDK_MAJOR" ]]; then
		ok "JDK：$vs（来源：$JAVA_FROM）"
	else
		fail "JDK：需要 $NEED_JDK_MAJOR.x，现有 $vs（来源：$JAVA_FROM）"
		info "本工程要求 17 整：更高版本会让 Gradle 尝试联网另下 17，国内网络会卡死在启动守护进程"
		info "安装 17：winget install EclipseAdoptium.Temurin.17.JDK，或 https://adoptium.net"
		info "文档：docs/build-and-install.md →「方式一 第 3 步」JDK 相关问题"
	fi
}

report_sdk() {
	detect_sdk
	if [[ -z "$SDK_PATH" ]]; then
		miss "Android SDK：未找到（环境变量、local.properties、默认路径里都没有）"
		info "需要：Android SDK（含 Platform $NEED_PLATFORM 与 Build-Tools ≥ $NEED_BUILD_TOOLS_MAJOR）"
		info "安装：最省事是装 Android Studio（首次向导自动装 SDK），或用 cmdline-tools 的 sdkmanager"
		info "文档：docs/build-and-install.md →「0. 环境要求」与「方式一 第 1、3 步」"
		return
	fi
	if ! sdk_looks_right "$SDK_PATH"; then
		warn "Android SDK：找到目录但不像 SDK（缺 platforms/build-tools/platform-tools 子目录）"
		info "位置：$SDK_PATH（来源：$SDK_FROM）——请人工确认路径是否正确"
	else
		ok "Android SDK：$SDK_PATH（来源：$SDK_FROM）"
	fi
}

report_platform() {
	detect_sdk
	if [[ -z "$SDK_PATH" ]]; then
		miss "SDK Platform：需要 $NEED_PLATFORM——因未找到 Android SDK，无法检查"
		info "先按上一条（Android SDK）的指引装好 SDK 再看本项"
		return
	fi
	local found
	found="$(sdk_has_platform "$SDK_PATH" "$NEED_PLATFORM")"
	if [[ -n "$found" ]]; then
		ok "SDK Platform：$found 已安装（compileSdk ${NEED_PLATFORM#android-} 对应）"
	else
		local have
		have="$(list_platforms "$SDK_PATH")"
		miss "SDK Platform：需要 $NEED_PLATFORM（或 $NEED_PLATFORM.x），未安装${have:+（现有：$have）}"
		info "安装：Android Studio → Settings → Languages & Frameworks → Android SDK → 勾选 Android API ${NEED_PLATFORM#android-}"
		info "或命令行：sdkmanager \"platforms;$NEED_PLATFORM\""
		info "文档：docs/build-and-install.md →「方式一 第 3 步」platform 安装问题"
	fi
}

report_build_tools() {
	detect_sdk
	if [[ -z "$SDK_PATH" ]]; then
		miss "Build-Tools：需要 ≥ ${NEED_BUILD_TOOLS_MAJOR}.0.0——因未找到 Android SDK，无法检查"
		info "先按 Android SDK 一条的指引装好 SDK 再看本项"
		return
	fi
	local max major
	max="$(max_build_tools "$SDK_PATH")"
	if [[ -z "$max" ]]; then
		miss "Build-Tools：需要 ≥ ${NEED_BUILD_TOOLS_MAJOR}.0.0，一个版本都没装"
		info "安装：SDK Manager 勾选 Android SDK Build-Tools ${NEED_BUILD_TOOLS_MAJOR}，或 sdkmanager \"build-tools;${NEED_BUILD_TOOLS_MAJOR}.0.0\""
		info "文档：docs/build-and-install.md →「0. 环境要求」"
		return
	fi
	major="$(java_major_of "$max")"
	if (( major >= NEED_BUILD_TOOLS_MAJOR )); then
		ok "Build-Tools：$max（满足 ≥ ${NEED_BUILD_TOOLS_MAJOR}）"
	else
		fail "Build-Tools：需要 ≥ ${NEED_BUILD_TOOLS_MAJOR}.0.0，现有 $max"
		info "安装：SDK Manager 勾选 Android SDK Build-Tools ${NEED_BUILD_TOOLS_MAJOR}，或 sdkmanager \"build-tools;${NEED_BUILD_TOOLS_MAJOR}.0.0\""
		info "文档：docs/build-and-install.md →「0. 环境要求」"
	fi
}

report_adb() {
	detect_sdk
	detect_adb "$SDK_PATH"
	if [[ -n "$ADB_PATH" ]]; then
		ok "adb：$ADB_PATH（来源：$ADB_FROM）"
	else
		miss "adb：未找到（SDK 的 platform-tools 里和 PATH 里都没有）"
		info "安装：SDK Manager 勾选 Android SDK Platform-Tools"
		info "文档：docs/build-and-install.md →「方式二 第 2 步」adb 问题"
	fi
}

report_wrapper() {
	judge_wrapper
	if [[ -n "$WRAPPER_MISSING" ]]; then
		miss "Gradle wrapper：缺少 $WRAPPER_MISSING"
		info "检出可能不完整或被换行符转换破坏：git checkout -- <缺失文件>，或重新克隆仓库"
		return
	fi
	local ver
	ver="$(gradle_dist_version)"
	if gradlew_is_crlf; then
		warn "Gradle wrapper：文件齐全，但 gradlew 被转成了 CRLF 行尾，Git Bash 下 ./gradlew 会报错"
		info "Windows 用户可改用 gradlew.bat（不受影响）；彻底修复：git config core.autocrlf input 后重新检出"
	else
		ok "Gradle wrapper：文件齐全${ver:+（Gradle $ver，首次构建自动下载）}"
	fi
}

report_disk() {
	local kb gb
	kb="$(disk_free_kb "$HOME")"
	if [[ -z "$kb" ]]; then
		unknown "磁盘空间：无法读取 $HOME 所在盘的剩余空间，请人工确认 ≥ ${NEED_DISK_GB} GB"
		return
	fi
	gb="$(kb_to_gb "$kb")"
	if (( kb >= NEED_DISK_GB * 1024 * 1024 )); then
		ok "磁盘空间：$HOME 所在盘剩余 ${gb} GB（≥ ${NEED_DISK_GB} GB，够首次构建缓存）"
	else
		fail "磁盘空间：需要 ≥ ${NEED_DISK_GB} GB，现有 ${gb} GB（$HOME 所在盘）"
		info "首次构建要下载 Gradle 发行版与依赖约 2~4 GB；清理该盘，或把 GRADLE_USER_HOME 挪到大盘"
		info "文档：docs/build-and-install.md →「0. 环境要求」磁盘一行"
	fi
}

report_network() {
	local r
	r="$(probe_gradle_host)"
	case "$r" in
		0)
			ok "Gradle 下载源：services.gradle.org 可直连"
			;;
		1)
			warn "Gradle 下载源：连不上 services.gradle.org（国内网络常见，连接被重置）"
			info "首次构建会卡在下载 Gradle 发行版，可提前用镜像：https://mirrors.cloud.tencent.com/gradle/"
			info "镜像下载与预置方法：docs/build-and-install.md →「方式一 第 3 步」；docs/env-pitfalls.md 第 4 条"
			;;
		2)
			unknown "Gradle 下载源：未找到 curl，无法探测连通性"
			;;
	esac
}

# ---- 主流程：输出表头 → 逐项报告 → 汇总与退出码 ----
main() {
	echo "每日掌控 · 构建环境自检（只读检查，不修改任何文件与设置）"
	echo "工程根目录：$REPO"
	echo "----------------------------------------------------------------"
	report_java
	report_sdk
	report_platform
	report_build_tools
	report_adb
	report_wrapper
	report_disk
	report_network
	echo "----------------------------------------------------------------"
	local warn_note=""
	(( WARN > 0 )) && warn_note="，另有 $WARN 项预警（不影响判定，建议看其指引）"
	if (( BAD == 0 )); then
		echo "汇总：$PASS 项通过，环境就绪$warn_note——可以构建：./gradlew assembleDebug"
	else
		echo "汇总：$PASS 项通过，$BAD 项需处理（见上方 [ 缺失 ] / [ 不符 ] / [ 未知 ] 条目）$warn_note"
	fi
	(( BAD > 0 )) && exit 1
	exit 0
}

# 直接执行才跑主流程；被测试脚本 source 时只暴露检测函数
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
	main
fi
