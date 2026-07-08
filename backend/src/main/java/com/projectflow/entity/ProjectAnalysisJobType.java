package com.projectflow.entity;

public enum ProjectAnalysisJobType {
    PROJECT,
    FILE,
    CAPABILITY_INTERPRET,
    WORK_SESSION_SCAN,
    // V3.3.4: 能力分析异步化，刷新/离开页面不丢任务。
    CAPABILITY_CARD_ANALYSIS
}
