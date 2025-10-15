# MahjongCopilot 功能Wiki（参考原项目，KMP 版规划）

- 目标：为雀魂（Majsoul）提供 AI 对局指导与自动化能力。

**核心功能**
- AI 步进指导：在对局每一步给出操作建议（HUD 叠加显示，或在桌面 GUI 展示）。
- 自动化：自动打牌、自动加入下一局、支持自动和牌。
- 多语言支持：界面与提示文本多语言。
- 模型支持：本地 Mortal 模型（兼容 Akagi）、在线 AkagiOT、在线 MJAPI。
- 对局模式：支持三麻与四麻。
- 浏览器控制：可自动启动、设置窗口尺寸。

**集成与基础设施**
- 浏览器控制：Playwright + Chromium 驱动网页端客户端。
- HUD 叠加：在网页画布上实时渲染 AI 指导与选项列表。
- 抓包与协议：
  - MITM 代理（自定义端口与上游代理）。
  - 进程注入 proxinject（可选）。
  - Chrome 扩展（可选）。
  - Liqi 协议解析（`liqi.py`、`liqi_proto`）。
- 机器人管理：`BotManager` 统一管理游戏状态、自动化与 HUD 更新，支持批量反应。
- 更新机制：从站点拉取更新包与帮助文档（`updater.py`）。

**设置项（GUI Settings）**
- 通用：自动启动浏览器、窗口宽高、Majsoul URL、显示语言。
- 扩展：启用 Chrome 扩展。
- 网络：MITM 端口、上游代理、启用 proxinject。
- 模型来源与选择：
  - Local：选择 4p/3p 模型文件（Mortal/Akagi 兼容）。
  - AkagiOT：服务地址与 APIKey。
  - MJAPI：服务地址、用户/密钥、模型选择、用量信息。
- 自动化细节：
  - 随机鼠标移动、空闲移动、拖拽打牌。
  - AI 决策随机化强度。
  - 回复表情概率。
  - 随机延迟范围（下界/上界）。

**模块与目录（原项目映射）**
- `gui`：桌面 UI（tkinter）——设置窗口、帮助窗口、主界面与控件。
- `game`：浏览器控制、状态解析、自动化操作（点击、拖拽、滚轮、自动和）。
- `bot`：AI 机器人（Local Mortal / AkagiOT / MJAPI）。
- `common`：语言、多平台常量、日志、设置与工具。
- `liqi_proto` / `liqi.py`：雀魂协议及解析。
- `mitm.py` / `proxinject.py` / `crx`：网络劫持/进程注入/浏览器扩展。
- `updater.py`：更新检查与拉取。

**运行与开发（原项目）**
- 依赖环境：Python 3.11、`requirements.txt`。
- 浏览器：`playwright install chromium`。
- 入口：`main.py`。

**Saki（KMP 版）对齐与优化方向**
- 界面：采用 Compose Multiplatform 实现桌面 UI 与跨平台适配。
- 浏览器控制：替换 Playwright，评估 KMP 可用 WebView/嵌入方案。
- 网络层：在 KMP 中实现 Liqi 协议解析与消息流转；评估 MITM/注入替代实现。
- 模型接入：优先对接在线模型（MJAPI/AkagiOT）；本地 Mortal 模型可通过 JNI/FFI 后续接入。
- HUD 叠加：在浏览器画布/前端扩展中实现 KMP 兼容的叠加层。
- 设置与持久化：统一配置模型与自动化选项，提供跨平台存储。
- 增量迁移：边对齐功能边替换模块，确保始终可运行。

**备注**
- 本 Wiki 仅在 Saki 项目维护；不改动原 `MahjongCopilot` 仓库。