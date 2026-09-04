ProjectFlow V4 设计系统与桌面基础研究

复核日期：2026-09-04

状态：本报告只记录 V4.0-A 的研究结论和 V4-B 可删除 PoC 的证据要求。不安装依赖，不修改当前 UI，不选择桌面 shell，也不授权 installer、tray、自动更新或发布。

一、结论

当前前端基础是 Next.js 16.3.2、React 19.2.8、TypeScript、Tailwind CSS 3.4.17 和 lucide-react。Tailwind 配置已经有 surface、ink、line、brand 与 success、warning、danger 等语义雏形，globals.css 仍含页面级硬编码颜色；目前没有安装 Radix、React Aria、Base UI、shadcn/ui 或 CVA。

V4.0-A 应先建立 ProjectFlow semantic tokens 与 source-owned components。token 描述语义而不是页面或具体颜色，例如 canvas、surface、raised surface、text、muted text、border、accent、focus、success、warning、danger、radius、spacing、type 与 motion。token 的唯一入口放在全局 CSS 变量和 Tailwind 映射；页面、组件和状态样式不再各自发明颜色、阴影或圆角。现有视觉值可先被语义化映射，不能借此升级 Tailwind、重画全站或改变产品语义。

source-owned component 指组件源码由仓库维护并可审计、可测试、可修订，不是把上游包当成不可见黑盒。建议后续只在 frontend/src/components/ui 形成少量可复用原子组件，并保留来源、版本和许可证记录。shadcn/ui 可作为这种交付方式的参考和受控源码来源；它不是本项目必须安装的运行时框架。复制后的代码由 ProjectFlow 负责升级、无障碍回归和安全修复，不能把本地修改误称为上游支持。

默认交互底座为 Radix。React Aria 是复杂 collection 的挑战者，只在 Table、ListBox、Tree、可选择集合、虚拟化前后的焦点与键盘模型等有真实复杂度的场景，以同一验收样本比较后再选用。Base UI 保持观察候选，不在 V4.0-A 引入。单一组件及同一交互族不得混用多个 primitive 系统；原生 button、input、label、dialog 语义优先，只有确有复合焦点、浮层、菜单或 collection 需求时才使用 primitive。

CVA 仅可作为 source-owned component 的受类型约束 class variant 工具。它适合有限的 intent、size、density、emphasis 等公开视觉变体；不负责 token、状态机、焦点管理、ARIA、键盘行为、表格选择、页面布局或任意 class 拼接。交互状态应来自 primitive 的 data state 和语义属性，token 仍由全局入口定义。不得把每个页面、每个业务字段或所有 Tailwind 组合都做成 CVA variant。

DESKTOP_SHELL_DECISION = DEFERRED

这不是对 Electron、Tauri 或 WebView2 的否定，而是遵守现有 Java Core、Next delivery surface、V3.10 runtime、数据目录、迁移、备份与凭据边界。在 V4-B 的可删除 PoC 有真实 Windows 证据前，不能用包体宣传、生态印象或静态网页样例替代选择。

二、候选组件与兼容性复核

Radix。默认交互底座。官方资料说明其提供无样式、可组合的 React primitives，并以 WAI-ARIA pattern、键盘和辅助技术支持为核心。官方 npm 元数据的 peer dependency 覆盖 React 19；当前 Next.js 16.3.2 的 peer range 也覆盖 React 19，因而与仓库的 React 19.2.8 在声明层兼容。实际采用仍须在当前 App Router、严格模式、客户端边界、键盘流程和生产构建上做最小 PoC。许可证为 MIT。2026-09-04 复核时，官方 GitHub 仓库未归档且仍有维护活动；这只是维护信号，不替代锁定精确版本、依赖审计和升级回归。

官方资料：https://www.radix-ui.com/primitives
https://www.radix-ui.com/primitives/docs/overview/accessibility
https://github.com/radix-ui/primitives
https://www.npmjs.com/package/@radix-ui/react-dialog

React Aria Components。复杂 collection 的挑战者。官方资料强调无预设样式、内置行为、辅助功能和国际化；它适合把可选择 collection、键盘导航、屏幕阅读器及本地化行为作为一个整体检验。官方 npm 元数据的 peer range 覆盖 React 19，因此在现有 Next 和 React 基线中可进入 PoC，但不是默认全量替换理由。许可证为 Apache-2.0。2026-09-04 复核时，Adobe 官方仓库未归档且有维护活动。若它在同一表格、树或列表样本中明显降低代码和无障碍风险，才针对该交互族采用；否则保持 Radix，避免双 primitive 叠加。

官方资料：https://react-spectrum.adobe.com/react-aria/components.html
https://github.com/adobe/react-spectrum
https://www.npmjs.com/package/react-aria-components

Base UI。观察候选。官方资料称其为无样式、遵循 WAI-ARIA pattern 的组件库，支持 React 17 及更新版本，并声明支持维护中的主流 bundler；因此其声明范围覆盖当前 React 19 与 Next 构建链。许可证为 MIT。2026-09-04 复核时，MUI 官方仓库未归档且有维护活动。shadcn/ui 当前的新项目默认项是 Base UI，但该上游默认不覆盖 ProjectFlow 的 Radix 决策。只有在 V4-B 前的受控比较中证明 API、类型、SSR 与客户端边界、焦点管理和升级成本优于默认底座，才重新评估。

官方资料：https://base-ui.com/react/overview/about
https://github.com/mui/base-ui
https://www.npmjs.com/package/@base-ui/react
https://ui.shadcn.com/docs/changelog/2026-07-base-ui-default

shadcn/ui。source-owned component 的参考和受控源码来源，而非必须保留的运行时依赖。官方文档说明组件代码落在项目中，可用现有项目生成器添加；官方 React 19 和 Next 指南说明其当前实现覆盖 React 19。许可证为 MIT。2026-09-04 复核时，官方仓库未归档且有维护活动。采用其源码时必须锁定 registry revision、保留许可证与本地改动说明，并明确选择 Radix 实现；不要因其也支持 Base UI 或 React Aria 而自动切换底层。

官方资料：https://ui.shadcn.com/docs/installation/next
https://ui.shadcn.com/docs/react-19
https://ui.shadcn.com/docs/changelog/2026-01-base-ui
https://github.com/shadcn-ui/ui

CVA。可选的类型化 class variant 工具，不是视觉系统或交互库。它不依赖 React peer，因而不会与当前 Next 16.3.2 和 React 19.2.8 形成框架 peer 冲突；仍须验证 TypeScript 类型、Tailwind 合并策略和组件 API 是否符合本项目边界。许可证为 Apache-2.0。2026-09-04 复核时，官方仓库未归档且有维护活动；npm 当前稳定包的最后修改时间较早，不能把仓库活动误写为已经发布了新的生产版本。若采用，锁定精确版本并做依赖审计。

官方资料：https://cva.style/docs
https://github.com/joe-bell/cva
https://www.npmjs.com/package/class-variance-authority

三、无障碍与 token 验收边界

所有 source-owned component 必须先保证语义 HTML、可见焦点、正确名称和描述、键盘可达性、Escape 与焦点返回、禁用状态、错误关联以及屏幕阅读器可理解的状态变化。Radix、React Aria 或 Base UI 可以提供行为基础，但不替代本地组件在中文文案、图标按钮标签、表单错误、颜色对比、缩放、减少动画偏好和真实页面焦点顺序上的验收。无障碍测试应使用键盘和至少一种 Windows 屏幕阅读器的关键路径检查，并以 Playwright 等自动检查作为补充而非唯一证据。

token 不得泄漏业务状态。业务语义先映射到成功、警告、危险、信息、未知等通用 token，再由组件消费；不能为某个页面、模型状态或历史事实直接新增专用颜色。组件可以有有限 variant，页面只能组合组件和 layout token。这样既避免重复卡片与页面级 helper，也使 Web、Electron、Tauri 和 WebView2 共享同一 React 与 CSS 输出。

四、桌面候选复核

Electron。它为 renderer 自带 Chromium 与 Node，现有 React 与 Next renderer 的迁移路径直观，但 Java Core 仍须作为 sidecar 管理，包体、内存、Chromium 安全更新和双运行时生命周期必须由真实测量回答。许可证为 MIT。2026-09-04 复核时，官方仓库未归档且发布日程仍公开维护。若进入 PoC，必须保持 nodeIntegration 关闭、contextIsolation 和 sandbox 开启、最小 preload 与 contextBridge、IPC sender 校验、严格 CSP、受限导航和受限外部链接；不能向 renderer 暴露任意 Node、文件系统或 IPC。

官方资料：https://www.electronjs.org/docs/latest/tutorial/security
https://www.electronjs.org/docs/latest/tutorial/context-isolation
https://releases.electronjs.org/schedule
https://github.com/electron/electron

Tauri。它以 Rust Core 编排系统 WebView，Windows 使用 Microsoft Edge WebView2；终端用户通常复用系统 WebView，但项目会新增 Rust/Tauri shell 与 Java/JRE sidecar 两个运行时边界。Next standalone 不能被假定为可直接静态塞入 WebView，需单独证明本地服务或静态交付方式。官方仓库 GitHub metadata 的许可证为 Apache-2.0，精确 crate 引入时仍以锁定版本的许可证文件复核。2026-09-04 复核时官方仓库未归档且有维护活动。PoC 必须使用最小 capability/command 权限，不给前端任意文件、shell 或 sidecar 调用能力。

官方资料：https://v2.tauri.app/concept/process-model/
https://v2.tauri.app/reference/webview-versions/
https://github.com/tauri-apps/tauri

WebView2。它是 Windows WebView runtime，不是完整的跨平台桌面 shell；直接选择它还需要另行确定 Windows host、窗口、进程、桥接、签名与更新方案。Microsoft 文档说明分发时必须保证 runtime 存在，Evergreen 与 Fixed Version 各有安全更新、离线安装和体积取舍。它不应被当成 Electron 或 Tauri 的零成本替代。WebView2 属于 Microsoft runtime 与分发条款范围，不可在本报告中当作开源依赖许可；发布前需按目标分发模式复核条款。桥接只能暴露按能力命名且参数校验的 API，并限制导航、资源来源和宿主对象。

官方资料：https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/distribution
https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/evergreen-vs-fixed-version

五、V4-B 可删除 PoC 证据矩阵

PoC 的共同约束是独立、可一次性删除、不得改动 Java Core 语义、数据库 schema、迁移、Secret Store、ProjectFact、History、Gateway、Hermes 或 Obsidian。实验代码、构建产物和临时配置必须与生产路径分离；删除实验后浏览器交付和现有 Start-ProjectFlow.bat 行为保持不变。通过某一项只表示该候选具备继续评估资格，不等于 shell 已选定。

启动与生命周期。Electron、Tauri 和 WebView2 host 都必须在干净 Windows 用户、无 Maven、npm、Git 和源码树依赖的条件下启动已验证的 Java runtime 与 Next 产物，完成 health check、正常退出、强制杀死 Core 后的 UI 诊断、重启和孤儿进程回收。证据包括版本清单、脱敏启动日志、失败原因和实际启动时间。任一容器依赖开发服务器、遗留端口或手工环境变量即失败。

安全 transport。每个候选必须在窄 IPC 与 loopback API 中选择一个受验证的 transport。loopback 必须使用随机端口、每进程 capability token、严格 Origin 与 Host、仅 loopback 绑定及退出失效；IPC 必须是按能力命名、参数校验、来源校验的窄 bridge。证据包括拒绝错误 token、错误 Origin、跨窗口或跨进程调用以及 renderer 注入尝试的结果。不得把凭据、任意路径、任意 shell 命令或完整 Node API 暴露给前端。

数据、迁移与凭据。PoC 必须在隔离的已有 H2 数据副本上证明备份、启动升级、失败保留与恢复；外部 PostgreSQL 仍按既有独立运维边界处理。必须证明 OS credential 路径在 shell 重启后可读取且不会进入 renderer、日志、页面、诊断或打包物。证据应包含成功和失败路径的脱敏结果；不得以新建空库或内存 fake 代替旧数据升级。

UI 与无障碍。三个候选加载同一 Web UI、同一 semantic tokens 和同一 source-owned components。验证登录后主路径、加载、降级、冲突、键盘、焦点返回、缩放和屏幕阅读器关键路径。证据包括浏览器与容器的截图或自动化结果及人工 Windows 检查；UI 为适应容器而复制页面、调用 shell API 或降低无障碍要求即失败。

资源与更新边界。对空闲、常规项目和大型仓库分析测量冷启动、空闲内存、CPU、包体、崩溃恢复和关闭时间，并记录硬件、Windows 版本与测量方法。Electron 还要记录 Chromium 与 Electron 更新责任；Tauri 和 WebView2 要分别记录 WebView2 运行时已存在、缺失、离线和受企业策略限制时的行为。installer、签名、tray、自动更新和回滚只能记录为后续风险，不在本 PoC 伪造完成状态。

删除判定。每个实验必须有单独的来源目录、构建清单和删除说明；删除后运行当前浏览器启动与核心回归。若实验需要改动业务事实、持久化 schema、正式 runtime、用户数据根、生产导航或依赖锁文件，便不再是可删除 PoC，必须停止并另行取得范围批准。

六、shell-independent foundation

V4.0-A 的 React 页面只能依赖现有受保护 HTTP API 和浏览器标准能力，不能直接 import Electron、Tauri、WebView2 host API，不能在组件中读取本地文件、启动进程、访问原生凭据或假定桌面窗口存在。未来桌面特性只能经一个显式 capability adapter 在 PoC 通过后提供，并且浏览器模式必须有安全的缺省或不可用提示。Core 继续不依赖页面路由、sessionStorage、Electron main process 或 Tauri command。

Shell 只编排已验证的 runtime、启动、health、退出、版本握手和受保护 transport；它不重建项目理解、事实、时间线、能力、连续性、Gateway、Hermes、Obsidian、迁移、备份或凭据逻辑。这样 token、source-owned components、页面语义和可访问性测试能同时服务浏览器与未来任一桌面候选，而 shell 失败不会改变用户数据和业务事实。

七、实施前门禁

本研究没有安装或升级依赖，也没有运行构建或测试。真正引入任一组件前，必须锁定精确版本和许可证、检查当前 package-lock、完成 production build、关键页面 Playwright、键盘与屏幕阅读器检查，并记录上游升级策略。真正启动 V4-B 前，必须先冻结 PoC 目录、候选版本、Windows 环境、transport 方案、量测方法和删除条件。任何不满足这些门禁的结果只能写为观察，不得写为兼容、已选型或可发布。
