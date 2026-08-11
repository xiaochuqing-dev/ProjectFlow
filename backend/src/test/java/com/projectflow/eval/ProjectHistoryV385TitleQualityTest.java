package com.projectflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectHistoryV385TitleQualityTest {
    @Test
    void acceptsConcreteConservativeChineseOutcomeWording() {
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "实现了成果导出功能的基础代码",
            "本阶段为成果导出功能编写并加入了实现代码，尚未进行验证。"
        )).isTrue();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "完成登录流程的代码实现",
            "为登录流程新增了代码实现，但尚未提供稳定性验证。"
        )).isTrue();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "首次创建研究报告并更新其内容",
            "此次创建了研究报告，收录了新增的研究成果，并调整了报告结构。"
        )).isTrue();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "整理项目材料，留下变更记录",
            "针对项目材料进行了一次记录层面的调整。"
        )).isTrue();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "对项目文档进行了现状记录",
            "此次记录的范围仅限于项目文档已有的内容。"
        )).isTrue();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "为登录流程编写实现代码使其具备功能基础",
            "在该时间范围内完成了登录流程所需代码的编写与引入。"
        )).isTrue();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "首次创建项目文档并保存工作交接记录",
            "使项目从没有文档变为拥有可供查看的文档记录。"
        )).isTrue();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "新建项目文档，使项目有文档可看",
            "此次调整让项目文档从无到有。"
        )).isTrue();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "整理项目成果的设计与范围，留下方案记录",
            "补充了项目成果的设计和范围说明。"
        )).isTrue();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "创建研究报告并保存内容，使项目中首次出现该报告",
            "研究报告在此阶段被首次引入项目中。"
        )).isTrue();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "撤销研究报告的上一项改动，使报告从项目中移除",
            "此操作回退了此前对研究报告所做的变更。"
        )).isTrue();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "重新应用此前被撤销的研究报告改动，使报告再次出现",
            "之前被回退的研究报告变更在此阶段被重新恢复。"
        )).isTrue();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "创建并修改了项目成果的方案记录",
            "涵盖项目成果的设计和范围说明的补充。"
        )).isTrue();
    }

    @Test
    void rejectsActionOnlyOrGenericTemplateWording() {
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "调整项目材料",
            "本次涉及项目材料。"
        )).isFalse();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "整理项目材料",
            "形成初始结果。"
        )).isFalse();
        assertThat(ProjectHistoryV385QualityEvaluator.actionObjectResult(
            "整理项目材料，留下",
            "本次涉及项目材料。"
        )).isFalse();
    }
}
