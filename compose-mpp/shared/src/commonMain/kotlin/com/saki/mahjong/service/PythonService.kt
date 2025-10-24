package com.saki.mahjong.service

import com.saki.mahjong.core.GameState
import kotlinx.coroutines.*

/**
 * Python服务接口，定义了与Python分析器通信的基本行为
 */
interface PythonService {
    suspend fun connect()
    fun disconnect()
    suspend fun sendGameState(gameState: GameState): AnalysisResult
    fun isConnected(): Boolean
    // 回调：当收到Python分析结果时触发
    var onAnalysisUpdate: ((AnalysisResult) -> Unit)?
}

/**
 * 分析结果数据类
 */
data class AnalysisResult(
    val tiles: Map<String, Any> = emptyMap(),
    val discards: Map<String, Any> = emptyMap(),
    val recommendations: List<String> = emptyList(),
    val warning: List<String> = emptyList(),
    val score: Int = 0,
    val gameStage: String = "unknown"
)

/**
 * 消息包装类
 */
sealed class ServiceMessage {
    abstract val type: String
}

data class GameStateMessage(val data: Map<String, Any>, override val type: String = "game_state") : ServiceMessage()

data class AnalysisResultMessage(val data: AnalysisResult, override val type: String = "analysis_result") : ServiceMessage()

data class GetAnalysisMessage(override val type: String = "get_analysis") : ServiceMessage()

/**
 * 创建平台特定的Python服务实例
 */
expect fun createPythonService(): PythonService