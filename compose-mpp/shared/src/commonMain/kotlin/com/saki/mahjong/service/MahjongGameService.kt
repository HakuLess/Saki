package com.saki.mahjong.service

import com.saki.mahjong.core.GameState
import kotlinx.coroutines.*

/**
 * 麻将游戏服务接口
 */
interface MahjongGameService {
    fun start()
    fun stop()
    fun updateGameState(gameState: GameState)
    var onGameStateUpdate: ((GameState, AnalysisResult?) -> Unit)?
}

/**
 * 基础麻将游戏服务实现
 */
abstract class BaseMahjongGameService : MahjongGameService {
    override var onGameStateUpdate: ((GameState, AnalysisResult?) -> Unit)? = null
    protected val pythonService by lazy { createPythonService() }
    private var isStarted = false
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    override fun start() {
        if (isStarted) return
        
        isStarted = true
        coroutineScope.launch {
            try {
                // 尝试连接Python服务
                pythonService.connect()
                println("Python分析器服务连接成功")

                // 订阅Python分析结果的更新，直接推送到UI
                pythonService.onAnalysisUpdate = { result ->
                    // 使用默认GameState占位，UI仅展示分析结果即可
                    val placeholderState = GameState()
                    launch {
                        withContext(Dispatchers.Main) {
                            onGameStateUpdate?.invoke(placeholderState, result)
                        }
                    }
                }
            } catch (e: Exception) {
                println("Python分析器服务连接失败: ${e.message}")
                println("请确保已启动Python分析器服务")
            }
        }
    }
    
    override fun stop() {
        if (!isStarted) return
        
        isStarted = false
        pythonService.disconnect()
        coroutineScope.cancel()
    }
    
    override fun updateGameState(gameState: GameState) {
        if (!isStarted) return
        
        coroutineScope.launch {
            var analysisResult: AnalysisResult? = null
            
            // 如果Python服务已连接，发送游戏状态进行分析
            if (pythonService.isConnected()) {
                try {
                    analysisResult = pythonService.sendGameState(gameState)
                } catch (e: Exception) {
                    println("发送游戏状态到Python服务失败: ${e.message}")
                }
            }
            
            // 回调通知游戏状态更新
            withContext(Dispatchers.Main) {
                onGameStateUpdate?.invoke(gameState, analysisResult)
            }
        }
    }
}