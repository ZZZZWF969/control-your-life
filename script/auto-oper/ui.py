# 模拟器 UI 自动化辅助：抓取界面结构、按文本找控件坐标、点按
# 用法：
#   python script/auto-oper/ui.py dump           # 抓取当前界面并列出所有文本
#   python script/auto-oper/ui.py find "文本"     # 打印含该文本控件的中心坐标（找不到时退出码 1）
#   python script/auto-oper/ui.py tap "文本"      # 点击含该文本的控件
# 说明：界面结构直接从 adb stdout 读入内存，不落任何本地文件；
#       find/tap 每次调用都会重新抓取一次（约一两秒），保证坐标基于最新界面
import os
import re
import subprocess
import sys

ADB = os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk", "platform-tools", "adb.exe")
SERIAL = "emulator-5554"


def grab_xml():
	# exec-out 直读 uiautomator 输出流；MSYS_NO_PATHCONV 防 Git Bash 路径改写设备侧参数
	env = dict(os.environ, MSYS_NO_PATHCONV="1")
	result = subprocess.run(
		[ADB, "-s", SERIAL, "exec-out", "uiautomator", "dump", "/dev/tty"],
		capture_output=True,
		text=True,
		env=env,
		timeout=30,
	)
	return result.stdout


def center(xml, text):
	# 在整棵控件树里找首个含目标文本的节点，取 bounds 四角算中心
	m = re.search(
		r'<node[^>]*text="[^"]*' + re.escape(text) + r'[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
		xml,
	)
	if not m:
		return None
	x = (int(m.group(1)) + int(m.group(3))) // 2
	y = (int(m.group(2)) + int(m.group(4))) // 2
	return x, y


if __name__ == "__main__":
	action = sys.argv[1]
	xml = grab_xml()
	if action == "dump":
		texts = re.findall(r'text="([^"]+)"', xml)
		print("\n".join(t for t in dict.fromkeys(texts) if t))
	elif action in ("find", "tap"):
		pos = center(xml, sys.argv[2])
		if pos is None:
			sys.exit(1)
		if action == "tap":
			subprocess.run([ADB, "-s", SERIAL, "shell", "input", "tap", str(pos[0]), str(pos[1])], timeout=30)
			print("tapped", pos[0], pos[1])
		else:
			print(pos[0], pos[1])
