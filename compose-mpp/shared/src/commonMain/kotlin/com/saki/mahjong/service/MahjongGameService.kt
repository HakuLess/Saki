package com.saki.mahjong.service

import com.saki.mahjong.data.GameState

/**
 * 麻将游戏服务接口
 * 定义获取游戏状态和处理网络请求的核心功能
 */
interface MahjongGameService {
    
    /**
     * 初始化游戏服务
     * @param onGameStateUpdate 游戏状态更新回调
     */
    fun initialize(onGameStateUpdate: (GameState) -> Unit)
    
    /**
     * 启动网络请求拦截
     */
    fun startNetworkInterceptor()
    
    /**
     * 停止网络请求拦截
     */
    fun stopNetworkInterceptor()
    
    /**
     * 获取当前游戏状态
     */
    fun getCurrentGameState(): GameState
    
    /**
     * 释放资源
     */
    fun dispose()
}