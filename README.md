# Saki
天才麻将少女Saki，借助 Akagi 模型，玩雀魂

## 项目介绍
Saki是一个基于Kotlin Multiplatform的智能麻将助手项目，支持雀魂麻将游戏。该项目基于MahjongCopilot项目进行扩展，增加了Windows客户端自动打牌功能。

## 功能特性
- 实时AI决策支持
- 手牌可视化展示
- Windows客户端自动打牌
- 网络通信拦截与分析（使用mitmproxy实现）
- 多种AI模型支持（本地模型和在线模型）

## 技术架构
- **开发语言**: Kotlin
- **UI框架**: Compose Multiplatform
- **网络通信**: Ktor
- **序列化**: Kotlinx Serialization
- **异步处理**: Kotlin Coroutines
- **网络拦截**: mitmproxy
- **平台支持**: Windows (计划支持Android/iOS)

## 项目结构
```
Saki/
├── compose-mpp/           # Kotlin Multiplatform项目
│   ├── shared/           # 共享代码模块
│   │   ├── common/       # 公共工具类
│   │   ├── data/         # 数据模型和仓库
│   │   ├── domain/       # 业务逻辑和服务
│   │   └── presentation/ # UI界面组件
│   │   └── platform/     # 平台特定实现
│   ├── desktopApp/       # Windows桌面应用
│   └── ...
├── models/               # AI模型文件
└── ...
```

## Windows客户端功能扩展
本项目在MahjongCopilot基础上进行了扩展，增加了以下功能：

### 1. Windows客户端自动打牌
- 使用Windows API实现输入模拟
- 支持鼠标点击、拖拽等操作
- 自动识别雀魂客户端窗口位置

### 2. 真实网络请求拦截
- 使用mitmproxy技术捕获真实网络通信
- 支持WebSocket协议解析
- 拦截URL与MahjongCopilot保持一致：
  - maj-soul.com
  - majsoul.com
  - mahjongsoul.com
  - yo-star.com

### 3. 手牌实时展示
- 实时解析游戏状态
- 可视化展示当前手牌
- 显示吃碰杠等面子信息

## 运行项目
1. 确保已安装mitmproxy：`pip install mitmproxy`
2. 进入compose-mpp目录
3. 运行命令: `.\gradlew.bat :desktopApp:run`

## 开发计划
详细开发计划请参考 [KMP MahjongCopilot 工作计划](KMP_MahjongCopilot_WorkPlan.md)

## 许可证
本项目基于GPLv3许可证开源