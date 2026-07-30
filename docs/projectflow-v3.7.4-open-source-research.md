# ProjectFlow V3.7.4 Open-source Research

调研日期：2026-07-29

## 采用映射

| 来源 | License | 分类 | 决策 |
| --- | --- | --- | --- |
| openai/openai-java | Apache-2.0 | ADAPTER_INTEGRATION | 继续复用项目已有官方 Java SDK 承载 Responses 与 Chat Completions；重试仍由 ProjectFlow 统一关闭和管理。 |
| modelcontextprotocol/modelcontextprotocol | MIT | PROTOCOL_REUSE | 复用 Tools、Resources、stdio 凭据来自环境、URI 不携带 token 的协议与授权边界。 |
| DietrichGebert/ponytail | MIT | PATTERN_REUSE | 只采用“先删除/复用/单点 helper，最后才新增抽象”的工程决策顺序，不复制运行时代码或形成产品依赖。 |
| FasterXML/jackson-core/databind | Apache-2.0 | DIRECT_REUSE | 项目已通过 Spring Boot 使用 Jackson；结构化 JSON 使用现有 streaming/tree 能力，不新增解析依赖。 |
| commonmark/commonmark-java | BSD-2-Clause | REFERENCE_ONLY | Markdown AST 和 heading 支持成熟，但当前只需有界标题目录；不为单一能力增加依赖。 |
| tree-sitter/tree-sitter | MIT | REFERENCE_ONLY | 适合精确语法树，但 ProjectFlow 已规定 precise symbol 走 SCIP；不引入 grammar/runtime 管理。 |
| apache/lucene | Apache-2.0 | REJECT | 全文索引能力成熟，但本阶段只需单次有界定位；引入索引生命周期和磁盘状态会扩张为搜索平台。 |
| microsoft/vscode / vscode-textbuffer | MIT | REJECT | Piece tree 适合交互编辑，不适合只读证据扫描；不复制编辑器缓冲层。 |
| ben-manes/caffeine | Apache-2.0 | REFERENCE_ONLY | 有界缓存成熟；当前现有 revision-key ConcurrentHashMap 足够，达到跨进程缓存需求前不新增依赖。 |
| Java 17 NIO FileChannel/BufferedReader | JDK | DIRECT_REUSE | 用标准库完成流式行扫描、哈希和固定位置采样，避免整文件入模和新 runtime。 |

## 工程结论

- Content Map 是有界定位层，不是 parser、全文索引或语义事实层。
- Markdown heading、TODO/FIXME、decision/deprecation/currentness 只作为词法锚点；精确 Definition/Reference 仍由 SCIP 提供。
- 8 万行 fixture 在测试时生成。单次扫描只保留有界 heading、marker、重复区域统计和样本文本。
- 超过读取上限时返回 PARTIAL、unread ranges 和 limitation，不把未读内容当作不存在。
- 多项目访问复用现有 authenticated userId 和 owned projectId；MCP stdio token 继续只从环境变量读取。
- 没有新增第三方依赖，因此当前无需修改 THIRD_PARTY_NOTICES。

## 兼容性与风险核验

| 方案 | 维护/安全 | Java 17 / Windows | 内存与大文件 | 依赖结论 |
| --- | --- | --- | --- | --- |
| openai-java | 官方仓库持续发布，含 Security Policy | 支持 Java 8+，现有 Java 17/Windows 路径已验证 | 只处理有界模型请求，不承担文件扫描 | 保留现有 SDK，不新增 |
| MCP specification | 官方规范持续维护，明确最小权限和输入校验责任 | 协议与语言无关；现有 Python stdio 在 Windows 已验证 | MCP 只返回紧凑分页和 Context Package | 复用协议，不引入新 SDK |
| Jackson | 成熟维护，Spring Boot 已统一版本 | Java 17/Windows 已由全套测试覆盖 | 大 JSON/YAML 只做有界文本与既有 JSON streaming，不整库建索引 | 直接复用 |
| CommonMark / Tree-sitter | 许可证可用且维护活跃 | 可支持目标平台 | 会增加 AST/grammar 生命周期和常驻对象 | 当前只参考，不引入 |
| Lucene / VS Code text buffer | 成熟但能力面远超只读定位 | 可运行但部署和状态管理更重 | 引入索引或编辑缓冲生命周期 | 拒绝 |
| Java NIO | JDK 安全更新链 | Java 17 与 Windows 原生可用 | 单次流式扫描、固定上限集合和按 revision 缓存 | 当前实现 |

## 参考

- https://github.com/modelcontextprotocol/modelcontextprotocol
- https://github.com/openai/openai-java
- https://github.com/DietrichGebert/ponytail
- https://github.com/FasterXML/jackson
- https://github.com/commonmark/commonmark-java
- https://github.com/tree-sitter/tree-sitter
- https://github.com/apache/lucene
- https://github.com/microsoft/vscode
- https://github.com/ben-manes/caffeine
