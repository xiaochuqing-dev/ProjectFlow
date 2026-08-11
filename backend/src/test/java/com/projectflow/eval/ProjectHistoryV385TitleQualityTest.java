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
    }
}
