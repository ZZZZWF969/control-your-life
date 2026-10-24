# 代码导读（按分层逐文件讲解）

> 目标读者：想基于本仓库二次开发的人。每份文档讲清楚**每个文件是什么、里面的类与重要函数怎么工作、为什么这样写**。
> 分层总览（谁调用谁）先读 [../architecture.md](../architecture.md)，本文档集是其下钻。
> 约定：写完代码必须同步更新对应文档（项目维护约定），所以这里的描述与代码是一致的。

| 文档 | 覆盖范围 |
|---|---|
| [data-layer.md](data-layer.md) | 数据层 `data/`：表结构实体、DAO 接口、数据库单例与迁移、DataStore 设置仓库 |
| [schedule-layer.md](schedule-layer.md) | 调度层 `schedule/`：requestCode 体系、闹钟封装、到点分发、各排程器、开机自愈 |
| [notify-layer.md](notify-layer.md) | 通知层 `notify/`：渠道定义与全部通知的发送逻辑 |
| [ai-layer.md](ai-layer.md) | AI 协调层 `ai/`（0.4.0）：OpenAI 兼容客户端、概要/提示词/解析、报告生成编排（含提示词定制指引） |
| [ui-layer.md](ui-layer.md) | 界面层 `ui/`：导航容器、六个页面、四个公共组件、主题系统 |
| [util-and-entry.md](util-and-entry.md) | 工具层 `util/`、应用入口与备份协调层 `backup/`：时间工具、时钟跳转、自愈启动、BackupManager |
