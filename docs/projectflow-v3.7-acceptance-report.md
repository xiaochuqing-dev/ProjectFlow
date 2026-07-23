# ProjectFlow V3.7 最终验收报告

验收日期：2026-07-24

当前结论：本地实现和完整本地门禁通过；远程 CI 状态将在提交后按实际结果更新。

## A–H：完成内容与项目形态

A. 完成 Evidence Source Map、bounded UTF-8 sample/redaction、Semantic Scout、capability registry、Adaptive Plan、Dynamic Profile、Historical Coverage、Evolution Preview、Metrics、API 类型和最小 UI。

B. 支持 open-world input 的生产最小闭环，但 PDF/Office 深读、remote forge 和真实模型质量仍有限。

C. 空目录：EMPTY、0 model、0 Section、无 Timeline。

D. 单 TXT：空白时 EMPTY_CONTENT/0 model；有内容时进入 bounded Scout，不生成代码架构。

E. 小脚本：只默认 Purpose/Input/Output/Dependencies/Usage，避免多层架构。

F. 纯前端：允许 routes/components/state/API 等模型证明的动态维度，不默认 Backend/Database。

G. 纯后端：允许 API/service/data/auth/integration 等动态维度，不默认 Frontend。

H. Desktop/Fullstack/Monorepo：shape 为多假设；workspace 和规模触发 hierarchical plan，不固定成 Web 模板。

## I–L：历史和材料

I. 无 Git：当前 Profile 可用，Historical Coverage unavailable/limited，Evolution=current-state-only。

J. 长历史：bounded Git period sample + Tag/Fact/结构锚点，最多 15 milestone candidates，不逐 commit LLM。

K. 奇怪命名文档：按扩展名/大小进入 UNKNOWN_DOCUMENT candidate，读取少量内容后由 Scout 判断语义角色。

L. README：只作为 README candidate；Scout 可标 possibly stale/conflict，claims 仍需 evidence ID。

## M–Q：智能调度

M. Semantic Scout 判断 shape hypothesis、source role、importance/currentness、applicable dimensions、tool needs、unknown/conflict 和动态 sections。

N. Planner 合并 Scout 与 deterministic guardrails，输出工具、预算、deep-read、适用/跳过维度和 history/structure strategy。

O. Guardrails：文件存在、安全路径、generated/vendor、credential redaction、Git/SCIP availability、tool allow-list、evidence ID、size/token/cache。

P. LLM：语义角色、混合形态、适用维度、当前性、冲突和 evidence-backed interpretation。

Q. 防止 LLM 接管：一次注册 Model Gateway 请求；不提供 shell；tool registry 校验；不让模型决定关系、Git、coverage 或事实写入。

## R–V：预算、Evidence 和 Profile

R. 单 sample 8 KiB/1,600 chars，最多 80 Scout evidence、48k prompt chars、1 model request、15 evolution windows；空/blank/unchanged 0 model。

S. 100k/1M LOC 真实性能见 product acceptance。235k LOC 17.18s；3.55M LOC 134.37s；均只 deep-read 80、0 model。

T. Source Map 使用稳定 evidence ID、category、candidate type、relative locator、role/currentness/importance/confidence/deep-read/summary；sample 不返回 API。

U. Dynamic Profile 是 ordered section list，允许 0 Architecture/Backend/Database/Timeline。

V. Historical Coverage 包含 Git/Fact/Tag、evidence range、period/gap、overall coverage 和 limitations。

## W–AB：Timeline、Evolution、SCIP、开源和 Provider

W. 有真实历史才展示 Evolution；无 Git/current-only 不显示 Timeline；3 commits 只显示 early project。

X. 完成 coverage-sized Evolution strategy 与既有 bridge preview；完整多 revision architecture evolution 未完成。

Y. SCIP consumer/fallback 保持生产可用；自动 producer 经调研后 DEFERRED，未静默安装 runtime。

Z. 直接复用 SCIP、JGraphT、Git CLI、Model Gateway、Durable Job；模式复用 Aider、PyDriller、CodeBoarding、DeepWiki、CodeScene。

AA. 明确不造 Parser、grammar、Symbol protocol、Git、PageRank、全文引擎、vector DB、RAG、LSP、Agent runtime、workflow、daemon。

AB. 环境无安全 Key，真实 Provider：SKIPPED。

## AC–AH：门禁、结论和交付

AC. Docker server 当前不可用，本地 PostgreSQL Testcontainers：SKIPPED；没有把 H2 描述成 PostgreSQL。GitHub Actions Run 30034572176 的 PostgreSQL Testcontainers 真实集成测试已通过。

AD. 已通过 Maven 完整 H2 套件 329 项（0 failure、0 error、1 条条件跳过）、V3.7 聚焦测试、前端 lint、48/48 contracts、Next.js production build（23 个静态页面）和真实前后端 Playwright 8/8。仓库根 `Start-ProjectFlow.bat -NoBrowser` 已从当前工作树完成依赖校验、生产构建、Java 17 后端启动、H2 旧库读取和前后端健康检查；`logs/last-embedded-build.json` 记录 version=3.7.0、frontendBuildId=yMTbsys-r775niT2LOpRU、hasLocalChanges=true。

AE. 产品人工验收：CONDITIONAL PASS。诚实性、动态 Profile 和 token 边界通过；真实语义质量和首次超大仓性能仍有风险。

AF. 风险：真实模型未验收；SCIP producer 未自动化；非 UTF-8/PDF/Office 不深读；large/huge 首扫慢；Evolution 尚非完整历史引擎；`npm audit --omit=dev` 仍报告现有 Next.js 传递依赖的 3 个 high advisory，当前没有非破坏性的兼容修复建议。

AG. 下一阶段优先优化 Discovery IO/cache、真实 Provider 语义验收和安全 producer PoC，再决定是否深化完整 Evolution；不自动转向 Desktop。

AH. 基线 `23186ca24e78796538e92444df0474e2bcbf5a28`。branch `codex/v3.7-universal-evidence`。implementation SHA `2df65c7`，documentation SHA `70e8dbb`，Draft PR #3。GitHub Actions Run 30034572176：SUCCESS；PostgreSQL、backend/H2、frontend、Playwright、Hermes、Obsidian 和 sensitive-content 全部通过，真实 DeepSeek 因无安全 Key 按设计跳过。
