# ProjectFlow V3.7.4 Minimal Scope Proposal

状态：实施中。

## 最小闭环

1. 在现有 ProjectFact 上增加兼容的 epistemic contract 与 Promotion Guard；RECORDED 强事实只允许 OBSERVED/VERIFIED。
2. 以一个流式 LargeFileContentService 建立 Content Map、HEAD/MIDDLE/TAIL/heading/marker 采样、范围元数据、哈希、partial coverage 与 unread ranges。
3. 让 Evidence Discovery 和现有 DOC_READER 复用该服务，不增加自由路径或 shell 能力，不持久化完整文档。
4. 在现有 Project Memory 边界上增加跨项目搜索、Evidence 读取、知识状态读取和版本化 Context Package。
5. 新增独立 Agent candidate 表和受控 POST；它只能写候选/过程层，不能创建或修改 ProjectFact。
6. 扩展 Hermes 的官方 MCP Tool/Resource 表面，不建设 Agent Manager GUI。
7. 复用 V3.7.3 Prompt Builder，升级同一事实宪法和版本，不为第二模型建立另一套规则。
8. 通过生成器产生 8 万行本地 fixture；GitHub 只保存规范、hash、轻量结果和复现命令。

## 明确延后

- GitHub PR/Issue/Release/Workflow 的完整 Provider 接入只保留正式 read-only contract 和后续项。
- 精确源码 Symbol 继续只来自 SCIP；轻量 Content Map 的词法锚点不伪装为精确关系。
- 不建设 V3.8 叙事、完整项目生命、里程碑故事、远程写、PDF/OCR、全文搜索引擎或向量检索。

## 退出标准

确定性测试必须证明中部/尾部可读、未读范围披露、非法事实升级为 0、跨项目隔离、候选写边界、Context Package 来源和旧 API 兼容。真实模型、Holdout、产品 E2E、CI 与最终 Gate 只按实际结果回填。

