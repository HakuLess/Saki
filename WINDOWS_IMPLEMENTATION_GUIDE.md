# Windows平台实现指南

## 概述
本文档详细说明了如何在Windows平台上实现Saki麻将助手的真实网络拦截和自动打牌功能。

## 技术选型

### 网络拦截技术
1. **mitmproxy**: 使用与MahjongCopilot相同的网络拦截技术
   - 支持HTTP/HTTPS流量拦截
   - 支持WebSocket协议解析
   - 可自定义脚本处理特定流量
2. **证书安装**: 自动安装和管理MITM证书

### 输入模拟技术
1. **Windows API (user32.dll)**: 
   - `mouse_event`/`SendInput` 用于鼠标模拟
   - `keybd_event` 用于键盘模拟
2. **JNA (Java Native Access)**: 在Kotlin中调用Windows API

### 窗口管理技术
1. **Windows API**:
   - `FindWindow`/`FindWindowEx` 查找雀魂客户端窗口
   - `GetWindowRect` 获取窗口位置和大小
   - `SetForegroundWindow` 将窗口置于前台

## 核心模块实现

### 1. MitmNetworkInterceptor 实现

#### 依赖库
```kotlin
// 在build.gradle.kts中添加
implementation("com.sun.jna:jna:5.13.0")
implementation("com.sun.jna:jna-platform:5.13.0")
```

#### 核心实现步骤
1. 启动mitmproxy进程
2. 配置拦截规则，只拦截雀魂相关域名
3. 使用自定义脚本处理WebSocket消息
4. 将解析结果转换为LiqiMessage并发射到Flow

#### 支持的雀魂域名
- maj-soul.com
- majsoul.com
- mahjongsoul.com
- yo-star.com

#### 示例代码框架
```kotlin
class MitmNetworkInterceptor : NetworkInterceptorRepository {
    private var mitmProcess: Process? = null
    private var isCapturing = false
    
    override suspend fun startInterception(settings: NetworkSettings): Result<Unit> {
        return try {
            // 1. 启动mitmproxy进程
            startMitmProxy()
            
            // 2. 开始轮询消息
            startMessagePolling()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun startMitmProxy() {
        // 1. 创建配置目录
        // 2. 构建mitmproxy命令
        // 3. 启动进程
    }
    
    private fun startMessagePolling() {
        // 启动协程轮询消息队列
        // 处理捕获到的消息
    }
}
```

### 2. WindowsInputSimulator 实现

#### 核心实现步骤
1. 查找雀魂客户端窗口
2. 计算游戏内坐标到屏幕坐标的转换
3. 实现鼠标点击、拖拽等操作
4. 实现键盘按键模拟

#### 示例代码框架
```kotlin
class WindowsInputSimulator {
    private val user32 = User32.INSTANCE
    
    fun clickAt(x: Int, y: Int, delayMs: Long = 50) {
        // 1. 设置鼠标位置
        val input = createMouseMoveInput(x, y)
        user32.SendInput(1, arrayOf(input), input.size())
        
        // 2. 鼠标按下
        val mouseDown = createMouseInput(MouseEventFlags.LEFTDOWN)
        user32.SendInput(1, arrayOf(mouseDown), mouseDown.size())
        
        // 3. 延迟
        Thread.sleep(delayMs)
        
        // 4. 鼠标释放
        val mouseUp = createMouseInput(MouseEventFlags.LEFTUP)
        user32.SendInput(1, arrayOf(mouseUp), mouseUp.size())
    }
    
    fun getMajsoulWindowRect(): WindowRect? {
        // 1. 查找雀魂窗口
        val hwnd = user32.FindWindow(null, "雀魂")
        if (hwnd == null) return null
        
        // 2. 获取窗口位置和大小
        val rect = WinDef.RECT()
        user32.GetWindowRect(hwnd, rect)
        
        return WindowRect(
            x = rect.left,
            y = rect.top,
            width = rect.right - rect.left,
            height = rect.bottom - rect.top
        )
    }
    
    private fun createMouseMoveInput(x: Int, y: Int): WinUser.INPUT {
        // 创建鼠标移动输入事件
    }
    
    private fun createMouseInput(flags: MouseEventFlags): WinUser.INPUT {
        // 创建鼠标输入事件
    }
}
```

### 3. WindowsAutomationService 实现

#### 核心实现步骤
1. 根据AI决策确定要执行的操作
2. 计算操作在游戏窗口中的位置
3. 调用WindowsInputSimulator执行操作
4. 处理操作结果和异常

#### 示例代码框架
```kotlin
class WindowsAutomationService : AutomationService {
    private val inputSimulator = WindowsInputSimulator()
    private val gamePositions = GamePositions()
    
    private fun executeDiscardAction(action: MjaiAction) {
        // 1. 获取雀魂窗口位置
        val windowRect = inputSimulator.getMajsoulWindowRect()
        if (windowRect == null) return
        
        // 2. 计算要切的牌在窗口中的位置
        val tilePosition = calculateTilePosition(action.pai, windowRect)
        
        // 3. 执行点击操作
        inputSimulator.clickAt(tilePosition.x, tilePosition.y)
        
        // 4. 等待片刻
        Thread.sleep(200)
        
        // 5. 点击discard按钮或拖拽到discard区域
        val discardPosition = gamePositions.actionButtons["discard"]
        if (discardPosition != null) {
            val screenPos = convertToScreenPosition(discardPosition, windowRect)
            inputSimulator.clickAt(screenPos.x, screenPos.y)
        }
    }
    
    private fun calculateTilePosition(tile: String?, windowRect: WindowRect): ScreenPoint {
        // 根据手牌位置计算牌的屏幕坐标
        // 这需要根据雀魂客户端的UI布局来确定
    }
    
    private fun convertToScreenPosition(position: Position, windowRect: WindowRect): ScreenPoint {
        // 将游戏内相对坐标转换为屏幕绝对坐标
        return ScreenPoint(
            x = windowRect.x + (position.x * windowRect.width).toInt(),
            y = windowRect.y + (position.y * windowRect.height).toInt()
        )
    }
}
```

## 坐标系统设计

### 游戏内坐标系统
使用相对坐标系统（0.0 - 1.0）表示游戏窗口内的位置：
- (0.0, 0.0): 窗口左上角
- (1.0, 1.0): 窗口右下角
- (0.5, 0.5): 窗口中心

### 坐标转换流程
```
游戏内相对坐标 (0.0-1.0) 
    ↓ (根据窗口位置和大小)
屏幕绝对坐标 (像素值)
    ↓ (调用Windows API)
实际鼠标位置
```

## 雀魂客户端UI分析

### 手牌位置
- 手牌区域通常位于窗口底部
- 每张牌的宽度约为窗口宽度的 5-7%
- 牌与牌之间有重叠

### 操作按钮位置
- 吃、碰、杠、和等按钮通常出现在窗口中央偏下位置
- 立直按钮通常在窗口右侧
- 切牌后确认按钮在窗口中央

### 候选牌选择
- 吃牌时的候选牌组合显示在窗口下方
- 碰/杠时的候选牌显示在相应按钮旁边

## 安全性和反作弊考虑

### 操作随机化
1. 添加随机延迟避免固定模式
2. 鼠标移动轨迹模拟人类操作
3. 操作位置添加微小随机偏移

### 检测规避
1. 避免过于频繁的操作
2. 模拟人类思考时间
3. 在适当时候添加"错误"操作再纠正

## 性能优化

### 网络拦截优化
1. 使用高效的包过滤器减少处理负担
2. 异步处理网络数据包
3. 缓存解析结果避免重复解析

### 输入模拟优化
1. 批量处理连续操作
2. 预计算常用坐标位置
3. 使用对象池减少GC压力

## 错误处理和恢复

### 网络拦截错误
1. mitmproxy进程启动失败时的重试机制
2. 证书安装失败的降级处理
3. 数据包解析错误的日志记录

### 输入模拟错误
1. 窗口找不到时的重试机制
2. 操作执行失败的重试和报警
3. 游戏状态不一致时的恢复机制

## 测试策略

### 单元测试
1. 坐标转换函数测试
2. 协议解析函数测试
3. 操作执行逻辑测试

### 集成测试
1. 网络拦截完整流程测试
2. 输入模拟完整流程测试
3. 自动化服务集成测试

### 性能测试
1. 网络拦截性能基准测试
2. 输入模拟响应时间测试
3. 内存和CPU使用率监控

## 部署和分发

### 依赖项打包
1. mitmproxy及其依赖
2. 必要的Windows DLL文件
3. AI模型文件

### 安装程序
1. 自动安装mitmproxy
2. 注册必要的系统服务
3. 配置防火墙和安全软件例外

## 后续扩展

### 功能扩展
1. 支持更多麻将平台
2. 添加统计分析功能
3. 实现云端配置同步

### 技术优化
1. 使用机器学习优化操作决策
2. 改进UI界面和用户体验
3. 支持多语言界面

## 总结
Windows平台的实现需要结合底层系统API和高级业务逻辑，通过合理的架构设计和错误处理机制，可以实现稳定可靠的自动打牌功能。在开发过程中需要特别注意安全性和反作弊问题，确保用户能够安全地使用该功能。

使用mitmproxy作为网络拦截技术与MahjongCopilot保持一致，确保了技术方案的成熟性和可靠性，同时也降低了开发和维护成本。