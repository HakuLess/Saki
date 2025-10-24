# MahjongSoul/Tenhou 拦截实现差异与修复记录

## 背景
- 现状：桌面代理存在未启用的系统代理设置、MITM 未完成、WebSocket 文本帧处理被注释、HTTPS CONNECT 仅做隧道转发。
- 目标：对比 MahjongCopilot，恢复/补齐拦截关键路径，确保能识别游戏数据并送入分析器。

## 核心差异
- WebSocket 文本帧处理
  - 之前：`DesktopWebSocketProxy.forwardWebSocketData` 未调用 `processTextMessage`；无法从文本消息中提取游戏数据。
  - 修复：恢复文本帧处理，调用 `BaseWebSocketProxy.processTextMessage`，支持天凤 XML/Liqi 文本信息的检测。

- 二进制帧处理（Liqi/Protobuf）
  - 之前：仅裸转发，未调用 `processMessage`；`ProtobufDecoder.kt` 提示为简化实现。
  - 修复：在代理中调用 `processMessage(payload)`；保留兼容转发。后续需完善 `ProtobufDecoder` 贴合真实 Liqi 协议。

- 系统代理设置（Windows）
  - 之前：系统代理设置/恢复被注释，且 Java System Properties 无法影响浏览器/WinINet。
  - 修复：新增基于 HKCU 注册表的代理接管：保存原值、在启动时设置 `ProxyEnable/ProxyServer/ProxyOverride`，停止时恢复。
  - 说明：修改 HKCU 不需要管理员。若客户端使用 WinHTTP，还需考虑 `netsh winhttp set proxy`（非本次改动）。

- HTTPS CONNECT/MITM
  - 现状：`CONNECT` 仅建立 TCP 隧道，未进行 TLS 解密；证书生成/安装代码路径未实现。
  - 影响：对于 `wss://` 场景无法读取明文帧，需完成 MITM 才能拦截。
  - 后续：按 `App.kt` 说明实现证书生成、安装（Windows 证书存储/受信 CA），并在 CONNECT 后切入 MITM 会话。

- Kotlin-Python 桥接与端口
  - 说明：`DesktopPythonService` 使用独立 WebSocket 连接传送 `GameState`；`mahjong_analyzer.py` 内 `GameDataServer`/`BridgeServer` 分工明确，当前无端口冲突迹象。
  - 建议：统一端口配置到 `config`，避免后续扩展冲突；在代理侧区分源站域名做数据路由。

## 已完成修复
- 启用 WebSocket 文本与二进制帧处理，并保持透明转发。
- 接入 Windows 系统代理设置与恢复到启动/停止流程。
- 在 HTTPS CONNECT 阶段增加目标主机日志，便于排查与后续 SNI 分类。

## 待办事项（后续）
- 完成 TLS MITM：证书生成、安装、动态伪造站点证书、在 CONNECT 后接入解密管线。
- 丰富 `ProtobufDecoder.kt` 以完全支持 Liqi 协议消息。
- 增加域名白/黑名单与精确匹配（MahjongSoul/Tenhou），降低副作用。
- 将端口、域名列表、拦截策略下沉到配置文件并提供 UI 切换。