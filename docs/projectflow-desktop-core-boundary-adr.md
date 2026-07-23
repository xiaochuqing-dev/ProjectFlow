# ADR: ProjectFlow Desktop and Core Boundary

状态：Accepted for V3.5

日期：2026-07-23

## 决策

V3.5 保留 Java 17 / Spring Core，不进行 Tauri、Electron 或 Rust 重写。新项目理解能力实现为可直接调用的 Spring Service，HTTP Controller 只是 delivery adapter。

未来 Desktop 第一阶段优先评估“Desktop shell 管理本地 ProjectFlow Core sidecar，消费者通过受保护的 loopback API 或窄 IPC bridge 访问”。在真实打包 PoC 完成前，不锁定 Tauri 或 Electron。

## 为什么现在不迁移

1. Fact、Timeline、Capability、Model Gateway、Memory Gateway、Job、H2/PostgreSQL 已形成 Java Core。
2. 重写为 Rust 不会增加 V3.5 项目理解价值，反而扩大数据迁移、Provider SDK 和恢复语义风险。
3. 当前 React/Next.js 页面可以继续作为一个 delivery surface。
4. Desktop 选择需要真实验证 JRE/sidecar、升级、签名、托盘、崩溃回收和 Windows 安装器，而不是仅比较宣传中的包体。

## Core Boundary

```text
ProjectFlow Core
  Repository Intake
  Git / Evidence
  Structure Index SPI
  Understanding Engine
  Model Gateway
  Fact / Memory / Evolution
  Durable Jobs
  Read Gateways

Delivery / Consumers
  Current Next.js Web UI
  HTTP API
  Hermes MCP
  Obsidian Projection
  Future Desktop GUI
  Future CLI
  Future Background Engine
```

Core 禁止依赖：

1. 浏览器页面是否打开。
2. React/Next.js 路由状态。
3. sessionStorage。
4. Electron main process 或 Tauri command。

## Tauri 评估

优点：

1. 系统 WebView，shell 通常较小。
2. 原生菜单、托盘、更新和权限模型成熟。
3. sidecar 能保留 Java Core。

成本：

1. 需要 Rust/Tauri shell 和 Java/JRE sidecar 双运行时管理。
2. 当前 Next.js server feature 不能直接等同于静态 WebView。
3. sidecar 崩溃回收、端口认证、签名、更新原子性需要单独工程。
4. 官方 Java Tree-sitter 主线还会带来 JRE 版本差异，不能和 Desktop 决策混为一谈。

## Electron 评估

优点：

1. React/Node 生态迁移路径直接。
2. Chromium 行为一致。
3. main/renderer/preload 和 IPC 文档成熟。

成本：

1. Chromium/Node 带来更大包体和内存。
2. 必须保持 contextIsolation、sandbox 和最小 contextBridge，不能向 renderer 暴露任意 Node/API。
3. Java Core 仍是 sidecar，生命周期问题并未消失。

## IPC 与 localhost

窄 IPC：

1. 安全边界清晰。
2. 需要为每个能力维护 bridge，Hermes/CLI 不能直接复用。

loopback API：

1. 现有 Controller 和消费者可复用。
2. 必须使用随机端口、每进程 capability token、严格 Origin/Host、只绑定 loopback，并处理端口劫持与退出。

V3.5 仅保持两者可行，不实现 Desktop transport。

## 必须先完成的 Desktop PoC

1. Windows 打包 Java runtime 与 Core。
2. 启动、健康检查、退出、崩溃和孤儿进程回收。
3. H2 数据目录升级和回滚。
4. shell 与 Core 版本握手。
5. 自动更新失败恢复。
6. loopback token/Origin 安全或窄 IPC bridge。
7. 空闲与大型仓库分析时的真实内存、CPU、包体和启动时间。

## 后果

V3.5 的 RepositoryIntakeService、ProjectStructureIndexer 和 ProjectUnderstandingService 可在无浏览器环境由 Job、未来 CLI 或 Desktop sidecar 调用。当前 Web 页面只是读取持久化快照和提交 Job，不成为业务核心。
