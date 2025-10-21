package mahjongcopilot.domain.service

import kotlinx.coroutines.flow.Flow
import mahjongcopilot.data.model.Decision
import mahjongcopilot.data.model.GameState
import mahjongcopilot.data.model.WindowInfo

/**
 * 自动化服务接口 - 管理自动化状态和操作
 */
interface AutomationService {
    /**
     * 自动化状态流
     */
    val automationStateFlow: Flow<AutomationState>
    
    /**
     * 动作历史记录流
     */
    val actionHistoryFlow: Flow<List<AutomationAction>>
    
    /**
     * 启用自动化
     */
    suspend fun enableAutomation()
    
    /**
     * 禁用自动化
     */
    suspend fun disableAutomation()
    
    /**
     * 暂停自动化
     */
    suspend fun pauseAutomation()
    
    /**
     * 恢复自动化
     */
    suspend fun resumeAutomation()
    
    /**
     * 执行自动操作
     * @param decision AI决策
     */
    suspend fun executeAutomaticAction(decision: Decision)
    
    /**
     * 更新自动化设置
     */
    suspend fun updateSettings(settings: AutomationSettings)
    
    /**
     * 获取当前设置
     */
    fun getSettings(): AutomationSettings
}

/**
 * 游戏管理服务接口 - 管理游戏状态和流程
 */
interface GameManagerService {
    /**
     * 游戏状态流
     */
    val gameStateFlow: Flow<GameState?>
    
    /**
     * 客户端状态流
     */
    val clientStateFlow: Flow<ClientState>
    
    /**
     * 启动游戏监控
     */
    suspend fun startMonitoring()
    
    /**
     * 停止游戏监控
     */
    suspend fun stopMonitoring()
    
    /**
     * 查找并连接到雀魂客户端
     */
    suspend fun connectToClient(): WindowInfo?
    
    /**
     * 获取当前客户端信息
     */
    fun getCurrentClientInfo(): WindowInfo?
    
    /**
     * 刷新游戏状态
     */
    suspend fun refreshGameState()
}

/**
 * AI服务接口 - 提供AI决策功能
 */
interface AiService {
    /**
     * AI状态流
     */
    val aiStateFlow: Flow<AiState>
    
    /**
     * 加载AI模型
     * @param modelConfig 模型配置
     */
    suspend fun loadModel(modelConfig: ModelConfig): Boolean
    
    /**
     * 卸载AI模型
     */
    suspend fun unloadModel()
    
    /**
     * 获取AI建议
     * @param gameState 当前游戏状态
     */
    suspend fun getAiSuggestions(gameState: GameState): List<Decision>
    
    /**
     * 分析手牌效率
     * @param handTiles 手牌
     */
    suspend fun analyzeHandEfficiency(handTiles: List<Any>): HandAnalysis
}

/**
 * 网络管理服务接口 - 管理网络代理和拦截
 */
interface NetworkManagerService {
    /**
     * 代理状态流
     */
    val proxyStateFlow: Flow<ProxyState>
    
    /**
     * 启动网络拦截
     */
    suspend fun startNetworkInterception()
    
    /**
     * 停止网络拦截
     */
    suspend fun stopNetworkInterception()
    
    /**
     * 导出证书
     * @param exportPath 导出路径
     */
    suspend fun exportCertificate(exportPath: String): Boolean
    
    /**
     * 测试连接
     */
    suspend fun testConnection(): Boolean
}

/**
 * 配置服务接口 - 管理应用配置
 */
interface ConfigurationService {
    /**
     * 保存配置
     * @param config 配置对象
     */
    suspend fun saveConfiguration(config: AppConfiguration)
    
    /**
     * 加载配置
     */
    suspend fun loadConfiguration(): AppConfiguration
    
    /**
     * 重置配置到默认值
     */
    suspend fun resetConfiguration()
}

/**
 * 日志服务接口 - 提供日志功能
 */
interface LoggingService {
    /**
     * 记录调试信息
     */
    fun debug(tag: String, message: String)
    
    /**
     * 记录普通信息
     */
    fun info(tag: String, message: String)
    
    /**
     * 记录警告信息
     */
    fun warn(tag: String, message: String)
    
    /**
     * 记录错误信息
     */
    fun error(tag: String, message: String, throwable: Throwable? = null)
    
    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String
}

/**
 * 统计服务接口 - 提供统计功能
 */
interface StatisticsService {
    /**
     * 记录对局结果
     */
    suspend fun recordGameResult(result: GameResult)
    
    /**
     * 获取胜率统计
     */
    suspend fun getWinRateStatistics(): WinRateStats
    
    /**
     * 获取AI决策准确率
     */
    suspend fun getAiAccuracy(): Double
}

// 辅助数据类
sealed class AutomationState {
    object Disabled : AutomationState()
    object Enabled : AutomationState()
    object Paused : AutomationState()
}

data class AutomationAction(
    val timestamp: Long,
    val type: String,
    val description: String,
    val success: Boolean
)

data class AutomationSettings(
    val autoDiscard: Boolean = true,
    val autoMeld: Boolean = true,
    val autoRiichi: Boolean = true,
    val autoAgari: Boolean = true,
    val actionDelayMs: Long = 500,
    val decisionConfidenceThreshold: Double = 0.6
)

sealed class ClientState {
    object NotConnected : ClientState()
    data class Connected(val windowInfo: WindowInfo) : ClientState()
    object Error : ClientState()
}

sealed class AiState {
    object NotLoaded : AiState()
    object Loading : AiState()
    object Loaded : AiState()
    data class Error(val message: String) : AiState()
}

data class ModelConfig(
    val type: ModelType,
    val path: String,
    val parameters: Map<String, Any> = emptyMap()
)

enum class ModelType {
    LOCAL,
    ONLINE
}

data class HandAnalysis(
    val efficiency: Double,
    val bestDiscard: List<Any>,
    val tenpaiDistance: Int,
    val yakuPossibilities: List<YakuInfo>
)

data class YakuInfo(
    val name: String,
    val possibility: Double,
    val han: Int
)

sealed class ProxyState {
    object NotRunning : ProxyState()
    object Starting : ProxyState()
    object Running : ProxyState()
    object Stopping : ProxyState()
    data class Error(val message: String) : ProxyState()
}

data class AppConfiguration(
    val automation: AutomationSettings = AutomationSettings(),
    val proxy: ProxySettings = ProxySettings(),
    val ai: AiSettings = AiSettings(),
    val ui: UISettings = UISettings(),
    val client: ClientSettings = ClientSettings()
)

data class ProxySettings(
    val enabled: Boolean = true,
    val port: Int = 7880,
    val autoConfigureSystemProxy: Boolean = true
)

data class AiSettings(
    val modelType: ModelType = ModelType.LOCAL,
    val modelPath: String = "",
    val onlineApiKey: String = "",
    val onlineApiUrl: String = ""
)

data class UISettings(
    val theme: Theme = Theme.LIGHT,
    val language: String = "zh-CN",
    val showHud: Boolean = true,
    val hudOpacity: Float = 0.8f
)

enum class Theme {
    LIGHT,
    DARK,
    AUTO
}

data class ClientSettings(
    val clientType: ClientType = ClientType.WEB,
    val windowTitle: String = "雀魂",
    val processName: String = "MahjongSoul"
)

enum class ClientType {
    WEB,
    DESKTOP
}

data class GameResult(
    val timestamp: Long,
    val gameId: String,
    val playerPosition: Int,
    val score: Int,
    val ranking: Int,
    val win: Boolean,
    val winType: String? = null,
    val aiSuggestionsCount: Int,
    val aiCorrectSuggestionsCount: Int
)

data class WinRateStats(
    val totalGames: Int,
    val wonGames: Int,
    val winRate: Double,
    val averageScore: Double,
    val averageRanking: Double
)