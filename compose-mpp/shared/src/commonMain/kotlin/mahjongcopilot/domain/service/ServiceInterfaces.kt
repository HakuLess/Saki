package mahjongcopilot.domain.service

import mahjongcopilot.data.model.*

/**
 * 游戏管理服务接口
 */
interface GameManagerService {
    /**
     * 启动游戏管理
     */
    suspend fun startGameManager(): Result<Unit>
    
    /**
     * 停止游戏管理
     */
    suspend fun stopGameManager(): Result<Unit>
    
    /**
     * 检查是否在游戏中
     */
    suspend fun isInGame(): Boolean
    
    /**
     * 获取当前游戏状态
     */
    suspend fun getCurrentGameState(): GameState?
    
    /**
     * 监听游戏状态变化
     */
    fun observeGameState(): kotlinx.coroutines.flow.Flow<GameState?>
    
    /**
     * 监听应用状态变化
     */
    fun observeAppState(): kotlinx.coroutines.flow.Flow<AppState>
}

/**
 * AI 决策服务接口
 */
interface AiDecisionService {
    /**
     * 获取 AI 决策
     */
    suspend fun getDecision(gameState: GameState): Result<AiDecision>
    
    /**
     * 验证决策
     */
    suspend fun validateDecision(decision: AiDecision, gameState: GameState): Result<Boolean>
    
    /**
     * 加载 AI 模型
     */
    suspend fun loadModel(modelConfig: AiModelConfig): Result<Unit>
    
    /**
     * 卸载 AI 模型
     */
    suspend fun unloadModel(): Result<Unit>
    
    /**
     * 检查模型是否已加载
     */
    suspend fun isModelLoaded(): Boolean
    
    /**
     * 获取模型信息
     */
    suspend fun getModelInfo(): AiModelConfig?
    
    /**
     * 监听决策历史
     */
    fun observeDecisionHistory(): kotlinx.coroutines.flow.Flow<List<AiDecision>>
    
    /**
     * 清除决策历史
     */
    suspend fun clearDecisionHistory(): Result<Unit>
}

/**
 * AI 模型管理服务接口
 */
interface AiModelService {
    /**
     * 初始化模型
     */
    suspend fun initializeModels(): Result<Unit>
    
    /**
     * 获取可用模型列表
     */
    suspend fun getAvailableModels(): List<AiModelConfig>
    
    /**
     * 加载模型
     */
    suspend fun loadModel(modelConfig: AiModelConfig): Result<Unit>
    
    /**
     * 卸载模型
     */
    suspend fun unloadModel(): Result<Unit>
    
    /**
     * 获取当前模型
     */
    suspend fun getCurrentModel(): AiModelConfig?
    
    /**
     * 检查模型是否已加载
     */
    suspend fun isModelLoaded(): Boolean
    
    /**
     * 更新模型配置
     */
    suspend fun updateModelConfig(modelConfig: AiModelConfig): Result<Unit>
    
    /**
     * 测试模型连接
     */
    suspend fun testModelConnection(modelConfig: AiModelConfig): Result<Boolean>
    
    /**
     * 监听可用模型变化
     */
    fun observeAvailableModels(): kotlinx.coroutines.flow.Flow<List<AiModelConfig>>
    
    /**
     * 监听当前模型变化
     */
    fun observeCurrentModel(): kotlinx.coroutines.flow.Flow<AiModelConfig?>
}

/**
 * 自动化服务接口
 */
interface AutomationService {
    /**
     * 启用自动化
     */
    suspend fun enableAutomation(): Result<Unit>
    
    /**
     * 禁用自动化
     */
    suspend fun disableAutomation(): Result<Unit>
    
    /**
     * 检查自动化是否启用
     */
    suspend fun isAutomationEnabled(): Boolean
    
    /**
     * 执行自动操作
     */
    suspend fun executeAutomaticAction(decision: AiDecision): Result<Unit>
    
    /**
     * 暂停自动化
     */
    suspend fun pauseAutomation(): Result<Unit>
    
    /**
     * 恢复自动化
     */
    suspend fun resumeAutomation(): Result<Unit>
    
    /**
     * 更新自动化设置
     */
    suspend fun updateSettings(settings: AutomationSettings): Result<Unit>
    
    /**
     * 获取自动化设置
     */
    suspend fun getSettings(): AutomationSettings
    
    /**
     * 获取动作历史
     */
    suspend fun getActionHistory(): List<AutomationAction>
    
    /**
     * 清除动作历史
     */
    suspend fun clearActionHistory(): Result<Unit>
    
    /**
     * 监听自动化状态
     */
    fun observeAutomationState(): kotlinx.coroutines.flow.Flow<AutomationState>
    
    /**
     * 监听动作历史
     */
    fun observeActionHistory(): kotlinx.coroutines.flow.Flow<List<AutomationAction>>
}

/**
 * 网络管理服务接口
 */
interface NetworkManagerService {
    /**
     * 启动网络拦截
     */
    suspend fun startNetworkInterception(): Result<Unit>
    
    /**
     * 停止网络拦截
     */
    suspend fun stopNetworkInterception(): Result<Unit>
    
    /**
     * 检查网络连接状态
     */
    suspend fun isNetworkConnected(): Boolean
    
    /**
     * 获取网络统计信息
     */
    suspend fun getNetworkStatistics(): mahjongcopilot.data.model.NetworkStatistics
    
    /**
     * 监听网络状态变化
     */
    fun observeNetworkStatus(): kotlinx.coroutines.flow.Flow<mahjongcopilot.data.model.NetworkStatus>
}

/**
 * 网络统计信息
 */
data class NetworkStatistics(
    val messagesReceived: Long = 0,
    val messagesSent: Long = 0,
    val bytesReceived: Long = 0,
    val bytesSent: Long = 0,
    val connectionUptime: Long = 0L,
    val lastMessageTime: Long = 0L
)

/**
 * 网络状态
 */
enum class NetworkStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * 配置管理服务接口
 */
interface ConfigurationService {
    /**
     * 加载配置
     */
    suspend fun loadConfiguration(): Result<GameSettings>
    
    /**
     * 保存配置
     */
    suspend fun saveConfiguration(settings: GameSettings): Result<Unit>
    
    /**
     * 重置配置
     */
    suspend fun resetConfiguration(): Result<GameSettings>
    
    /**
     * 验证配置
     */
    suspend fun validateConfiguration(settings: GameSettings): Result<Unit>
    
    /**
     * 监听配置变化
     */
    fun observeConfiguration(): kotlinx.coroutines.flow.Flow<GameSettings>
}

/**
 * 日志服务接口
 */
interface LoggingService {
    /**
     * 记录调试日志
     */
    suspend fun debug(message: String, tag: String? = null)
    
    /**
     * 记录信息日志
     */
    suspend fun info(message: String, tag: String? = null)
    
    /**
     * 记录警告日志
     */
    suspend fun warn(message: String, tag: String? = null, exception: Throwable? = null)
    
    /**
     * 记录错误日志
     */
    suspend fun error(message: String, tag: String? = null, exception: Throwable? = null)
    
    /**
     * 获取日志历史
     */
    suspend fun getLogHistory(level: LogLevel? = null, limit: Int = 100): List<LogEntry>
    
    /**
     * 监听日志
     */
    fun observeLogs(): kotlinx.coroutines.flow.Flow<LogEntry>
}



/**
 * 统计服务接口
 */
interface StatisticsService {
    /**
     * 更新游戏统计
     */
    suspend fun updateGameStatistics(gameResult: GameResult): Result<Unit>
    
    /**
     * 获取游戏统计
     */
    suspend fun getGameStatistics(): GameStatistics
    
    /**
     * 重置统计
     */
    suspend fun resetStatistics(): Result<Unit>
    
    /**
     * 导出统计
     */
    suspend fun exportStatistics(): Result<String>
}

/**
 * 游戏结果
 */
data class GameResult(
    val gameId: String,
    val startTime: Long,
    val endTime: Long,
    val finalScore: Int,
    val rank: Int,
    val aiDecisions: Int,
    val correctDecisions: Int
)
