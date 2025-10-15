package mahjongcopilot.domain.repository

import mahjongcopilot.data.model.*

/**
 * AI 模型仓库接口
 */
interface AiModelRepository {
    /**
     * 加载模型
     */
    suspend fun loadModel(config: AiModelConfig): Result<Unit>
    
    /**
     * 卸载模型
     */
    suspend fun unloadModel(): Result<Unit>
    
    /**
     * 获取决策
     */
    suspend fun getDecision(gameState: GameState): Result<AiDecision>
    
    /**
     * 检查模型是否已加载
     */
    suspend fun isModelLoaded(): Boolean
    
    /**
     * 获取支持的模型列表
     */
    suspend fun getAvailableModels(): List<AiModelConfig>
}

/**
 * 游戏状态仓库接口
 */
interface GameStateRepository {
    /**
     * 更新游戏状态
     */
    suspend fun updateGameState(message: LiqiMessage): Result<GameState>
    
    /**
     * 获取当前游戏状态
     */
    suspend fun getCurrentGameState(): GameState?
    
    /**
     * 清除游戏状态
     */
    suspend fun clearGameState(): Result<Unit>
    
    /**
     * 监听游戏状态变化
     */
    fun observeGameState(): kotlinx.coroutines.flow.Flow<GameState?>
}

/**
 * 网络拦截仓库接口
 */
interface NetworkInterceptorRepository {
    /**
     * 启动网络拦截
     */
    suspend fun startInterception(settings: NetworkSettings): Result<Unit>
    
    /**
     * 停止网络拦截
     */
    suspend fun stopInterception(): Result<Unit>
    
    /**
     * 监听网络消息
     */
    fun observeNetworkMessages(): kotlinx.coroutines.flow.Flow<LiqiMessage>
    
    /**
     * 检查拦截状态
     */
    suspend fun isInterceptionActive(): Boolean
}

/**
 * 协议解析仓库接口
 */
interface ProtocolParserRepository {
    /**
     * 解析 Liqi 消息
     */
    suspend fun parseLiqiMessage(data: ByteArray): Result<LiqiMessage>
    
    /**
     * 转换 Liqi 消息为 MJAI 消息
     */
    suspend fun convertToMjai(liqiMessage: LiqiMessage): Result<MjaiMessage>
    
    /**
     * 转换 MJAI 动作为游戏操作
     */
    suspend fun convertToGameOperation(mjaiAction: MjaiAction): Result<GameOperation>
}

/**
 * 自动化控制仓库接口
 */
interface AutomationRepository {
    /**
     * 执行游戏操作
     */
    suspend fun executeOperation(operation: GameOperation): Result<Unit>
    
    /**
     * 检查操作是否可用
     */
    suspend fun isOperationAvailable(operation: GameOperation): Boolean
    
    /**
     * 获取目标窗口信息
     */
    suspend fun getTargetWindowInfo(): Result<WindowInfo>
    
    /**
     * 模拟鼠标点击
     */
    suspend fun simulateClick(x: Int, y: Int): Result<Unit>
    
    /**
     * 模拟键盘输入
     */
    suspend fun simulateKeyPress(keyCode: Int): Result<Unit>
}

/**
 * 窗口信息
 */
data class WindowInfo(
    val title: String,
    val processName: String,
    val windowHandle: Long,
    val position: Pair<Int, Int>,
    val size: Pair<Int, Int>
)

/**
 * 设置仓库接口
 */
interface SettingsRepository {
    /**
     * 保存设置
     */
    suspend fun saveSettings(settings: GameSettings): Result<Unit>
    
    /**
     * 加载设置
     */
    suspend fun loadSettings(): Result<GameSettings>
    
    /**
     * 重置设置为默认值
     */
    suspend fun resetToDefaults(): Result<GameSettings>
    
    /**
     * 监听设置变化
     */
    fun observeSettings(): kotlinx.coroutines.flow.Flow<GameSettings>
}

/**
 * 日志仓库接口
 */
interface LogRepository {
    /**
     * 记录日志
     */
    suspend fun log(entry: LogEntry): Result<Unit>
    
    /**
     * 获取日志历史
     */
    suspend fun getLogHistory(limit: Int = 100): List<LogEntry>
    
    /**
     * 清除日志
     */
    suspend fun clearLogs(): Result<Unit>
    
    /**
     * 监听日志
     */
    fun observeLogs(): kotlinx.coroutines.flow.Flow<LogEntry>
}
