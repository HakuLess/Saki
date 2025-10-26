package collector

import model.GameState
import kotlinx.coroutines.flow.Flow

/**
 * 游戏状态采集器接口
 */
interface GameStateCollector {
    
    /**
     * 开始采集游戏状态
     * @return 游戏状态的Flow流
     */
    fun startCollecting(): Flow<GameState>
    
    /**
     * 停止采集
     */
    suspend fun stopCollecting()
    
    /**
     * 检查是否正在采集
     */
    val isCollecting: Boolean
    
    /**
     * 获取当前游戏状态（同步方法）
     */
    suspend fun getCurrentState(): GameState?
}

/**
 * 状态采集配置（Steam 客户端专用）
 */
data class CollectorConfig(
    val pollIntervalMs: Long = 1000L, // 轮询间隔（毫秒）
    val debugMode: Boolean = false, // 调试模式
    // 可能使用的域名（明确主机）
    val steamHosts: List<String> = listOf(
        "game.maj-soul.com",
        "gateway-game.maj-soul.com",
        "mahjongsoul.game.yo-star.com",
        "game.mahjongsoul.com"
    ),
    // DNS 缓存匹配的关键词（用于发现实际连接域名）
    val hostPatterns: List<String> = listOf("maj-soul", "mahjong", "yo-star"),
    // MITM 代理设置
    val enableMitm: Boolean = true,
    val mitmPort: Int = 8899,
    // Proxinject 自动代理设置
    val enableProxinject: Boolean = false  // 新增：是否启用 proxinject 自动代理
)