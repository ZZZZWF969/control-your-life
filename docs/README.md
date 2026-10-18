# 文档索引

> 本目录收纳除根目录两份纲领文档外的全部工程文档。
> 纲领文档在根目录：`AGENTS.md`（协作规矩）、`goal.md`（需求、已确认决策与跨阶段 TODO）——它们是所有工作的依据，不动。

## 顶层两份（常翻）

| 文档 | 内容 | 什么时候看 |
|---|---|---|
| [architecture.md](architecture.md) | 工程分层、模块与调用关系的大白话说明 | 想知道「代码怎么组织的、改哪里会影响哪里」；每次结构性变化后同步更新 |
| [env-pitfalls.md](env-pitfalls.md) | 环境陷阱清单：模拟器、真机 HyperOS、工具链踩坑记录，含症状与规避办法 | 交付验收前必须核对；遇到诡异问题先来这里搜，踩到新坑必须追加 |

## reports/ —— 阶段验收报告

每阶段完成时归档一份：构建结果、实测证据汇总、遗留问题。

- [phase1-report.md](reports/phase1-report.md) 第一阶段：工程骨架
- [phase2-report.md](reports/phase2-report.md) 第二阶段：饮食与运动记录
- phase3（提醒系统）撰写中，完成后归入此处

## testing/ —— 测试手册

写给项目所有者的分步操作手册，含权限设置清单、每步的预期结果。

- [phase4-device-test-guide.md](testing/phase4-device-test-guide.md) 第三阶段真机六项实测手册（红米 HyperOS 有线连接）

## screenshots/ —— 实测取证（本地产物，不入库）

按阶段分子目录（phase1 ~ phase4）存放截图与录屏证据；已被 .gitignore 排除，仅存于开发机本地备查，报告中的结论以文字为准。

## 新增文档的约定

- 阶段报告：`reports/phaseN-report.md`
- 测试手册：`testing/phaseN-主题-guide.md`
- 专题文档（如未来的数据备份方案、发布流程）：放顶层单文件或按主题建子目录，建好后在本文索引里登记一行
