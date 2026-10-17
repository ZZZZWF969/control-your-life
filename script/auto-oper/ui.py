# 模拟器 UI 自动化辅助：抓取界面结构、按文本找控件坐标、点按
# 用法：
#   python script/auto-oper/ui.py dump           # 重新抓取当前界面到 ui.xml 并列出所有文本
#   python script/auto-oper/ui.py find "文本"     # 打印含该文本控件的中心坐标（找不到时退出码 1）
#   python script/auto-oper/ui.py tap "文本"      # 点击含该文本的控件
import os
import re
import subprocess
import sys

ADB = os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk", "platform-tools", "adb.exe")
SERIAL = "emulator-5554"
UI_FILE = os.path.join(os.path.dirname(__file__), "ui.xml")


def adb(*args, device_path=False):
	cmd = [ADB, "-s", SERIAL] + list(args)
	env = dict(os.environ, MSYS_NO_PATHCONV="1") if device_path else os.environ
	return subprocess.run(cmd, capture_output=True, text=True, env=env, timeout=30)


def dump():
	adb("shell", "rm", "-f", "/sdcard/window_dump.xml", device_path=True)
	adb("shell", "uiautomator", "dump")
	with open(UI_FILE, "w", encoding="utf-8") as f:
		f.write(adb("shell", "cat", "/sdcard/window_dump.xml", device_path=True).stdout)
	texts = re.findall(r'text="([^"]+)"', open(UI_FILE, encoding="utf-8").read())
	print("\n".join(t for t in dict.fromkeys(texts) if t))


def center(text):
	xml = open(UI_FILE, encoding="utf-8").read()
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
	if action == "dump":
		dump()
	elif action == "find":
		pos = center(sys.argv[2])
		if pos is None:
			sys.exit(1)
		print(pos[0], pos[1])
	elif action == "tap":
		pos = center(sys.argv[2])
		if pos is None:
			sys.exit(1)
		adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
		print("tapped", pos[0], pos[1])
