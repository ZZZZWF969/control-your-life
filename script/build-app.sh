#!/usr/bin/env bash
# =============================================================================
# 每日掌控 · 一键构建 debug APK（面向刚拿到源码的开发者）
#
# 用法：双击 script/build-app.bat（推荐新手），或在 Git Bash 执行：
#   bash script/build-app.sh
#
# 流程：环境自检（借用 check-env.sh）→ 构建 debug APK → 展示路径与大小
#       → 交互终端下自动打开产物所在文件夹并选中 APK
# 说明：只构建 debug 包（本地调试签名自动管理、可直接安装）；
#       release 正式包涉及仓库主人的私钥签名，不在本脚本范围内
# =============================================================================
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
APK_REL="app/build/outputs/apk/debug/app-debug.apk"

# 复用 check-env 的路径转换等函数；它自带「直接执行才跑主流程」守卫，source 不会触发检查
source "$SCRIPT_DIR/check-env.sh"

# MSYS 路径 → Windows 风格（给用户在资源管理器里对照用）；无 cygpath 时原样返回
winpath() {
	if command -v cygpath >/dev/null 2>&1; then
		cygpath -w "$1" 2>/dev/null || echo "$1"
	else
		echo "$1"
	fi
}

# 字节数 → 友好大小显示（KB / MB）
human_size() {
	awk -v b="$1" 'BEGIN {
		if (b >= 1048576) printf "%.1f MB", b / 1048576
		else printf "%.0f KB", b / 1024
	}'
}

echo "每日掌控 · 一键构建 debug APK"
echo "工程根目录：$REPO"
echo "=================================================================="
echo
echo "第一步：环境自检（只读检查，来自 script/check-env.sh）"
echo "------------------------------------------------------------------"
if bash "$SCRIPT_DIR/check-env.sh"; then
	:
else
	echo "------------------------------------------------------------------"
	echo "环境自检未通过：请先按上方 [ 缺失 ] / [ 不符 ] 条目的指引逐项处理，"
	echo "处理后重新运行本脚本。文档：docs/build-and-install.md「0. 环境要求」。"
	exit 1
fi
echo "------------------------------------------------------------------"
echo
echo "第二步：构建 debug APK"
echo "  · 构建输出实时滚动；首次构建需联网下载依赖（几百 MB），可能 5~15 分钟"
echo "  · 若自检里出现 Gradle 下载源预警：国内网络可能卡在下载步，"
echo "    可按 docs/build-and-install.md「方式一 第 3 步」的镜像方案提前备好再构建"
echo
read -r -p "回车开始构建（Ctrl+C 可随时取消）..." _
echo

# 用 sh 调 gradlew：不依赖文件可执行位（ZIP 下载用户的文件没有执行位也能跑）
if ! (cd "$REPO" && sh ./gradlew assembleDebug); then
	echo
	echo "构建失败：完整报错见上方滚动输出。常见原因与解法："
	echo "  · 卡在下载 Gradle / 依赖 → 镜像方案：docs/build-and-install.md「方式一 第 3 步」、docs/env-pitfalls.md 第 4 条"
	echo "  · JDK / SDK 相关报错 → docs/build-and-install.md「0. 环境要求」与「方式二 第 1 步」"
	exit 1
fi

APK="$REPO/$APK_REL"
if [[ ! -s "$APK" ]]; then
	echo "异常：Gradle 报告成功但找不到产物 $APK_REL——请把上方构建输出反馈到 issue，谢谢"
	exit 1
fi

echo
echo "构建成功："
echo "  APK 路径：$(winpath "$APK")"
echo "  大小：$(human_size "$(stat -c %s "$APK")")"

# 交互终端下自动打开产物文件夹并选中 APK；输出被重定向（测试/CI）时跳过，避免乱弹窗口
if [[ -t 1 ]] && command -v cygpath >/dev/null 2>&1; then
	# explorer 的 /select 需要反斜杠路径；其退出码恒为 1 属已知怪癖，不检查
	explorer.exe "/select,$(cygpath -w "$APK")" || true
	echo "  已为你打开产物所在文件夹并选中 APK（若无窗口弹出，按上面路径手动打开）"
fi
