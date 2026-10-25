package com.dailycontrol.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL

// OpenAI 兼容接口客户端：全应用唯一的网络出口（0.4.0 第四阶段）
// 用平台自带的 HttpURLConnection：每天仅一次请求，不为一次调用引入第三方网络库。
// 安全约定：Key 只出现在 Authorization 请求头、只发往调用方传入的这一个地址；本类不打任何日志
object AiClient {

	// OpenAI 兼容格式的消息：role 为 system（角色与规则设定）或 user（本次实际内容）
	data class ChatMessage(val role: String, val content: String)

	// 调用结果三态：成功（拿到回复正文）/ HTTP 错误（带状态码，可译成「Key 无效/欠费」等）/ 网络错误（不通/超时）
	sealed class CallResult {
		data class Success(val content: String) : CallResult()
		data class HttpError(val httpCode: Int, val reasonText: String) : CallResult()
		data class NetworkError(val reasonText: String) : CallResult()
	}

	// 连接测试：发一次最小对话请求，验证「地址 + Key + 模型名」三要素整条链路可用
	suspend fun testConnection(baseUrl: String, apiKey: String, model: String): CallResult {
		return chatOnce(baseUrl, apiKey, model, listOf(ChatMessage("user", "ping")), forceJson = false)
	}

	// 正式调用：默认请求 JSON 输出（response_format 开关）；
	// 个别兼容商不认该参数返回 400，此时自动去掉参数原样重试一次
	suspend fun chat(baseUrl: String, apiKey: String, model: String, messages: List<ChatMessage>): CallResult {
		val first = chatOnce(baseUrl, apiKey, model, messages, forceJson = true)
		if (first is CallResult.HttpError && first.httpCode == 400) {
			return chatOnce(baseUrl, apiKey, model, messages, forceJson = false)
		}
		return first
	}

	// 单次 HTTP 请求：固定走 IO 线程；连接 15 秒、读取 120 秒（大模型生成一份报告可能要几十秒）
	private suspend fun chatOnce(
		baseUrl: String,
		apiKey: String,
		model: String,
		messages: List<ChatMessage>,
		forceJson: Boolean
	): CallResult = withContext(Dispatchers.IO) {
		val fullUrl = normalizeEndpoint(baseUrl)
		var connection: HttpURLConnection? = null
		try {
			connection = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
				requestMethod = "POST"
				connectTimeout = 15_000
				readTimeout = 120_000
				doOutput = true
				setRequestProperty("Content-Type", "application/json; charset=utf-8")
				setRequestProperty("Accept", "application/json")
				setRequestProperty("Authorization", "Bearer $apiKey")
			}
			val body = buildRequestBody(model, messages, forceJson)
			connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

			val code = connection.responseCode
			if (code == 200) {
				val text = connection.inputStream.bufferedReader().use { it.readText() }
				val content = parseContent(text)
				if (content != null) {
					CallResult.Success(content)
				} else {
					// HTTP 成功但结构不合：归为带状态码的错误，原因写清缺了什么
					CallResult.HttpError(code, "返回结构异常：未找到 choices[0].message.content")
				}
			} else {
				CallResult.HttpError(code, describeHttpError(code, readErrorBody(connection)))
			}
		} catch (e: MalformedURLException) {
			CallResult.NetworkError("地址格式不正确（需以 http:// 或 https:// 开头）")
		} catch (e: SocketTimeoutException) {
			CallResult.NetworkError("连接超时：接口长时间无响应")
		} catch (e: IOException) {
			CallResult.NetworkError("网络不通或地址无法解析")
		} finally {
			connection?.disconnect()
		}
	}

	// 用户可能填「到 /v1 为止」的 Base URL，也可能直接粘贴完整端点；统一补全成 chat 端点
	private fun normalizeEndpoint(baseUrl: String): String {
		val trimmed = baseUrl.trim().trimEnd('/')
		return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
	}

	private fun buildRequestBody(model: String, messages: List<ChatMessage>, forceJson: Boolean): String {
		val root = JSONObject()
		root.put("model", model)
		root.put("messages", JSONArray(messages.map { message ->
			JSONObject().put("role", message.role).put("content", message.content)
		}))
		// 低温度让输出更稳定，利于固定 JSON 格式
		root.put("temperature", 0.4)
		if (forceJson) {
			root.put("response_format", JSONObject().put("type", "json_object"))
		}
		return root.toString()
	}

	// 宽容解析回复：绝大多数兼容商返回标准结构，个别字段差异按 null 处理交给上层降级
	private fun parseContent(responseText: String): String? {
		return try {
			val root = JSONObject(responseText)
			root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
		} catch (e: Exception) {
			null
		}
	}

	private fun readErrorBody(connection: HttpURLConnection): String {
		return try {
			connection.errorStream?.bufferedReader()?.use { it.readText() }?.take(300) ?: ""
		} catch (e: IOException) {
			""
		}
	}

	// HTTP 状态码 → 用户能看懂的原因；错误正文截取片段附在后面便于排查
	private fun describeHttpError(code: Int, bodyText: String): String {
		val brief = bodyText.replace("\n", " ").trim()
		val head = when (code) {
			401 -> "Key 无效或未授权"
			402 -> "账户余额不足"
			403 -> "无权访问该模型"
			404 -> "接口地址不存在（检查地址与 /v1 路径）"
			429 -> "请求受限或额度耗尽"
			in 500..599 -> "服务商服务器故障"
			else -> "请求被拒绝（HTTP $code）"
		}
		return if (brief.isNotBlank()) "$head：$brief" else head
	}
}
