# ProjectFlow V4 GUI Deferred Contract

日期：2026-08-25

状态：DEFERRED_NOT_IMPLEMENTED

## V3.10 边界

V3.10 只保证未来桌面 shell 可消费的 backend/frontend runtime、health、start/stop、version manifest、data directory、schema migration、backup/recovery 和 OS credential 合同。它不实现或宣称最终桌面 GUI。

## V4 才处理

- Electron/Tauri/其他 shell 的正式 PoC 与选择。
- 最终导航、Dashboard、Current State、Timeline、Chapter、Story、Evidence Drawer 和 Engineering Details。
- SHA/Hash 默认短显示、复制与完整审计展开。
- 正式中文产品语言、Progressive Disclosure、loading/degraded/conflict、onboarding、accessibility、motion 和视觉系统。
- 最终 icon、window、tray、installer branding、签名、更新与回滚体验。

## 必须复用

V4 不得复制或重写 V3.10 的 migration、Secret Store、runtime directories、backup/restore、security preflight、manifest/checksum 和 process ownership 规则。Shell 只编排已验证入口，不在页面层重建业务事实、History、Continuity、Memory、Gateway、Hermes 或 Obsidian 语义。

## 进入条件

只有 V3.10 数据迁移、凭据、local security、source-independent runtime、Windows smoke、供应链、核心回归、三 Provider secure-path smoke 和 GitHub closure 全部有真实 Evidence 后，才可给出 `V4.0 ENTRY = APPROVED`。本阶段不创建 Tag 或 Release。
