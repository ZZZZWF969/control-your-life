#!/usr/bin/env bash
# =============================================================================
# check-env.sh 的回归测试：在临时目录伪造各种故障/正常现场，断言输出与退出码
# 不依赖也不破坏真实环境——这正是环境自检脚本的标准测法（遮蔽 PATH + 替身 + 假目录树）
#
# 用法：bash script/auto-oper/test-check-env.sh
# 退出码：0 = 全部用例通过；1 = 有失败用例
#
# 注意：本文件的断言函数刻意叫 tpass/tfail——场景 0 会 source 被测脚本，
# 而它自带 fail()/PASS/BAD 等同名定义，撞名会让计数全部失真
# =============================================================================
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SUBJECT="$ROOT/script/check-env.sh"
TMP="$(mktemp -d)"
OUT="$TMP/last-output"
trap 'rm -rf "$TMP"' EXIT

TPASS=0
TFAIL=0

tpass() { TPASS=$((TPASS + 1)); printf '  通过：%s\n' "$1"; }
tfail() { TFAIL=$((TFAIL + 1)); printf '  失败：%s\n' "$1"; }
scene() { printf '\n[场景] %s\n' "$1"; }

# 运行假仓库里的被测脚本；$1=假仓库根，其余参数原样作为 env 前缀（PATH、JAVA_HOME 等）
run_case() {
	local repo="$1"
	shift
	RC=0
	env -u JAVA_HOME -u ANDROID_HOME -u ANDROID_SDK_ROOT -u LOCALAPPDATA "$@" \
		bash "$repo/script/check-env.sh" >"$OUT" 2>&1 || RC=$?
}

# 输出断言（固定字符串匹配，避免正则转义噪音）
has()   { grep -qF -- "$1" "$OUT" && tpass "输出含「$1」" || tfail "输出未含「$1」"; }
hasnt() { grep -qF -- "$1" "$OUT" && tfail "输出不该含「$1」" || tpass "输出不含「$1」"; }
rc_is() { [ "$RC" = "$1" ] && tpass "退出码=$1" || tfail "退出码期望 $1，实际 $RC"; }

# ---- 造现场的工具 ----

# 假仓库：复制被测脚本进去（脚本按自身位置定位仓库根），wrapper 默认配成齐全
mkrepo() {
	local r="$TMP/$1"
	mkdir -p "$r/script" "$r/gradle/wrapper"
	cp "$SUBJECT" "$r/script/check-env.sh"
	printf '#!/bin/sh\nexit 0\n' > "$r/gradlew"
	printf '@echo off\n' > "$r/gradlew.bat"
	printf 'PKfake-jar\n' > "$r/gradle/wrapper/gradle-wrapper.jar"
	printf 'distributionUrl=https\\://services.gradle.org/distributions/gradle-9.8.1-bin.zip\n' \
		> "$r/gradle/wrapper/gradle-wrapper.properties"
	echo "$r"
}

# 假 SDK 目录树：plat37 / plat370 / plat35 / bt36 / bt34 / adb 自由组合
mksdk() {
	local d="$1"
	shift
	mkdir -p "$d"
	local f
	for f in "$@"; do
		case "$f" in
			plat37)  mkdir -p "$d/platforms/android-37" ;;
			plat370) mkdir -p "$d/platforms/android-37.0" ;;
			plat35)  mkdir -p "$d/platforms/android-35" ;;
			bt36)    mkdir -p "$d/build-tools/36.0.0" ;;
			bt34)    mkdir -p "$d/build-tools/34.0.0" ;;
			adb)     mkdir -p "$d/platform-tools"
				printf '@echo off\n' > "$d/platform-tools/adb.exe"
				chmod +x "$d/platform-tools/adb.exe" ;;
		esac
	done
}

# java 替身：$1=目录 $2=版本号（打到 stderr，模仿真 java）；$2 为 bad 时输出无法解析的内容
mkjava() {
	mkdir -p "$1"
	{
		echo '#!/bin/sh'
		if [ "$2" = bad ]; then
			echo 'echo hello-world-no-version'
		else
			echo "echo 'openjdk version \"$2\" 2026-01-01' >&2"
			echo "echo 'OpenJDK Runtime Environment (build $2)' >&2"
		fi
	} > "$1/java"
	chmod +x "$1/java"
}

# curl 替身：ok（回 200）/ dead（连接失败退出码 7）/ absent（存在但不可执行=视同没有）
mkcurl() {
	mkdir -p "$1"
	case "$2" in
		ok)     printf '#!/bin/sh\necho 200\n' > "$1/curl"; chmod +x "$1/curl" ;;
		dead)   printf '#!/bin/sh\nexit 7\n' > "$1/curl"; chmod +x "$1/curl" ;;
		absent) printf 'not executable\n' > "$1/curl"; chmod -x "$1/curl" ;;
	esac
}

# ---- 0. 纯函数单测：source 被测脚本后直接灌样本 ----
scene "纯函数：版本解析与工具函数灌样本"
source "$SUBJECT"
t() { [ "$2" = "$3" ] && tpass "$1" || tfail "$1（期望 $3，实际 $2）"; }
t "主版本 17.0.2 → 17" "$(java_major_of 17.0.2)" "17"
t "主版本 21.0.5 → 21" "$(java_major_of 21.0.5)" "21"
t "主版本 1.8.0_392 → 8" "$(java_major_of 1.8.0_392)" "8"
t "版本提取" "$(version_string_of 'openjdk version "17.0.12" 2024-10-15')" "17.0.12"
t "版本提取（java 前缀也认）" "$(version_string_of 'java version "21.0.5"')" "21.0.5"
t "KB→GB 进位" "$(kb_to_gb 4194304)" "4.0"
if [[ "$(disk_free_kb "$HOME")" =~ ^[0-9]+$ ]]; then tpass "磁盘读取返回数字"; else tfail "磁盘读取异常"; fi
t "探测函数：环境里无 curl → 返回 2" "$(PATH= probe_gradle_host)" "2"
P="$(mktemp -d)"
printf '#!/bin/sh\r\n' > "$P/gradlew"
if gradlew_is_crlf "$P"; then tpass "gradlew_is_crlf 认出 CR 文件"; else tfail "gradlew_is_crlf 没认出 CR 文件"; fi
printf '#!/bin/sh\n' > "$P/gradlew"
if gradlew_is_crlf "$P"; then tfail "gradlew_is_crlf 误报 LF 文件"; else tpass "gradlew_is_crlf 对 LF 文件返回假"; fi
rm -rf "$P"

# ---- 1. JDK 三态 ----
scene "JDK 缺失（PATH 里看不见 java）"
R="$(mkrepo s1)"
run_case "$R" PATH="/usr/bin"
has "[ 缺失 ] JDK"
has "winget install EclipseAdoptium.Temurin.17.JDK"
rc_is 1

scene "JDK 版本不符（21 替身）"
R="$(mkrepo s2)"
mkjava "$TMP/j21" 21.0.5
mkcurl "$TMP/cok" ok
run_case "$R" PATH="$TMP/j21:$TMP/cok:/usr/bin"
has "[ 不符 ] JDK"
has "需要 17.x，现有 21.0.5"
rc_is 1

scene "JDK 正确（17 替身，来源 PATH）"
R="$(mkrepo s3)"
mkjava "$TMP/j17" 17.0.2
mkcurl "$TMP/cok2" ok
run_case "$R" PATH="$TMP/j17:$TMP/cok2:/usr/bin"
has "[ 通过 ] JDK：17.0.2（来源：PATH）"

scene "JDK 版本输出无法解析（坏替身）"
R="$(mkrepo s4)"
mkjava "$TMP/jbad" bad
run_case "$R" PATH="$TMP/jbad:/usr/bin"
has "[ 未知 ] JDK"

scene "JDK 经 JAVA_HOME 命中"
R="$(mkrepo s5)"
mkdir -p "$TMP/jh/bin"
mkjava "$TMP/jh/bin" 17.0.9
mkcurl "$TMP/cok3" ok
run_case "$R" PATH="$TMP/cok3:/usr/bin" JAVA_HOME="$TMP/jh"
has "JDK：17.0.9（来源：JAVA_HOME）"

# ---- 2. SDK 三态 ----
scene "ANDROID_HOME 指向齐备的假 SDK（37 + bt36 + adb）"
R="$(mkrepo s6)"
mksdk "$TMP/sdkfull" plat37 bt36 adb
mkjava "$TMP/j2" 17.0.2
mkcurl "$TMP/c2" ok
run_case "$R" PATH="$TMP/j2:$TMP/c2:/usr/bin" ANDROID_HOME="$TMP/sdkfull"
has "[ 通过 ] Android SDK"
has "来源：环境变量 ANDROID_HOME"
has "[ 通过 ] SDK Platform：android-37"
has "[ 通过 ] Build-Tools：36.0.0"
has "来源：SDK platform-tools"

scene "platform 只有 35（报缺失并列出现有）"
R="$(mkrepo s7)"
mksdk "$TMP/sdk35" plat35 bt36 adb
mkjava "$TMP/j3" 17.0.2
mkcurl "$TMP/c3" ok
run_case "$R" PATH="$TMP/j3:$TMP/c3:/usr/bin" ANDROID_HOME="$TMP/sdk35"
has "[ 缺失 ] SDK Platform"
has "现有：android-35"

scene "platform 目录名是新版风格 android-37.0"
R="$(mkrepo s8)"
mksdk "$TMP/sdk370" plat370 bt36 adb
mkjava "$TMP/j4" 17.0.2
mkcurl "$TMP/c4" ok
run_case "$R" PATH="$TMP/j4:$TMP/c4:/usr/bin" ANDROID_HOME="$TMP/sdk370"
has "[ 通过 ] SDK Platform：android-37.0"

scene "build-tools 过旧（34.0.0）"
R="$(mkrepo s9)"
mksdk "$TMP/sdkbt34" plat37 bt34 adb
mkjava "$TMP/j5" 17.0.2
mkcurl "$TMP/c5" ok
run_case "$R" PATH="$TMP/j5:$TMP/c5:/usr/bin" ANDROID_HOME="$TMP/sdkbt34"
has "[ 不符 ] Build-Tools：需要 ≥ 36.0.0，现有 34.0.0"

scene "build-tools 一个没有"
R="$(mkrepo s10)"
mksdk "$TMP/sknobt" plat37 adb
mkjava "$TMP/j6" 17.0.2
mkcurl "$TMP/c6" ok
run_case "$R" PATH="$TMP/j6:$TMP/c6:/usr/bin" ANDROID_HOME="$TMP/sknobt"
has "[ 缺失 ] Build-Tools"

scene "adb 缺失（SDK 里没有、PATH 里也没有）"
R="$(mkrepo s11)"
mksdk "$TMP/sdknoadb" plat37 bt36
mkjava "$TMP/j7" 17.0.2
mkcurl "$TMP/c7" ok
run_case "$R" PATH="$TMP/j7:$TMP/c7:/usr/bin" ANDROID_HOME="$TMP/sdknoadb"
has "[ 缺失 ] adb"

scene "SDK 整体缺失（无环境变量/无 local.properties/无默认路径）→ 级联报无法检查"
R="$(mkrepo s12)"
mkjava "$TMP/j8" 17.0.2
mkcurl "$TMP/c8" ok
run_case "$R" PATH="$TMP/j8:$TMP/c8:/usr/bin"
has "[ 缺失 ] Android SDK"
has "SDK Platform：需要 android-37——因未找到 Android SDK，无法检查"
has "因未找到 Android SDK，无法检查"
has "[ 缺失 ] adb"
rc_is 1

scene "local.properties：CRLF 行尾 + 转义路径 + 带空格目录，仍能命中"
R="$(mkrepo s13)"
mksdk "$TMP/my sdk" plat37 bt36 adb
mkjava "$TMP/j9" 17.0.2
mkcurl "$TMP/c9" ok
# 手工构造 properties：冒号加 \:、反斜杠翻倍、行尾 CRLF——还原后应指向 "$TMP/my sdk"
esc="$(cygpath -w "$TMP/my sdk" | sed -e 's/\\/\\\\/g' -e 's/:/\\:/g')"
printf 'sdk.dir=%s\r\n' "$esc" > "$R/local.properties"
run_case "$R" PATH="$TMP/j9:$TMP/c9:/usr/bin"
has "来源：local.properties"
has "[ 通过 ] SDK Platform"

# ---- 3. Gradle wrapper ----
scene "wrapper 的 jar 缺失"
R="$(mkrepo s14)"
rm "$R/gradle/wrapper/gradle-wrapper.jar"
run_case "$R" PATH="/usr/bin"
has "[ 缺失 ] Gradle wrapper：缺少"
has "gradle-wrapper.jar"

scene "gradlew 被转成 CRLF 行尾"
R="$(mkrepo s15)"
printf '#!/bin/sh\r\nexit 0\r\n' > "$R/gradlew"
mkjava "$TMP/j10" 17.0.2
mkcurl "$TMP/c10" ok
run_case "$R" PATH="$TMP/j10:$TMP/c10:/usr/bin"
has "[ 预警 ] Gradle wrapper"
has "CRLF"

scene "wrapper 齐全时展示 Gradle 版本号"
R="$(mkrepo s16)"
mkjava "$TMP/j11" 17.0.2
mkcurl "$TMP/c11" ok
run_case "$R" PATH="$TMP/j11:$TMP/c11:/usr/bin"
has "Gradle 9.8.1，首次构建自动下载"

# ---- 4. 网络 ----
scene "下载源连不上（curl 替身退出码 7）→ 预警给镜像，不计失败"
R="$(mkrepo s17)"
mksdk "$TMP/sdkn" plat37 bt36 adb
mkjava "$TMP/j12" 17.0.2
mkcurl "$TMP/cdead" dead
run_case "$R" PATH="$TMP/j12:$TMP/cdead:/usr/bin" ANDROID_HOME="$TMP/sdkn"
has "[ 预警 ] Gradle 下载源"
has "mirrors.cloud.tencent.com"
rc_is 0

scene "curl 不存在 → 未知而非崩溃（无 curl 分支已在纯函数场景验证，此处验证脚本整体不崩）"
R="$(mkrepo s18)"
mkjava "$TMP/j13" 17.0.2
run_case "$R" PATH="$TMP/j13:/usr/bin"
hasnt "[ 缺失 ] JDK"
rc_is 1

# ---- 5. 全绿合成现场 ----
scene "合成全绿：替身 JDK17 + 假 SDK 齐备 + curl 通 → 退出码 0"
R="$(mkrepo s19)"
mksdk "$TMP/sdkg" plat37 bt36 adb
mkjava "$TMP/j14" 17.0.2
mkcurl "$TMP/cok9" ok
run_case "$R" PATH="$TMP/j14:$TMP/cok9:/usr/bin" ANDROID_HOME="$TMP/sdkg"
rc_is 0
has "环境就绪"
hasnt "[ 缺失 ]"
hasnt "[ 不符 ]"
hasnt "[ 未知 ]"

# ---- 汇总 ----
printf '\n==============================================\n'
printf '回归测试汇总：通过 %d，失败 %d\n' "$TPASS" "$TFAIL"
[ "$TFAIL" -eq 0 ] && exit 0
exit 1
