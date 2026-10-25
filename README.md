# 每日掌控 · control-your-life

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg)]()
[![Language](https://img.shields.io/badge/Kotlin%20%2B%20Jetpack%20Compose-2.3-7F52FF.svg)]()

把手机变成掌控生活的工具：记录饮食与运动，到点提醒三餐、背单词与次日安排，用「系统闹钟 + 应用震动」双保险把你从床上叫起来。

> 仓库主人大学专业为集成电路（IC），并非计算机科班出身；本工程从零到一纯 vibe coding 完成，存在诸多不专业之处，虚心接受批评和建议。

一款个人生活管理 Android 应用——数据全部存在本地，无账号、无云同步、不追踪。唯一的网络出口是你自己配置的 AI 接口（仅用于每日汇总生成建议报告）。数据存哪、怎么备份带走见 [docs/data-storage.md](docs/data-storage.md)（应用内置一键导出/恢复与每周自动备份）。

## 最近更新

### v0.4.0（2026-10-24）· AI 生活建议上线

**新增**

- **AI 每日营养报告**：每天凌晨 4:00 自动汇总前一天的饮食（含营养品成分明细）与运动，早晨无声通知查看；每条建议一行**行动摘要**（如「餐后吃橙子猕猴桃补维C」），点开才看执行细节与依据（你的数据事实 + 膳食指南推荐量）；涉及营养缺口时同时给食物来源与营养品选择（剂型、参考剂量，以产品说明或医嘱为准）
- **接口你说了算**：OpenAI 兼容三要素自己填（智谱 / DeepSeek / OpenAI 等通用），「测试连接」一键验证；欠费、断网都不怕——恢复后自动补交缺失日期的「回顾报告」
- **隐私可亲手核验**：API Key 仅存本机（卸载即清除）、不随备份导出、只发往你填写的这一个地址；每份报告都能展开「本次发送的数据」逐字查看发了什么

**修复**

- 无（本版为纯新增功能版本）

> 0.4 系列各版本的完整更新与开发记录：[docs/release-notes/0.4.md](docs/release-notes/0.4.md) · 全部历史版本：[CHANGELOG.md](CHANGELOG.md) · 第一次使用？看[新手上手指南](docs/getting-started.md)

## 接下来的计划

- **AI 追问机制（0.4.1）**：AI 可就不完整的信息（身高体重、饮食目标等）主动向你提问，回答随次日数据一并提交，不阻塞当日报告——接口已在 0.4.0 预留
- 报告质量持续调优（提示词可按文档定制，无需改代码结构）

> 更多待办与远景规划见 [docs/roadmap.md](docs/roadmap.md)。

## 界面

| 今日概览 | 记录与滚轮时间 |
|:---:|:---:|
| ![今日页](docs/screenshots/readme/home-today.png) | ![记录页](docs/screenshots/readme/record.png) |

| 滚轮时间选择 | 起床双保险引导 |
|:---:|:---:|
| ![滚轮](docs/screenshots/readme/time-wheel.png) | ![双保险](docs/screenshots/readme/wake-guide.png) |

| AI 每日报告：行动摘要一屏可扫 | 展开详情：执行细节 + 依据 |
|:---:|:---:|
| ![AI报告](docs/screenshots/readme/ai-report.png) | ![展开详情](docs/screenshots/readme/ai-report-expanded.png) |

## 构建与安装

| 方式 | 适合谁 |
|---|---|
| Android Studio 打开运行 | 想看代码、改代码的开发者（最省事） |
| 命令行 Gradle 构建 + adb 安装 | 熟悉终端的开发者 |
| 直接安装 APK | 只想用的普通用户（正式版将挂 GitHub Releases） |

> 每种方式的逐步操作、每一步的预期输出、可预见的非预期行为与解决办法，见 **[docs/build-and-install.md](docs/build-and-install.md)**；装好后怎么开始用，看[新手上手指南](docs/getting-started.md)。想省事：双击仓库里的 `script/check-env.bat` 一键自检环境，双击 `script/build-app.bat` 一键构建出 APK。设备要求：Android 15 及以上。

## 技术栈与工程

- Kotlin + Jetpack Compose + Material 3；单模块、无第三方 UI 依赖
- Room（数据库，显式迁移）+ DataStore（轻量设置）
- AlarmManager 精确闹钟 + 通知深链导航

工程分层与模块关系见 [docs/architecture.md](docs/architecture.md)，更多文档索引见 [docs/README.md](docs/README.md)。项目按阶段开发，阶段报告存于 [docs/reports/](docs/reports/)，更新日志见 [CHANGELOG.md](CHANGELOG.md)。

## 开发模型

本工程由仓库主人与 AI 结对完成（vibe coding）。使用过的模型：

- **GLM-5.3** —— 截至目前唯一在此署名的模型，本工程的全部代码与文档均与其协作完成

## 许可

[MIT](LICENSE)
