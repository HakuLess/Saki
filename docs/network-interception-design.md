# 雀魂网络请求拦截功能设计文档

## 1. 项目概述

本文档描述了在Kotlin Multiplatform环境中实现雀魂网络请求拦截功能的设计方案。该功能将允许应用程序拦截雀魂游戏的网络通信，提取游戏状态和手牌信息，并在UI中展示。

## 2. 系统架构

系统将采用以下分层架构：

1. **代理层**：负责拦截和转发网络请求
2. **协议解析层**：解析雀魂的网络协议和数据格式
3. **数据模型层**：定义游戏状态、手牌等数据结构
4. **状态管理层**：管理和更新游戏状态
5. **UI层**：展示游戏状态和手牌信息

## 3. 网络请求拦截机制

### 3.1 代理服务器实现

使用Ktor实现HTTPS代理服务器，监听本地端口（默认7880）。代理服务器将：

- 拦截雀魂游戏的HTTP和WebSocket请求
- 转发请求到实际服务器
- 捕获响应并进行分析
- 将原始响应返回给客户端

### 3.2 证书管理

- 生成自签名CA证书
- 提供证书安装指南
- 在运行时动态生成针对特定域名的证书

### 3.3 流量重定向

- 桌面平台：提供配置系统代理的功能
- 可选：集成Proxifier等工具的配置指南

## 4. 协议解析

### 4.1 WebSocket消息解析

雀魂使用WebSocket进行实时游戏数据传输，消息格式为Protobuf。需要：

- 解析WebSocket帧
- 提取Protobuf消息
- 根据消息类型进行处理

### 4.2 关键API和消息类型

需要拦截和解析的关键消息：

1. **游戏初始化**：
   - 对局信息（局数、场风等）
   - 初始手牌

2. **游戏进行中**：
   - 摸牌动作
   - 打牌动作
   - 吃碰杠动作
   - 立直宣言

3. **游戏结束**：
   - 和牌信息
   - 得分计算

## 5. 数据模型

### 5.1 核心数据类

```kotlin
// 麻将牌
data class Tile(
    val suit: Suit,  // 万、筒、索、字牌
    val value: Int,  // 1-9或字牌值
    val dora: Boolean = false,
    val red: Boolean = false  // 赤宝牌
)

// 游戏状态
data class GameState(
    val round: Int,          // 局数
    val honba: Int,          // 本场数
    val deposit: Int,        // 场棒数
    val playerWind: Wind,    // 自风
    val roundWind: Wind,     // 场风
    val activePlayer: Int,   // 当前行动玩家
    val remainingTiles: Int, // 剩余牌数
    val scores: List<Int>,   // 各玩家分数
    val handTiles: List<Tile>, // 手牌
    val discardedTiles: List<List<Tile>>, // 各玩家的弃牌
    val openMelds: List<Meld>, // 副露
    val doras: List<Tile>    // 宝牌指示牌
)

// 副露（吃碰杠）
data class Meld(
    val type: MeldType,      // 吃、碰、明杠、暗杠
    val tiles: List<Tile>,   // 副露中的牌
    val calledTile: Tile?,   // 被鸣的牌
    val calledFrom: Int      // 被鸣的玩家
)
```

### 5.2 状态管理

使用Kotlin Flow管理游戏状态：

```kotlin
class GameStateManager {
    private val _gameStateFlow = MutableStateFlow<GameState?>(null)
    val gameStateFlow: StateFlow<GameState?> = _gameStateFlow
    
    fun updateGameState(update: (GameState?) -> GameState?) {
        _gameStateFlow.update(update)
    }
}
```

## 6. 实现计划和工作步骤

### 阶段一：基础设施搭建

1. **设置项目依赖**
   - 添加Ktor客户端和服务器依赖
   - 添加Protobuf依赖
   - 配置多平台构建

2. **实现基本代理服务器**
   - 创建HTTPS代理服务器
   - 实现证书生成和管理
   - 测试基本请求拦截功能

### 阶段二：协议解析

3. **WebSocket拦截**
   - 实现WebSocket连接的拦截
   - 解析WebSocket帧

4. **Protobuf解析**
   - 定义Protobuf消息结构
   - 实现消息解析器

5. **游戏状态提取**
   - 从消息中提取游戏状态信息
   - 更新数据模型

### 阶段三：UI实现

6. **状态管理集成**
   - 将解析的游戏状态连接到状态管理器
   - 实现状态更新逻辑

7. **UI组件开发**
   - 设计游戏状态显示组件
   - 实现手牌显示组件
   - 创建牌河显示组件

### 阶段四：测试和优化

8. **功能测试**
   - 测试不同游戏场景下的状态提取
   - 验证UI显示的准确性

9. **性能优化**
   - 优化代理服务器性能
   - 减少内存使用和CPU负载

10. **用户体验改进**
    - 添加配置选项
    - 改进错误处理和恢复机制

## 7. 技术挑战和解决方案

### 7.1 证书信任问题

**挑战**：自签名证书需要被系统和应用信任。
**解决方案**：提供详细的证书安装指南，可能的话实现自动安装功能。

### 7.2 协议变更适应

**挑战**：雀魂可能更新其协议和数据格式。
**解决方案**：设计灵活的解析器，能够适应小的协议变化；监控版本更新。

### 7.3 跨平台兼容性

**挑战**：不同平台的网络代理配置方式不同。
**解决方案**：为每个平台实现特定的代理配置代码，使用expect/actual模式。

## 8. 未来扩展

1. 支持更多麻将平台（天凤、麻雀一番街等）
2. 添加AI分析和建议功能
3. 实现自动打牌功能
4. 添加游戏记录和回放功能

## 9. 参考资源

- Akagi项目的MITM实现
- MahjongCopilot的网络拦截机制
- 雀魂API文档和协议分析
- Ktor代理服务器文档