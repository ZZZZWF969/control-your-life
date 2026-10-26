# 数据层导读（`data/`）

数据层只负责「存与取」，不知道界面长什么样。两条通道：

- **Room 数据库**（重数据）：饮食、运动、待办等业务记录，存 SQLite 文件
- **DataStore**（轻量设置）：主题、提醒时刻、背单词状态等键值配置

## 目录结构

```
data/
├── entity/   表结构与值对象（Room 按注解生成建表 SQL）
├── dao/      数据访问接口（只写声明，Room 编译期生成实现）
├── db/       数据库单例 + 版本迁移
└── settings/ DataStore 设置仓库与主题枚举
```

## entity/ —— 表结构与值对象

### DietRecord.kt（`diet_record` 表）

一行 = 一次进食（正餐 / 零食 / 营养品）。字段带注释说明用途，两点设计：

- `mealType` 存字符串而不是枚举：数据库层保持「哑存储」，枚举转换在界面层做（见 MealType）
- `weightText` / `nutrientText` 可空：只有零食/营养品才填；`nutrientText` 是旧版自由文本字段，结构化后被 nutrient_item 子表取代，保留作兼容展示

### NutrientItem.kt（`nutrient_item` 表）

营养品的「成分 + 含量」明细行（如 `VA 800μg`），挂在某条饮食记录下：

- **外键 + `onDelete = CASCADE`**：删除所属饮食记录时数据库自动删掉明细行，不会产生孤儿数据
- **`indices = [Index("dietRecordId")]`**：外键列建索引，Room 的关系查询和级联删除都靠它提速

### DietRecordWithItems.kt（组合查询结果，非表）

Room 的 `@Relation` 用法：一次查询同时拿到「记录 + 它的全部明细行」。列表页卡片需要这两种数据，若分开查会闪烁，这个组合类是性能与简洁的折中。

### ExerciseRecord.kt（`exercise_record` 表）

一行 = 一次运动。起止时间存 epoch 毫秒，**按开始时间归日**（跨天运动算在开始那天）。时长不存库，展示时用 `endAt - startAt` 现算——派生值不落库，避免数据不一致。

### TodoTask.kt（`todo_task` 表）

晚间规划 / 当日加记的待办：

- `taskDateEpochDay`：任务归属日，存 `LocalDate.toEpochDay()`（一个长整数），比存日期字符串省心且好排序
- `startAtMillis` 可空：null 表示「只记录不提醒」——待办分两种语义
- `remindAheadMinutes`：提醒提前量，**提醒时刻 = startAtMillis − 提前分钟数**，这个换算在调度层用

### AiReport.kt（`ai_report` 表，0.4.0）

一行 = 一次成功生成的 AI 报告（归属日唯一索引，同日重生成整行 REPLACE）：

- `reportDateEpochDay` + `kind`（first/daily/backfill）+ `generatedAt`：归属、模式、时刻
- 结构化字段（summary / dietAnalysis / exerciseAnalysis / suggestionsJson / questionsJson）供 AI 页拆板块渲染；`questionsJson` 为 AI 追问列表 `[{topic,text,type}]`（0.4.1 启用，由解析层归一化）
- `parseOk` + `rawResponse` + `sentPayload`：格式异常降级开关、AI 原文（排查兜底）、本次发送数据全文（隐私自查入口的数据来源）——原文永远留档，数据永不丢
- `answersJson`（0.4.1，DB v6 加列，默认 `[]`）：追问答案 `[{question,type,answer,answeredAt,submittedEpochDay}]`——问题文本冗余存储使作答与提交自包含；重生成同日报告时由生成器按问题文本匹配保留

### MealType.kt（枚举，非表）

五个餐别。每个枚举值带两个属性：`storageName`（数据库里的存储值）和 `labelRes`（界面文案资源 id）——**存储与展示解耦**，加一种餐别只需加一行枚举加一条字符串。`fromStorage()` 做还原，认不出来时降级为零食（健壮性：坏数据不崩溃）。

## dao/ —— 数据访问接口

DAO（Data Access Object）只声明方法签名 + SQL 注解，实现由 Room 在编译期生成——**拼错列名是编译错误而不是运行时崩溃**，这是选 Room 的核心理由。

所有查询方法返回 `Flow`（挂起函数返回单值）：界面订阅 Flow 后，**数据库一变界面自动刷新**，不需要手动「刷新列表」。

### DietRecordDao.kt

- 常规 insert/update/delete/count
- `countBetween(mealType, start, end)`：数某天某餐别有几条记录——**三餐询问闹钟到点时靠它判断「这餐记了没」**，记了就静默
- `observeBetween(start, end)`：带 `@Transaction` 的组合查询（记录 + 营养素明细），`@Transaction` 保证主表与子表数据一致地读到
- `getBetweenOnce(start, end)`（0.4.0）：同区间的一次性读取（挂起返回单值），AI 汇总组装提示词用——不需要响应式刷新

### NutrientItemDao.kt

只有两个方法，因为明细行走「**整体替换**」策略：编辑保存时先 `deleteByRecordId` 清空旧行再 `insertAll` 写入新行，不做逐行 diff——数据量小，替换比对比简单可靠得多。

### ExerciseRecordDao.kt

结构与饮食 DAO 同构，只是少了明细子表。0.4.0 同样新增 `getBetweenOnce` 供 AI 汇总用。

### AiReportDao.kt（0.4.0）

- `insert` 用 **REPLACE 冲突策略**：同归属日重生成 = 整行替换，靠唯一索引兜底
- `observeRecent(fromEpochDay)`：只取保留窗内的报告（新→旧），AI 页列表订阅
- `recentParsed(count)`：最近几份「解析成功」的报告——提示词的「近几天已给建议勿重复」段落数据源
- `deleteOlderThan(beforeEpochDay)`：14 天滚动清理，每次成功生成后调用
- `getAllOnce` / `updateAnswers`（0.4.1）：生成前收集已答未提交的追问答案；界面作答只更新答案列

### TodoTaskDao.kt

为规划页与调度层定制了几个特殊查询：

- `deleteUndoneOfDate(epochDay)`：删某日全部**未完成**任务。规划页保存 = 整体替换草稿，但已完成的不许动（勾掉的不能因为重新规划而复活）
- `observeByDate`：`ORDER BY startAtMillis IS NULL, startAtMillis ASC`——SQLite 里 `IS NULL` 判断产生 0/1，这句 SQL 的意思是**有时间的排前面按时间升序，没时间的垫底**，一行搞定双规则排序
- `pendingReminders(now)`：找出「未完成 + 有时间 + 提醒时刻还没过去」的任务——**重启自愈**时靠它重排闹钟
- `getByReminderRequest(taskId)`：闹钟到点后反查任务（requestCode 减去基址就是任务 id）

## db/DailyControlDatabase.kt —— 单例与迁移

- `@Database(... version = 6, exportSchema = true)`：版本号每次改表结构必须 +1，同时**必须**补一段 `Migration`，否则老用户升级直接崩（Room 校验版本差没有迁移路径就抛异常）。`exportSchema = true` 会把每版表结构快照存进 `app/schemas/`，升级时可 diff 对比
- 五段迁移（1→2 加列、2→3 建子表、3→4 建待办表、4→5 建 ai_report 报告表、5→6 报告表加 answersJson 列）都直接写 SQL——迁移 SQL 必须与 Room 期望的表结构**严格一致**（含外键、索引、NOT NULL），否则运行时校验失败
- `get(context)` 是双重检查锁定单例：全应用共享一个数据库连接。`@Volatile` 保证多线程读到的是已构造完成的对象

## settings/ —— DataStore 设置仓库

### ThemeMode.kt

三档主题枚举，结构与 MealType 同构（storageName + labelRes + fromStorage 兜底）。

### SettingsRepository.kt

**一条配置一个 Flow + 一个挂起 setter**，统一从 `context.dataStore` 读写。要点：

- DataStore 实例用**顶层 `by preferencesDataStore` 委托**挂在 Context 上——这是官方保证全局唯一的方式，自己 new 会崩溃
- 存储的键集中在 companion object 的 `KEY_*` 常量，读写永不散落字符串
- 各配置的语义（注释里都写了）：
	- `themeMode`：主题
	- `reminderTimes`：三餐询问与晚间规划的时刻，存「当日分钟数」（570 = 9:30），换算在界面层
	- `vocabPackage`：背单词应用包名，空串 = 未配置
	- `vocabDoneDate`：最近一次「今日已背」的日期，与今天比对即得完成状态——**按日期过期，不需要定时任务重置**
	- `lastPlannedDate`：最近一次保存规划的日期，22:00/23:00 提醒据此判断当晚是否已规划
	- `wakePlanDate` + `wakePlanTimes`：起床唤醒计划存档（目标日期 + 各声时刻），**开机自愈的依据**；空日期表示无计划
	- `wakeClockConfirmedDate`：用户自记「时钟闹钟已开」的日期（应用读不到系统时钟，见 schedule 层的 WakePlanner）
	- AI 七键（0.4.0~0.4.1）：三要素 `aiBaseUrl` / `aiApiKey` / `aiModel`（都非空 = 功能启用），水位线 `aiLastReportEpochDay`（0 = 从未生成；**成功才推进**，详见 ai-layer.md），运行痕迹 `aiLastErrorText` / `aiLastAttemptAt`，个人档案 `aiProfileJson`（0.4.1：档案型追问答案的长期存档 `[{topic,question,answer,updatedAt}]`，按 topic 覆盖）
	- **AI 各键全部刻意不入 backupSettings/restoreSettings**：Key 属敏感信息不随备份导出（安全红线）；水位线/档案/错误属运行痕迹，恢复后按新环境重建
