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
    suspend fun validateDecision(decision: AiDecision, gameState: GameState): Boolean
    
    /**
     * 获取决策历史
     */
    suspend fun getDecisionHistory(): List<AiDecision>
    
    /**
     * 清除决策历史
     */
    suspend fun clearDecisionHistory(): Result<Unit>
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
