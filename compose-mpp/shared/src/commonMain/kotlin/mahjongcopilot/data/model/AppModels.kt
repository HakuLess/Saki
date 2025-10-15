package mahjongcopilot.data.model

/**
 * AI 模型类型
 */
enum class AiModelType {
    LOCAL,      // 本地模型
    AKAGI_OT,   // Akagi 在线模型
    MJAPI       // MJAPI 在线服务
}

/**
 * AI 模型配置
 */
data class AiModelConfig(
    val type: AiModelType,
    val name: String,
    val modelPath: String? = null,
    val apiUrl: String? = null,
    val apiKey: String? = null,
    val isEnabled: Boolean = true,
    val supportedModes: List<GameMode> = listOf(GameMode.FOUR_PLAYER)
)

/**
 * AI 决策结果
 */
data class AiDecision(
    val action: MjaiAction,
    val confidence: Float,
    val reasoning: String? = null,
    val alternatives: List<AiDecision>? = null,
    val processingTime: Long = 0L
)

/**
 * 游戏设置
 */
data class GameSettings(
    val autoPlay: Boolean = false,
    val showOverlay: Boolean = true,
    val autoJoin: Boolean = false,
    val language: String = "zh-CN",
    val aiModel: AiModelConfig,
    val networkSettings: NetworkSettings,
    val automationSettings: AutomationSettings
)

/**
 * 网络设置
 */
data class NetworkSettings(
    val mitmPort: Int = 10999,
    val upstreamProxy: String? = null,
    val enableProxyInject: Boolean = false,
    val injectProcessName: String = "jantama_mahjongsoul"
)

/**
 * 自动化设置
 */
data class AutomationSettings(
    val autoDiscard: Boolean = true,
    val autoCall: Boolean = true,
    val autoReach: Boolean = false,
    val autoAgari: Boolean = true,
    val actionDelay: Long = 1000L,  // 动作延迟（毫秒）
    val humanLikeDelay: Boolean = true
)

/**
 * 应用状态
 */
data class AppState(
    val isConnected: Boolean = false,
    val isInGame: Boolean = false,
    val currentGame: GameState? = null,
    val lastDecision: AiDecision? = null,
    val errorMessage: String? = null,
    val isProcessing: Boolean = false
)

/**
 * 日志级别
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * 日志条目
 */
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String,
    val tag: String? = null,
    val exception: String? = null
)

/**
 * 统计信息
 */
data class GameStatistics(
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val averageScore: Float = 0f,
    val totalPlayTime: Long = 0L,
    val aiDecisions: Int = 0,
    val correctDecisions: Int = 0
)

enum class NetworkStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class NetworkStatistics(
    val connectionUptime: Long = 0L,
    val messagesReceived: Int = 0,
    val messagesSent: Int = 0,
    val errors: Int = 0
)
