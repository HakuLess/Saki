package com.saki.mahjong.desktop

import com.saki.mahjong.core.GameState
import com.saki.mahjong.service.BaseMahjongGameService
import com.saki.mahjong.service.MahjongGameService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 桌面端麻将游戏服务实现
 * 
 * 这个服务不再使用模拟数据，而是依靠WebSocket代理来获取真实的雀魂游戏数据。
 * 游戏状态通过WebSocketProxy的onGameStateUpdated回调传递给此服务。
 */
class DesktopMahjongGameService : BaseMahjongGameService(), MahjongGameService {
    private val isMonitoring = AtomicBoolean(false)

    override fun start() {
        if (isMonitoring.get()) return
        
        isMonitoring.set(true)
        
        // 启动Python服务连接，用于分析游戏状态
        // 注意：这里不再需要模拟游戏数据，而是等待来自WebSocket代理的真实数据
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // 初始化Python服务连接
                pythonService.connect()
                println("Python分析服务已启动并等待游戏数据")
            } catch (e: Exception) {
                println("启动Python服务时出错: ${e.message}")
            }
        }
    }

    override fun stop() {
        super.stop()
        isMonitoring.set(false)
    }

    /**
     * 更新游戏状态并发送到Python分析器
     * 
     * 这个方法将从WebSocket代理接收的游戏状态传递给父类进行处理，
     * 父类会将状态发送到Python服务进行分析，并通过回调返回分析结果。
     */
    override fun updateGameState(gameState: GameState) {
        if (!isMonitoring.get()) return
        
        println("收到新的游戏状态，玩家手牌数量: ${gameState.playerHand.size}")
        
        // 调用父类方法处理游戏状态更新和Python分析
        super.updateGameState(gameState)
    }
    
    /**
     * 创建桌面端麻将游戏服务实例
     */
    companion object {
        fun create(): DesktopMahjongGameService {
            return DesktopMahjongGameService()
        }
    }
}