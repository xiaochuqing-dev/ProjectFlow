// 把扫描事实映射为稳定的能力名称。
// 原始事实不丢，由调用方保留到 detail.recognized。
// 兜底用保守分桶名"项目资产沉淀能力"，不取事实首段拼接（会生成怪标题）。

type CapabilityBucket = {
  name: string;
  test: (raw: string) => boolean;
};

const BUCKETS: CapabilityBucket[] = [
  { name: "项目结构识别能力", test: (raw) => /已导入.*项目结构|完整项目结构|目录树|文件结构|项目结构/.test(raw) },
  { name: "技术栈理解能力", test: (raw) => /技术栈|主要技术|框架|依赖|技术选型/.test(raw) },
  { name: "测试证据追踪能力", test: (raw) => /测试目录|测试文件|测试入口|单测|集成测试|自动化测试|回归验收/.test(raw) },
  { name: "运行配置识别能力", test: (raw) => /本地启动|启动配置|启动入口|环境变量|部署|容器|docker|运行验证/.test(raw) },
  { name: "开发成果沉淀能力", test: (raw) => /Git 变化|Agent 写回|开发成果整理|变更追溯|开发活动沉淀/.test(raw) },
  { name: "分层架构落地能力", test: (raw) => /前后端分层|API.*前端|页面.*接口.*服务|分层架构/.test(raw) },
  { name: "全链路数据流转能力", test: (raw) => /页面.*接口.*服务.*数据流转|数据流转闭环|数据流转/.test(raw) },
  { name: "工程文档治理能力", test: (raw) => /readme|架构文档|协作规则|工程文档|长期维护追溯|项目说明|架构和协作/.test(raw) },
  { name: "风险识别能力", test: (raw) => /风险|隐患|待修复|问题/.test(raw) },
  { name: "输出素材沉淀能力", test: (raw) => /输出|模板|表达|README.*简历|成果素材/.test(raw) },
];

const FALLBACK_NAME = "项目资产沉淀能力";

export function capabilityNameOf(rawFact: string): string {
  const text = rawFact ?? "";
  for (const bucket of BUCKETS) {
    if (bucket.test(text)) {
      return bucket.name;
    }
  }
  return FALLBACK_NAME;
}

export function isFallbackName(name: string): boolean {
  return name === FALLBACK_NAME;
}
