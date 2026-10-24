# 文档索引

> 本目录收纳工程文档；根目录的 `goal.md`（需求、已确认决策与跨阶段 TODO）是功能演进的总依据。

## 顶层文档（常翻）

| 文档 | 内容 | 什么时候看 |
|---|---|---|
| [architecture.md](architecture.md) | 工程分层、模块与调用关系的大白话说明 | 想知道「代码怎么组织的、改哪里会影响哪里」；每次结构性变化后同步更新 |
| [env-pitfalls.md](env-pitfalls.md) | 环境陷阱清单：模拟器、真机 HyperOS、工具链踩坑记录，含症状与规避办法 | 交付验收前必须核对；遇到诡异问题先来这里搜，踩到新坑必须追加 |
| [versioning.md](versioning.md) | 版本号方案与发布流程 | 每次批次提交/阶段收官打 tag 前 |
| [data-storage.md](data-storage.md) | 数据存储与备份策略：数据位置、备份/恢复机制、换机与升级路径 | 用户问「数据存哪/怎么带走」时；改备份相关代码前 |

## 使用者文档（面向访客与最终用户）

| 文档 | 内容 | 什么时候看 |
|---|---|---|
| [getting-started.md](getting-started.md) | 新手上手指南：权限向导、记第一笔、提醒体系、规划与起床、AI 报告、备份 | 第一次安装使用 |
| [build-and-install.md](build-and-install.md) | 构建与安装详解：三种方式的逐步操作、预期输出与非预期排查 | 构建/安装遇到问题时 |
| [release-notes/](release-notes/) | 各中版本（0.4、0.5 …）的版本记录：用户可见变化 + 开发记录 | 想了解某版本改了什么 |
| [roadmap.md](roadmap.md) | 待办与远景规划：下一步 / 待办池 / 远景 | 想知道接下来会做什么 |

## code/ —— 代码导读（按分层逐文件讲解）

面向二次开发者的下钻文档：每个文件是什么、重要类与函数的编码逻辑、踩过的坑。

| 文档 | 覆盖 |
|---|---|
| [code/data-layer.md](code/data-layer.md) | 数据层：表结构、DAO、数据库迁移、DataStore 设置 |
| [code/schedule-layer.md](code/schedule-layer.md) | 调度层：requestCode 体系、闹钟封装、到点分发、各排程器 |
| [code/notify-layer.md](code/notify-layer.md) | 通知层：渠道定义与发送逻辑、深链、持续震动 |
| [code/ai-layer.md](code/ai-layer.md) | AI 协调层（0.4.0）：OpenAI 兼容客户端、概要/提示词/解析、报告生成编排（含提示词定制指引） |
| [code/ui-layer.md](code/ui-layer.md) | 界面层：导航、六页面、滚轮选择器、主题 |
| [code/util-and-entry.md](code/util-and-entry.md) | 工具层、应用入口与备份协调层：时间工具、时钟跳转、自愈启动、BackupManager |

## reports/ —— 阶段验收报告

每阶段完成时归档一份：构建结果、实测证据汇总、遗留问题。

- [phase1-report.md](reports/phase1-report.md) 第一阶段：工程骨架
- [phase2-report.md](reports/phase2-report.md) 第二阶段：饮食与运动记录
- [phase3-report.md](reports/phase3-report.md) 第三阶段：提醒系统
- [phase4-report.md](reports/phase4-report.md) 第四阶段第一批（0.4.0）：AI 接口接入与每日报告

## testing/ —— 测试手册

写给项目所有者的分步操作手册，含权限设置清单、每步的预期结果。

- [phase4-device-test-guide.md](testing/phase4-device-test-guide.md) 第三阶段真机六项实测手册（红米 HyperOS 有线连接）
- [0.4-device-test-guide.md](testing/0.4-device-test-guide.md) 0.4.0 AI 功能真机实测手册（接口配置、报告生成、隐私自查、失败恢复、过夜验收）

## screenshots/ —— 实测取证（本地产物，不入库）

按阶段分子目录（phase1 ~ phase4）存放截图与录屏证据；已被 .gitignore 排除，仅存于开发机本地备查，报告中的结论以文字为准。
例外：`readme/` 子目录是仓库门面截图（README 引用），随仓库入库。

## 新增文档的约定

- 阶段报告：`reports/phaseN-report.md`
- 测试手册：`testing/phaseN-主题-guide.md`
- 专题文档（如未来的数据备份方案、发布流程）：放顶层单文件或按主题建子目录，建好后在本文索引里登记一行
