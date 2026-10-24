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

# 正常场景返回的「报告内容」：符合应用约定的报告 schema
REPORT = {
	"date": "2026-10-22",
	"summary": "整体饮食结构均衡，蛋白质摄入略低于近30天常态，运动量达标。",
	"dietAnalysis": "三餐规律，晚餐偏晚；蔬菜来源充足，主食以精制米面为主，粗粮占比较低。营养品摄入以复合维生素与钙为主，剂量平稳。",
	"exerciseAnalysis": "慢跑30分钟属中等强度有氧，结合近30天频率（平均每周3次）处于健康区间。",
	"suggestions": [
		{"topic": "蛋白质", "content": "今天蛋白质来源偏少，晚餐可加一份豆制品或鸡蛋。", "level": "提醒"},
		{"topic": "主食结构", "content": "近30天精制主食为主，建议每周替换2-3餐为杂粮主食。", "level": "普通"},
		{"topic": "运动", "content": "保持当前频率即可；如方便，每周可加1次力量训练平衡肌力。", "level": "普通"}
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
		print("[mock] POST %s model=%s messages=%d" % (self.path, model, len(body.get("messages", []))))
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
