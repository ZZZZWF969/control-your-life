#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""本地 mock AI 服务：开发与自动化验收用，模拟 OpenAI 兼容的 /chat/completions 端点。

只监听 127.0.0.1；模拟器内经 http://10.0.2.2:18080/v1 访问到宿主机的本服务
（应用的网络安全配置只对 10.0.2.2 放行明文 HTTP）。

场景切换无需重启服务：把应用设置里的「模型名」改成对应关键字即可——
  正常场景：任意模型名（如 deepseek-chat）
  含 err401 / err402 / err429 / err500：返回对应 HTTP 错误码
  含 timeout：挂起 150 秒（触发应用侧 120 秒读超时）
  含 badjson：HTTP 200 但回复正文不是 JSON（触发应用的宽容解析降级）

用法：python mock-ai-server.py [端口]（默认 18080）
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 18080

# 正常场景返回的「报告内容」：符合应用约定的报告 schema（含 basis 依据字段与营养品双层建议示例）
REPORT = {
	"date": "2026-10-22",
	"summary": "三餐规律达标；蛋白质摄入略低；运动量达标",
	"dietAnalysis": "三餐规律，晚餐偏晚；蔬菜来源充足，主食以精制米面为主，粗粮占比较低。营养品摄入以复合维生素与钙为主，剂量平稳。",
	"exerciseAnalysis": "慢跑30分钟属中等强度有氧，结合近30天频率（平均每周3次）处于健康区间。",
	"suggestions": [
		{"topic": "蛋白质", "headline": "晚餐加豆制品或鸡蛋补蛋白", "detail": "今天蛋白质来源偏少：晚餐加一份豆制品或鸡蛋；长期不足可考虑乳清蛋白粉，每次20-30g随餐，以产品说明或医嘱为准。", "level": "提醒", "basis": "昨日三餐仅午餐盒饭含肉类蛋白，近30天高蛋白食物频次偏低；膳食指南建议每日动物性食物120-200克"},
		{"topic": "主食结构", "headline": "每周2-3餐换成杂粮主食", "detail": "如糙米饭、燕麦或全麦面，等量替换精制米面即可，无需减量。", "level": "普通", "basis": "近30天高频食物以精制米面为主，粗粮未见记录"},
		{"topic": "钙与维生素D", "headline": "每天加一杯奶，日照不足补D3", "detail": "优先每天一杯奶或等量酸奶；室内生活日照不足时，可选维生素D3滴剂400-800IU/日与钙片同补，以产品说明或医嘱为准。", "level": "普通", "basis": "近30天无乳制品记录，营养品中未含维生素D；膳食指南建议每日奶及奶制品300-500克"},
		{"topic": "运动", "headline": "保持频率，每周加1次力量", "detail": "当前有氧频率处于健康区间，加入力量训练可平衡肌力与骨密度。", "level": "普通", "basis": "近30天运动覆盖12天，均为有氧类型"}
	],
	"questions": []
}


class Handler(BaseHTTPRequestHandler):

	def _send_json(self, code, obj):
		data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
		self.send_response(code)
		self.send_header("Content-Type", "application/json")
		self.send_header("Content-Length", str(len(data)))
		self.end_headers()
		self.wfile.write(data)

	def do_POST(self):
		length = int(self.headers.get("Content-Length", 0))
		body = json.loads(self.rfile.read(length) or b"{}")
		model = str(body.get("model", ""))
		messages = body.get("messages", [])
		# 打印 system 消息摘要：自动化验收「自定义提示词是否真的上行」的观测点
		system_brief = next((str(m.get("content", ""))[:60] for m in messages if m.get("role") == "system"), "")
		print("[mock] POST %s model=%s messages=%d system[:60]=%s" % (self.path, model, len(messages), system_brief))
		if "err401" in model:
			return self._send_json(401, {"error": {"message": "模拟：Key 无效"}})
		if "err402" in model:
			return self._send_json(402, {"error": {"message": "模拟：余额不足"}})
		if "err429" in model:
			return self._send_json(429, {"error": {"message": "模拟：请求受限"}})
		if "err500" in model:
			return self._send_json(500, {"error": {"message": "模拟：服务器故障"}})
		if "timeout" in model:
			time.sleep(150)
			return self._send_json(200, {"choices": [{"message": {"role": "assistant", "content": "迟到的响应"}}]})
		if "badjson" in model:
			content = "这不是JSON：抱歉，这次直接用自然语言回复了。\n建议：多吃优质蛋白，保持运动频率。"
		elif "qa" in model:
			# 0.4.1 追问场景：报告带 1 档案型 + 1 一次性问题
			report = dict(REPORT)
			report["questions"] = [
				{"topic": "身高体重", "text": "方便的话告诉我你的身高和体重（如 175cm/70kg），便于给出更准确的摄入量建议", "type": "profile"},
				{"topic": "昨晚睡眠", "text": "昨晚大概几点入睡、睡了几个小时？", "type": "oneoff"}
			]
			content = json.dumps(report, ensure_ascii=False)
		else:
			content = json.dumps(REPORT, ensure_ascii=False)
		self._send_json(200, {
			"choices": [{"message": {"role": "assistant", "content": content}}],
			"usage": {"prompt_tokens": 100, "completion_tokens": 50}
		})

	def do_GET(self):
		self._send_json(200, {"status": "ok"})

	def log_message(self, *args):
		# 关掉默认逐请求噪音日志，关键信息已在 do_POST 里打印
		pass


if __name__ == "__main__":
	print("mock AI 服务已启动：http://127.0.0.1:%d" % PORT)
	print("模拟器内应用填地址：http://10.0.2.2:%d/v1" % PORT)
	ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
