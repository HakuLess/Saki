package mahjongcopilot.data.repository

import kotlinx.coroutines.flow.Flow
import mahjongcopilot.data.model.Decision
import mahjongcopilot.data.model.GameState
import mahjongcopilot.data.model.WindowInfo

/**
 * 自动化仓库接口 - 负责进程监测和输入模拟
 */
interface AutomationRepository {
    /**
     * 查找目标窗口信息
     * @param windowTitle 窗口标题关键词
     * @param processName 进程名关键词
     * @return 窗口信息，如果未找到返回null
     */
    suspend fun findTargetWindow(windowTitle: String = "雀魂", processName: String = ""): WindowInfo?
    
    /**
     * 监控目标进程是否正在运行
     * @param processName 进程名
     * @return 进程运行状态的Flow
     */
    fun monitorProcess(processName: String = "MahjongSoul"): Flow<Boolean>
    
    /**
     * 模拟鼠标点击
     * @param windowHandle 窗口句柄
     * @param x 相对X坐标
     * @param y 相对Y坐标
     * @return 是否成功
     */
    suspend fun simulateMouseClick(windowHandle: Long, x: Int, y: Int): Boolean
    
    /**
     * 模拟键盘输入
     * @param windowHandle 窗口句柄
     * @param key 按键码
     * @param delayMs 按键延迟
     * @return 是否成功
     */
    suspend fun simulateKeyPress(windowHandle: Long, key: String, delayMs: Long = 100): Boolean
    
    /**
     * 获取屏幕截图
     * @param windowHandle 窗口句柄
     * @return 截图数据，如果失败返回null
     */
    suspend fun captureWindow(windowHandle: Long): ByteArray?
}

/**
 * 游戏状态仓库接口 - 负责游戏状态管理
 */
interface GameStateRepository {
    /**
     * 获取当前游戏状态
     */
    val gameStateFlow: Flow<GameState?>
    
    /**
     * 更新游戏状态
     */
    suspend fun updateGameState(newState: GameState)
    
    /**
     * 重置游戏状态
     */
    suspend fun resetGameState()
    
    /**
     * 保存游戏记录
     */
    suspend fun saveGameRecord(gameState: GameState)
}

/**
 * AI决策仓库接口 - 负责AI决策生成
 */
interface AiDecisionRepository {
    /**
     * 加载AI模型
     * @param modelPath 模型路径
     * @return 是否成功加载
     */
    suspend fun loadModel(modelPath: String): Boolean
    
    /**
     * 卸载AI模型
     */
    suspend fun unloadModel()
    
    /**
     * 获取AI决策
     * @param gameState 当前游戏状态
     * @return 决策列表，按优先级排序
     */
    suspend fun getDecision(gameState: GameState): List<Decision>
    
    /**
     * 检查模型是否已加载
     */
    fun isModelLoaded(): Boolean
}

/**
 * 网络仓库接口 - 负责网络代理和协议解析
 */
interface NetworkRepository {
    /**
     * 启动代理服务器
     * @param port 端口号
     * @return 是否成功启动
     */
    suspend fun startProxyServer(port: Int = 7880): Boolean
    
    /**
     * 停止代理服务器
     */
    suspend fun stopProxyServer()
    
    /**
     * 检查代理服务器状态
     */
    fun isProxyRunning(): Boolean
    
    /**
     * 设置系统代理
     * @param enable 是否启用
     * @param host 代理主机
     * @param port 代理端口
     * @return 是否成功
     */
    suspend fun setSystemProxy(enable: Boolean, host: String = "127.0.0.1", port: Int = 7880): Boolean
}