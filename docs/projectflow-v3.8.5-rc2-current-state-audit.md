# ProjectFlow V3.8.5 RC2 当前状态审计

审计日期：2026-08-08。审计对象为 Draft PR #15，基线 master 为 5cb5e49661206feb8f59885bea672c314c9374e8，RC2 首个修复提交为 04fa00062d4243d67d7d47dcca5f351e1bdfa671。

接管时保留的失败事实：GLM 19-case 资格 FAIL；DeepSeek 19-case 资格 FAIL；DeepSeek 真实场景 10/11，ProjectFlow Dogfood 因 Primary/Supporting 引用不一致失败；GLM 完整真实场景 NOT_RUN；人工 Story/Chapter 评分 0/0；旧 Quality Gates 的 Obsidian job 失败。

根因与修复：模型旧合同同时承担文字、角色关系和 Chapter 成员，窗口级输出无法稳定维护全局角色图。RC2 将 role、primaryStoryId、supportingChangeRefs、Chapter storyRefs、before/change/after 和 Evidence 归属收回工程层，模型只返回 Story/Chapter 措辞与有 Evidence 的 reason。Obsidian 旧红灯来自测试按文件系统顺序读取任意 Chapter；生产投影本身的 split 归属正确，测试改为按稳定内容定位，并新增 split/merge、双向关系和 revision 漂移校验。

本地确定性结果：后端 H2 546 项通过、0 失败、5 个条件跳过；PostgreSQL 16 Testcontainers 5 项通过，failsafe 11 项中 6 个外部真实依赖条件跳过；前端契约 58/58、Playwright 9/9、生产构建通过；Hermes 10/10、Obsidian 25/25；敏感扫描通过。根 `Start-ProjectFlow.bat -NoBrowser` 实际重建并确认前后端就绪，`logs/last-embedded-build.json` 已记录当前 revision 和 build ID。

依赖风险：只读 npm audit 为 4 high、0 critical，涉及 Next/PostCSS 与传递依赖 nanoid/sharp；本轮没有执行可能改变依赖图的 audit fix。

当前阻断：workflow 31264440534 的真实模型 job 因两项 GitHub Secrets 未配置而在请求前失败，没有模型请求或计费；真实双 Provider 新结果和人工 30 Story/8 Chapter 评分尚未产生。PR 必须保持 Draft。
