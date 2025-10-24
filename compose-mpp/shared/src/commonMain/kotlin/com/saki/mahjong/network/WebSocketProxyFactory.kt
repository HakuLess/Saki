package com.saki.mahjong.network

/**
 * WebSocket代理工厂，用于创建平台特定的WebSocket代理实例
 */
interface WebSocketProxyFactory {
    /**
     * 创建WebSocket代理实例
     */
    fun createWebSocketProxy(): WebSocketProxy
}