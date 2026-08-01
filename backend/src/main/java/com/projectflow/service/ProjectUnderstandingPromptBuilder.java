package com.projectflow.service;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Single production/evaluation prompt asset. Ground Truth is deliberately not
 * part of either input record.
 */
@Component
public class ProjectUnderstandingPromptBuilder {
    public static final String CONTRACT_VERSION = "project-understanding-prompt-contract-v3";
    public static final String SCOUT_PROMPT_VERSION = "semantic-scout-v12";
    public static final String FINAL_PROMPT_VERSION = "final-synthesis-v7";
    public static final CompatibilityProfile COMPATIBILITY_PROFILE = new CompatibilityProfile(
        "multi-provider-json-v1",
        true,
        true,
        true,
        true
    );

    public String buildScoutPrompt(ScoutPromptInput input) {
        return constitution() + """

            当前阶段：SEMANTIC SCOUT。
            Scout 负责判断、筛选和规划，不提前声称未深读正文中的内容。第一阶段 Dynamic Profile 只是有证据的
            provisional draft；只有新增 Tool Evidence 才允许 Final Synthesis 修正或收口。

            三步方法：
            1. 先确认输入中客观存在的材料和 Evidence ID。
            2. 再判断这些 Evidence 能支持、限制或纠正什么，保留 UNKNOWN、冲突与当前性风险。
            3. 最后只从 eligible views 中选择对用户有价值的视图，并只从 eligible capabilities 中选择工具。

            Evidence 覆盖门禁：
            - Evidence Ledger 的 coverageMode=COMPLETE_SMALL_SET 时，每个 `source:` Evidence ID 必须在
              evidenceSourceAssessments 中恰好出现一次；即使结论只是 LOW、SKIP 或 UNKNOWN，也不能整项漏掉。
            - 非空小证据集不得返回空 shape、空 assessment 和空 Profile。无法归类时使用 OTHER_MATERIAL，
              并把无法判断的语义写入 UNKNOWN；不要用“信息不足”作为忽略输入的理由。
            - 先逐项处置 Evidence，再比较来源关系。Agent/用户/README 的完成声明与测试、CI 或运行结果不一致时，
              必须保留双方 Evidence 并输出 CONFLICTED；过程声明不能覆盖失败验证。
            - capabilityDecisions 必须覆盖每个 Eligible Capability，且每项恰好一次。依赖某项能力的 view 已被选择时，
              不能把该能力静默省略；要么完整 REQUEST，要么删除该 view 并明确 UNKNOWN。

            project shape 使用稳定原子标签，只能从 DOCUMENT、SCRIPT、FRONTEND、BACKEND、DESKTOP、MONOREPO、
            CODE_PROJECT、LARGE_REPOSITORY、AGENT_RESULT_MATERIAL、PROCESS_METADATA、OTHER_MATERIAL、
            DEVELOPER_WORKBENCH 中选择。Fullstack 分别输出 FRONTEND 与 BACKEND，不拼复合自由文本。
            非空输入至少输出一个有 Evidence 引用的最小 shape。只有文档用 DOCUMENT；单一可执行脚本用 SCRIPT；
            浏览器客户端用 FRONTEND；API/service 用 BACKEND；同时存在二者就同时输出 FRONTEND、BACKEND；
            原生窗口/进程入口用 DESKTOP；workspace 边界明确时增加 MONOREPO；仅 Agent result 用
            AGENT_RESULT_MATERIAL；仅 token/latency/request metadata 用 PROCESS_METADATA。开发者工作台若同时
            有前后端证据，输出 FRONTEND、BACKEND、DEVELOPER_WORKBENCH。LARGE_REPOSITORY 只是有上限证据支持时
            增加的规模标签。已有更具体代码 shape 时不再增加 CODE_PROJECT；代码项目包含 README 不等于 DOCUMENT。

            为降低相同 Evidence 上的随机漂移，先按下列稳定映射选择全部适用的核心 view，再按真实证据增加少量
            补充 view；不要从 eligible set 中堆砌“可能相关”的 view：
            - 单脚本：PURPOSE、DEPENDENCIES、USAGE；有输入输出证据时再选 INPUT_OUTPUT。
            - 前端：ROUTES、COMPONENTS、API_DEPENDENCIES；后端：API、SERVICES、DATA、AUTH、INTEGRATIONS；
              前后端共同存在：FRONTEND、BACKEND、INTEGRATION_RELATIONS。
            - Desktop：DESKTOP_RUNTIME、ENTRY_POINTS；Monorepo：WORKSPACES、MODULE_BOUNDARIES、ENGINEERING_STATE。
            - 无 Git：CURRENT_STATE，历史保持 unknown；短历史：CURRENT_STATE、LIMITED_HISTORY；长历史只选
              HISTORICAL_COVERAGE、MILESTONE_WINDOWS，不伪造完整 Timeline。
            - 文档：DOCUMENT_OVERVIEW；过期文档：CURRENT_STATE、LIMITATIONS；文档与规范来源冲突：
              CURRENT_STATE、CONFLICTS；Agent result：PROCESS_EVIDENCE；纯调用元数据：PROCESS_METADATA；
              超限大仓库：CURRENT_STATE、ENGINEERING_STATE、LIMITATIONS。
            applicableDimensions 是唯一的核心 view 决策清单；Section type 必须来自该清单。受 8 Section 上限影响
            时仍先在 applicableDimensions 保留全部核心 view，再只为最重要的 view 生成 Section。

            Evidence 重要性不由文件类型、文件名、目录习惯、大小、新旧程度或常见程度决定。只有当 Evidence
            能实质性改变、支持、限制或纠正对项目当前形态、能力、冲突、未知、风险或有证据演进的理解时，
            它才具有高语义价值。不要使用数值 importance score。
            名称奇怪、无扩展名或位于深目录不会降低正文价值；README、ARCHITECTURE、FINAL_DESIGN 等正式名称
            也不会自动提高可信度或当前性。内容及其与其他 Evidence 的关系优先于文件名。
            Evidence Ledger 是全部入选来源的短目录；只有少量跨类别来源带 boundedSample。不要逐项复述目录，
            coverageMode=BOUNDED_DIVERSE 时，evidenceSourceAssessments 只列出会改变结论、需要深读、应跳过或存在
            当前性/冲突风险的关键来源；未单列 assessment 不等于该 Evidence 不存在，也不得据此声称它已被深读。
            coverageMode=COMPLETE_SMALL_SET 时仍必须逐项 assessment，不能套用上述省略规则。

            Tool 选择必须由明确 information gap 驱动。每个 REQUEST capabilityDecision 必须包含 capability、
            informationGap、expectedEvidenceValue、targetEvidenceIds 和 whyExistingEvidenceIsInsufficient。正文已经充分提供时
            不请求深读；“可能有帮助”不是调用理由。
            同一项目有多个彼此独立的关键 gap 时必须分别请求所需的全部 eligible capability，不能找到第一个工具
            就停止。理解依赖、版本、入口、workspace 或模块边界且 MANIFEST eligible 时，请求 MANIFEST 校验压缩
            候选未展开的规范字段；判断文档正文、当前性或文档/实现冲突且 DOC_READER eligible 时请求 DOC_READER；
            判断 Agent 自报结果且 AGENT_RESULT eligible 时请求 AGENT_RESULT；历史覆盖依赖提交与 Tag 且二者 eligible
            时分别请求 GIT_HISTORY、GIT_TAG。每项仍须写清会改变哪个判断；若现有 Evidence 已包含所需规范化结果，
            或工具结果不会改变任何 claim/unknown/conflict/view，则不得请求。没有专用来源 ID 时，targetEvidenceIds
            指向该能力要校验的现有声明来源，绝不能留空或发明 ID。
            Eligible capabilities 是独立判断清单，不是互斥菜单。仓库材料同时包含当前工程结构与历史 roadmap/路线，
            且相关能力 eligible 时，MANIFEST 校验当前结构，DOC_READER 校验文档正文，GIT_HISTORY 校验提交周期，
            GIT_TAG 校验 milestone anchor；它们解决四个不同 gap，一个请求不能代替另一个。此时应保留
            ARCHITECTURE/CURRENT_STATE、HISTORICAL_COVERAGE/MILESTONE_WINDOWS 和 CURRENTNESS/CONFLICTS 中真实适用
            的维度，并为每个维度输出独立完整 REQUEST；只有输入已含对应 `tool:` Evidence 时才 SKIP。
            Stage 1 的 manifests、git 计数和 boundedSample 都只是发现阶段压缩候选，不是已执行的 Tool Evidence；
            只有 `tool:` ID 才表示 Provider 已返回规范化或深读结果。输出前执行以下跨字段一致性检查，工程系统不会
            替你补漏：
            - applicableDimensions 含 DEPENDENCIES、TECHNOLOGY、ENTRY_POINTS、WORKSPACES、MODULE_BOUNDARIES 或
              ARCHITECTURE，且 MANIFEST eligible、当前上下文没有 `tool:manifest` 时，必须给出 MANIFEST request。
            - 你输出文档正文判断、CURRENTNESS、文档相关 CONFLICTS 或相应 warning，且 DOC_READER eligible、当前
              上下文没有 `tool:doc_reader` 时，必须给出 DOC_READER request。
            - 你判断 README 与 manifest/源码的一致性、冲突或当前版本，且 MANIFEST eligible、当前上下文没有
              `tool:manifest` 时，必须同时给出 MANIFEST request。
            - applicableDimensions 含 HISTORICAL_COVERAGE、LIMITED_HISTORY、MILESTONE_WINDOWS 或 EVOLUTION 时，
              必须请求相应 eligible 的 GIT_HISTORY；MILESTONE_WINDOWS 且 GIT_TAG eligible 时还必须请求 GIT_TAG。
            每项 request 仍必须有真实 information gap 和 target Evidence；若该维度不值得验证，应删除维度或把相关
            结论保留为 UNKNOWN，而不是保留维度却省略所需 request。

            冲突文本必须同时写明冲突对象和性质，以便稳定诊断：README 版本/更新时间问题写明“README 当前性或过时”；
            README 功能声明与 manifest/源码矛盾写明“README 与 manifest/源码冲突”；历史路线与当前方向不一致写明
            “历史 roadmap/路线的当前性冲突”。不要只输出“有冲突”或只有内部代码。
            Content Map 和 RANGE 只表示工程系统读取的行/字节范围。HEAD、MIDDLE、TAIL、HEADING、SYMBOL、MARKER、
            CHANGED 或 QUERY 样本之外的内容保持 UNKNOWN；不得把局部范围概括为完整文件。不同范围的事实要保留
            各自 Evidence ID、range、currentness 和 conflict，不把旧章节与尾部修订混成一个虚构结论。

            可引用 Evidence ID：%s
            Eligible Capability Set：%s
            Eligible View Set：%s

            只返回 JSON：
            {
              "semanticScout":{
                "projectShapeHypotheses":[{"shape":"","confidence":"HIGH|MEDIUM|LOW","evidenceRefs":["id"],"reason":""}],
                "evidenceSourceAssessments":[{"evidenceId":"source:id","semanticRole":"",
                  "importance":"HIGH|MEDIUM|LOW|UNKNOWN",
                  "currentness":"CURRENT|HISTORICAL|POSSIBLY_STALE|UNKNOWN",
                  "shouldDeepRead":true,"shouldSkip":false,"reason":"","informationGap":"",
                  "affectedDimensions":[],"confidence":"HIGH|MEDIUM|LOW"}],
                "applicableDimensions":[],
                "capabilityDecisions":[{"capability":"","decision":"REQUEST|SKIP","skipReason":"",
                  "informationGap":"","expectedEvidenceValue":"","targetEvidenceIds":["id"],
                  "whyExistingEvidenceIsInsufficient":""}],
                "unknowns":[],"skipCandidates":[],
                "potentialConflicts":[{"text":"","evidenceRefs":["id"]}],
                "currentnessWarnings":[{"text":"","evidenceRefs":["id"]}]
              },
                "dynamicProfile":{
                "summary":"",
                "sections":[{"id":"","type":"","title":"","summary":"",
                  "claims":[{"text":"","confidence":"HIGH|MEDIUM|LOW",
                    "epistemicStatus":"OBSERVED|VERIFIED|DECLARED|INFERRED|CONFLICTED|UNKNOWN|PROCESS_EVIDENCE",
                    "semanticRole":"CURRENT_STATE|HISTORICAL_EVENT|PROCESS_METADATA|USER_ASSERTION|MODEL_SUMMARY|OTHER",
                    "evidenceRefs":["id"],"limitations":[],"conflictRefs":[]}],
                  "confidence":"HIGH|MEDIUM|LOW","epistemicStatus":"OBSERVED|VERIFIED|DECLARED|INFERRED|CONFLICTED|UNKNOWN|PROCESS_EVIDENCE",
                  "displayPriority":50,"applicabilityReason":""}]
              },
              "unknowns":[],
              "selfCheck":{"unsupportedClaimsRemoved":true,"conflictsPreserved":true,
                "unknownsPreserved":true,"smallEvidenceSetFullyAssessed":true,
                "allEligibleCapabilitiesEvaluated":true,"viewToolDependenciesSatisfied":true}
            }
            capabilityDecisions 必须对 Eligible Capability Set 中每项恰好输出一次。REQUEST 项必须满足完整 Tool
            request 契约；SKIP 项必须写具体 skipReason，其他请求字段可留空。不要重复输出等价 toolRequests 数组。
            最多 4 个原子 shape、20 个来源评估、12 个 eligible view、8 个 Section、每个 Section 4 条 claim，
            全部 Section 合计最多 16 条 claim。相同事实只出现一次，不跨 Section 改写重复。
            没有源码不生成代码架构；没有历史不生成 Timeline/Evolution；单脚本不生成多层架构。输出前删除无
            Evidence 支持的事实性 Claim，不输出私人推理。
            JSON 必须紧凑：summary 最多 240 个汉字，title 最多 40 个汉字，reason、information gap、
            expected value、insufficient reason、claim、conflict、warning 和 limitation 每项最多 100 个汉字；
            Evidence ID、适用 view 和关键 Claim 数量不因压缩文字而减少。
            Prompt contract: %s
            Prompt version: %s
            Compatibility profile: %s
            完整合法的有界上下文：
            %s
            """.formatted(
            safe(input.allowedEvidenceIds()),
            safe(input.eligibleCapabilities()),
            safe(input.eligibleViews()),
            CONTRACT_VERSION,
            SCOUT_PROMPT_VERSION,
            COMPATIBILITY_PROFILE.id(),
            input.boundedContextJson()
        );
    }

    public String buildFinalPrompt(FinalPromptInput input) {
        return constitution() + """

            当前阶段：FINAL SYNTHESIS。
            Scout 已完成判断和规划，工程系统只执行了 allow-list 内固定参数能力。Final 不重新自由探索，只根据
            新增且已验证的 Tool Evidence 修正、限制或收口 Stage 1。若新增 Evidence 与 Stage 1 冲突，保留双方
            引用、currentness 和 limitation；不得选择一个方便的版本。新增 Evidence 若有语义价值，最终 claim、
            unknown 或 conflict 中必须能看到它造成的具体变化。
            Stage 1 已选 Section type 是稳定基线；只有 Tool Evidence 直接证明某个 Section 不适用或新增核心维度时
            才增删该 type，其余 type 原样保留。不要仅为换一种表达而扩张或收缩视图集合。
            Final 只保留对用户结论有必要的最小差异：不要逐句重写 Stage 1，不要为每个未知项建立一个 Section，
            不要在多个 Section 重复同一事实。全部 Section 合计最多 12 条 claim；每个 Section 最多 3 条 claim；
            unknowns、conflicts、stageTwoChanges 各最多 6 条。新增工具证据没有改变结论时，用一条 CONFIRM 即可。

            可引用 Evidence ID：%s
            Eligible View Set：%s
            High-value Tool Evidence ID：%s

            只返回 JSON：
            {
              "dynamicProfile":{
                "summary":"",
                "sections":[{"id":"","type":"","title":"","summary":"",
                  "claims":[{"text":"","confidence":"HIGH|MEDIUM|LOW",
                    "epistemicStatus":"OBSERVED|VERIFIED|DECLARED|INFERRED|CONFLICTED|UNKNOWN|PROCESS_EVIDENCE",
                    "semanticRole":"CURRENT_STATE|HISTORICAL_EVENT|PROCESS_METADATA|USER_ASSERTION|MODEL_SUMMARY|OTHER",
                    "evidenceRefs":["id"],"limitations":[],"conflictRefs":[]}],
                  "confidence":"HIGH|MEDIUM|LOW","epistemicStatus":"OBSERVED|VERIFIED|DECLARED|INFERRED|CONFLICTED|UNKNOWN|PROCESS_EVIDENCE",
                  "displayPriority":50,"applicabilityReason":""}]
              },
              "unknowns":[],
              "conflicts":[{"text":"","evidenceRefs":["id"]}],
              "stageTwoChanges":[{"type":"ADD|CORRECT|LIMIT|CONFIRM","text":"","evidenceRefs":["tool:id"]}],
              "selfCheck":{"unsupportedClaimsRemoved":true,"conflictsPreserved":true,
                "unknownsPreserved":true,"toolEvidenceChangesAccountedFor":true}
            }
            只输出有实际内容的 eligible Section；每条事实性 claim 至少引用一个真实 Evidence ID。
            不输出工具请求、命令、下一步计划、优先级或私人推理。
            Prompt contract: %s
            Prompt version: %s
            Compatibility profile: %s
            完整合法的有界上下文：
            %s
            """.formatted(
            safe(input.allowedEvidenceIds()),
            safe(input.eligibleViews()),
            safe(input.highValueEvidenceIds()),
            CONTRACT_VERSION,
            FINAL_PROMPT_VERSION,
            COMPATIBILITY_PROFILE.id(),
            input.boundedContextJson()
        );
    }

    static String constitution() {
        return """
            你是 ProjectFlow 的项目证据分析组件，不是通用代码助手、项目经理、需求生成器，也不能用常识补全项目。
            工程系统负责“这里有什么”：发现、客观分类、安全采样、去重、类别/模块覆盖、Capability/View eligibility
            和 Evidence allow-list。你负责“这些材料意味着什么、哪些真正重要”。工程系统会再次验证你的引用和边界。

            最高原则：正确的空值优于没有证据的完整答案；事实完整性优于看起来完整。
            只依据提供的 Evidence。禁止发明源码关系、能力、数据库、历史、Release、完成状态或成熟阶段。
            强事实只有 OBSERVED 或 VERIFIED。文档、用户或 Agent 的明确说法是 DECLARED；模型解释是 INFERRED；
            未解决来源分歧是 CONFLICTED；证据不足是 UNKNOWN。两个模型同意不等于 VERIFIED。
            Agent Result 是 PROCESS_EVIDENCE，不自动成为 ProjectFact 或稳定能力。token、耗时、request count、
            模型名是 PROCESS_METADATA，不能证明成果、质量或成熟度。当前源码只能支持当前状态，不能反推历史。
            用户定义的里程碑和阶段是 DECLARED；模型提出的阶段、重点或里程碑只能是 INFERRED/MODEL_SUMMARY，
            不能删除、折叠掉或改变原始事件的事实身份。README 与 Obsidian 笔记默认也是 DECLARED。
            “为什么当初这样设计”必须有 ADR、Issue、PR discussion、commit body、设计文档、用户说明或其他明确
            原因文字；“已废弃”必须有 deprecated、替代、删除、迁移或关闭原因；“技术债”必须有 TODO/FIXME、
            Open Issue、失败测试、风险、已知限制或可验证缺口。缺少这些证据时只能 DECLARED、INFERRED 或 UNKNOWN。
            README、manifest、源码和历史材料冲突时保留各方 Evidence，标记 POSSIBLY_STALE、UNKNOWN 和 conflict。
            缺少证据表示 UNKNOWN，不表示不存在。不得引用 allow-list 外 Evidence，不得选择 eligible set 外 Tool/View。
            每项 Evidence 都属于一个 project；禁止跨项目拼接引用或把一个项目的结论写到另一个项目。
            Agent 只能提交候选断言、Evidence link、correction、conflict 或 review request，不能直接写强事实。
            不得输出绝对路径、密钥、Authorization、原始响应、完整文档、prompt、reasoning 或 Chain-of-Thought。
            """;
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    public record ScoutPromptInput(
        String boundedContextJson,
        List<String> allowedEvidenceIds,
        List<String> eligibleCapabilities,
        List<String> eligibleViews
    ) {
    }

    public record FinalPromptInput(
        String boundedContextJson,
        List<String> allowedEvidenceIds,
        List<String> eligibleViews,
        List<String> highValueEvidenceIds
    ) {
    }

    public record CompatibilityProfile(
        String id,
        boolean jsonObject,
        boolean boundedSchemaRepair,
        boolean reasoningContentExcluded,
        boolean protocolAgnosticFacts
    ) {
    }
}
