# AI 协调层 ai/（0.4.0 第四阶段第一批）

> 全应用**唯一的网络出口**。五个文件：一个 HTTP 客户端、三个纯逻辑组装/拆解器、一个编排器。
> 设计总纲见 [../architecture.md](../architecture.md)；本篇按文件下钻。

## 总体数据流

```
凌晨4:00闹钟 / 启动自愈 / AI页懒检查 / 「立即生成」按钮
        │  全部只调 AiReportGenerator 这一个入口（内部单飞锁，并发触发自动去重）
        ▼
AiDataSummarizer ──近30天统计概要──┐
                                  ├─► AiPromptBuilder ──提示词──► AiClient ──HTTPS──► 用户配置的接口
DAO 明细查询 ───昨日/缺口/30天明细──┘                                   │
                                                                      ▼
                        AiReport ◄── AiReportParser ◄── 返回正文（JSON）
                        （Room v5 落库，14 天滚动清理）
```

## AiClient.kt —— OpenAI 兼容 HTTP 客户端

- **为什么用 HttpURLConnection 而不是 OkHttp**：每天仅一次请求，任何第三方网络库都是为一次调用背上依赖与镜像拉取风险；平台自带库完全够用。
- `ChatMessage(role, content)`：OpenAI 消息格式的最小封装（system 设定角色 / user 携带数据）。
- `CallResult` 三态：`Success(正文)` / `HttpError(状态码, 可读原因)` / `NetworkError(可读原因)`。状态码映射：401 Key 无效、402 欠费、404 地址不对、429 受限、5xx 服务商故障——这些就是「中断补交」无需专门逻辑的原因：所有失败殊途同归为「不记账」。
- `chat()`：默认带 `response_format: json_object` + `temperature 0.4` 请求稳定 JSON；收到 400（个别兼容商不认该参数）自动去掉参数原样重试一次。
- `testConnection()`：发一条最小消息验证三要素整条链路，设置页「测试连接」按钮用。
- 端点拼装：用户填到 `/v1` 为止的 Base URL 自动补 `/chat/completions`；直接粘完整端点也能识别。
- 超时：连接 15 秒、读取 120 秒（大模型生成一份报告需要几十秒）。
- **安全**：Key 只出现在 `Authorization: Bearer` 请求头；本文件无任何日志输出。

## AiDataSummarizer.kt —— 近 30 天统计概要

纯函数：明细列表 → 中文键 JSONObject。统计内容：三餐规律度（各餐覆盖天数占比）、高频食物 Top10（食材文本按顿号/逗号拆词）、常吃营养品与成分剂量频次、运动类型/强度分布与覆盖天数。**为什么程序算而不用 AI 数**：又快又稳不出错、token 成本恒定（每天约 700 token）、隐私面最小。

## AiPromptBuilder.kt —— 提示词组装

- system 固定：营养学顾问角色（以《中国居民膳食指南（2022）》为参照、无明显过量不建议节食）+ 严格输出 JSON 约定（字段 schema、建议 3~5 条、questions 恒空数组——**0.4.1 追问机制的预留位**）。
- user 按三模式拼装：`FIRST`（首份：近 30 天明细 + 概要，指令侧重整体模式）/ `DAILY`（昨日明细 + 概要 + 近几天已给建议防重复）/ `BACKFILL`（缺口时段明细合并 + 回顾指令）。
- 明细字段瘦身：只上分析必需字段（餐别/时间/食物/饭量/成分/类型/强度/总量），id 与录入时间不上行。
- `buildSentPayloadText()`：user 内容的美化排版——报告页「本次发送的数据」的数据来源（中文键让用户读得懂）。

## AiReportParser.kt —— 返回正文宽容拆解

剥 ```json 围栏 → 截首尾大括号 → 逐字段宽容读取（缺正文的建议条目跳过而非整体失败）。三段正文全空才判「不可解析」→ 生成器按 parseOk=false 原文保存降级展示。

## AiReportGenerator.kt —— 生成编排器

唯一入口，两个方法：`generateIfDue()`（条件触发：闹钟/自愈/懒检查用，配置齐全且昨日缺失才跑）与 `generateNow()`（手动：无视水位线，覆盖式重生成）。

核心机制：

| 机制 | 做法 | 为什么 |
|---|---|---|
| 成功才记账 | 水位线 `ai_last_report_epoch_day` 只在接口返回内容后推进 | 欠费/断网/超时/格式异常全部自然形成「缺口」，次日合并补交，零专门逻辑 |
| 模式判定 | 无水位线=FIRST；缺 1 天=DAILY；缺多天=BACKFILL | 一条 when 表达式，与提示词三模式一一对应 |
| 合并补交 | BACKFILL 一次请求带整个缺口时段（明细上限 14 天，更早只进概要） | 比逐日补 N 份快，营养分析本就更适合看周期 |
| 单飞锁 | `Mutex` 包住整个生成过程 | 闹钟/自愈/懒检查同时到点只跑一次 |
| 滚动清理 | 每次成功生成后删除 14 天窗口外的旧报告 | 报告是易逝文本，不是账本 |
| 尝试留痕 | 无论成败写 `ai_last_attempt_at`，失败写 `ai_last_error_text` | AI 页状态卡展示「上次为什么失败」 |

注意：**格式异常（parseOk=false）也推进水位线**——接口调用本身成功了，原文已存档，不该因模型偶发不守格式而阻塞次日流程；状态卡会提示「可重新生成」，手动重生成走 REPLACE 覆盖。

## 相关文件（不在本层但强相关）

- `schedule/AiReportPlanner`：三要素齐全时排每日 4:00 闹钟，否则撤销；接收器分支见 [schedule-layer.md](schedule-layer.md)。
- `data/entity/AiReport` + `data/dao/AiReportDao`：报告表（归属日唯一索引，REPLACE 覆盖重生成），见 [data-layer.md](data-layer.md)。
- `ui/screens/AiScreen.kt` + `AiConfigSection.kt`：页面与设置区，见 [ui-layer.md](ui-layer.md)。
- `script/auto-oper/mock-ai-server.py`：本地假接口（模拟成功/401/402/超时/坏格式，按模型名关键字切换），开发与自动化验收用。
