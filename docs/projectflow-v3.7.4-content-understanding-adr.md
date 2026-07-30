# ADR: Content-first Evidence Discovery

状态：Accepted for V3.7.4。

文件名只参与安全策略、定位、初始类别和多样性配额。重要性、可信度、当前性和事实等级由正文信号、工程元数据与交叉 Evidence 决定。

无扩展名文本、中文或临时命名、深目录文档和 agent-output 文本只要通过 text/binary 与敏感检查，都可进入 UNKNOWN_DOCUMENT 或 AGENT_RESULT 候选。README、ARCHITECTURE 和 FINAL_DESIGN 也只获得候选身份，不自动获得 HIGH、CURRENT 或 VERIFIED。

Discovery 对大文本生成 HEAD/MIDDLE/TAIL 代表样本和 Content Map 摘要；Scout 仍只请求 capability 与 Evidence ID。Provider 从 allow-list Evidence 解析固定相对路径，不接受模型绝对路径、shell 或任意参数。

