# 每日掌控 · control-your-life

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg)]()
[![Language](https://img.shields.io/badge/Kotlin%20%2B%20Jetpack%20Compose-2.3-7F52FF.svg)]()

把手机变成掌控生活的工具：记录饮食与运动，到点提醒三餐、背单词与次日安排，用「系统闹钟 + 应用震动」双保险把你从床上叫起来。

> 仓库主人大学专业为集成电路（IC），并非计算机科班出身；本工程从零到一纯 vibe coding 完成，存在诸多不专业之处，虚心接受批评和建议。

一款个人生活管理 Android 应用——数据全部存在本地，无账号、无云同步、不追踪。

## 功能

### 已实现（v0.3.0）

- **饮食与营养记录**：三餐、零食、营养品按日记档；营养品支持「成分 + 含量」结构化录入（如复合维生素的 VA/VB/VC/VD 各行），编辑、删除、按日回顾
- **运动记录**：类型快选与自定义、起止时间、时长自动计算、强度与总量
- **三餐询问**：饭点后主动询问「这餐记了吗」，已记录则静默；点击通知直达该餐预填表单
- **背单词循环提醒**：10:00–23:00 每 90 分钟锚点提醒，直到你在应用内确认「今日已背」；通知栏一键跳转背单词应用（不背单词/百词斩等可配置）
- **晚间规划**：22:00 提醒规划明天（23:00 未规划二次提醒）；待办支持开始时间与提醒提前量，当日也可安排当日
- **起床双保险**：规划早八/早十后，引导你在系统时钟开一个闹钟做叫醒主力（应用只读跳转、零积累），应用在同时刻发渐进震动做保底——末声持续震动直到处理
- **提醒可靠性**：精确闹钟、开机自动恢复全部提醒、划掉后台不影响；深链通知直达对应页面
- **双主题**：浅色/深色/跟随系统
- **滚轮时间选择**：全应用统一的双列滚轮（时/分逐分钟、惯性吸附、循环滚动）

### 规划中

- AI 生活建议（OpenAI 兼容接口，每日汇总分析，凌晨生成报告）—— 第四阶段
- 包装食品 NRV 营养成分表录入
- 药箱管理（效期、余量、按用途检索）
- 睡眠记录 / 运动自动同步（Health Connect 通道）

## 界面

| 今日概览 | 记录与滚轮时间 |
|:---:|:---:|
| ![今日页](docs/screenshots/readme/home-today.png) | ![记录页](docs/screenshots/readme/record.png) |

| 滚轮时间选择 | 起床双保险引导 |
|:---:|:---:|
| ![滚轮](docs/screenshots/readme/time-wheel.png) | ![双保险](docs/screenshots/readme/wake-guide.png) |

## 构建与安装

环境要求：Android Studio、JDK 17、Android SDK（compileSdk 37，minSdk 35，即 Android 15+ 设备）。

```
# 命令行构建 debug APK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或直接用 Android Studio 打开工程，Sync 后 Run。

> 提醒功能在小米 HyperOS 等定制系统上需要一次性权限设置（自启动、电池无限制、精确闹钟、按渠道开启锁屏通知），详见 [docs/testing/phase4-device-test-guide.md](docs/testing/phase4-device-test-guide.md)。

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
