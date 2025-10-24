package com.saki.mahjong.network

import com.saki.mahjong.core.GameMessage
import com.saki.mahjong.core.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * WebSocket代理接口，定义了跨平台WebSocket代理的基本行为
 */
interface WebSocketProxy {
    val gameState: GameState
    var onGameStateUpdated: ((GameState) -> Unit)?
    var onConnectionStatusChanged: ((Boolean) -> Unit)?
    var onError: ((Throwable) -> Unit)?
    var onCertificateInstalled: (() -> Unit)?  // 证书安装完成回调
    
    fun start(port: Int = 8888)
    fun stop()
    fun isRunning(): Boolean
}

/**
 * WebSocket消息监听器接口
 */
interface WebSocketMessageListener {
    fun onMessageReceived(message: ByteArray)
    fun onConnectionOpened()
    fun onConnectionClosed()
    fun onError(error: Throwable)
}

/**
 * HTTP请求拦截器接口
 */
interface HttpRequestInterceptor {
    fun interceptRequest(request: String, headers: Map<String, String>): Boolean
    fun interceptResponse(response: String, headers: Map<String, String>): Boolean
}

/**
 * 雀魂游戏数据检测器接口
 */
interface TenhouGameDataDetector {
    fun containsGameData(data: String): Boolean
    fun extractGameData(data: String): ByteArray?
}

/**
 * 基础WebSocket代理实现，提供共享逻辑
 */
abstract class BaseWebSocketProxy : WebSocketProxy {
    override val gameState: GameState = GameState()
    override var onGameStateUpdated: ((GameState) -> Unit)? = null
    override var onConnectionStatusChanged: ((Boolean) -> Unit)? = null
    override var onError: ((Throwable) -> Unit)? = null
    override var onCertificateInstalled: (() -> Unit)? = null
    
    protected val mutex = Mutex()
    protected var running = false
    
    private val protobufDecoder = ProtobufDecoder()
    private val httpInterceptors = mutableListOf<HttpRequestInterceptor>()
    private val gameDataDetectors = mutableListOf<TenhouGameDataDetector>()
    
    init {
        // 初始化默认的HTTP请求拦截器 - 增强的域名匹配
        addHttpRequestInterceptor(object : HttpRequestInterceptor {
            override fun interceptRequest(request: String, headers: Map<String, String>): Boolean {
                // 拦截雀魂相关的HTTP请求 - 使用更全面的域名匹配
                val isTargetHost = headers.any { 
                    it.key.equals("host", ignoreCase = true) && 
                    (it.value.contains("tenhou.net") || 
                     it.value.contains("mj-front.cygames.jp") || 
                     it.value.contains("cygames.jp") || 
                     it.value.contains("mahjongsoul.com") || 
                     it.value.contains("mahjong-soul.com") || 
                     it.value.contains("game.mahjongsoul.com") || 
                     it.value.contains("api.mahjongsoul.com"))
                }
                
                val isTargetRequest = request.contains("tenhou.net") ||
                                     request.contains("mj-front.cygames.jp") ||
                                     request.contains("cygames.jp") ||
                                     request.contains("mahjongsoul.com") ||
                                     request.contains("mahjong-soul.com") ||
                                     request.contains("game.mahjongsoul.com") ||
                                     request.contains("api.mahjongsoul.com")
                
                val shouldIntercept = isTargetHost || isTargetRequest
                if (shouldIntercept) {
                    println("拦截到雀魂相关HTTP请求: $request")
                }
                return shouldIntercept
            }
            
            override fun interceptResponse(response: String, headers: Map<String, String>): Boolean {
                // 拦截雀魂相关的HTTP响应 - 使用更全面的域名匹配
                val isTargetDomain = response.contains("tenhou.net") ||
                                     response.contains("mj-front.cygames.jp") ||
                                     response.contains("cygames.jp") ||
                                     response.contains("mahjongsoul.com") ||
                                     response.contains("mahjong-soul.com") ||
                                     response.contains("game.mahjongsoul.com") ||
                                     response.contains("api.mahjongsoul.com") ||
                                     headers.any { 
                                         it.key.equals("sec-websocket-accept", ignoreCase = true) 
                                     }
                
                if (isTargetDomain) {
                    println("拦截到雀魂相关HTTP响应: ${response.substring(0, Math.min(response.length, 100))}...")
                }
                return isTargetDomain
            }
        })
        
        // 初始化默认的游戏数据检测器 - 使用更全面的模式匹配
        addGameDataDetector(object : TenhouGameDataDetector {
            override fun containsGameData(data: String): Boolean {
                // 检测是否包含雀魂游戏数据 - 使用更全面的模式匹配
                // 匹配完整的XML标签格式
                return data.contains("<TAIKYOKU") ||
                       data.contains("<INIT") ||
                       data.contains("<TILE") ||
                       data.contains("<DORA") ||
                       data.contains("<HAI") ||
                       data.contains("<N ") ||
                       data.contains("<REACH") ||
                       data.contains("<AGARI") ||
                       data.contains("<RYUUKYOKU") ||
                       data.contains("<OWARI") ||
                       // 同时保留小写形式的检查，确保兼容性
                       data.contains("<taikyoku") ||
                       data.contains("<init") ||
                       data.contains("<hai") ||
                       data.contains("<n ") ||
                       data.contains("<reach") ||
                       data.contains("<agari") ||
                       data.contains("<owari")
            }
            
            override fun extractGameData(data: String): ByteArray? {
                // 提取游戏数据并转换为ByteArray
                try {
                    // 先记录检测到的消息内容，方便调试
                    println("检测到雀魂游戏消息: ${data.substring(0, Math.min(data.length, 100))}...")
                    return data.toByteArray(Charsets.UTF_8)
                } catch (e: Exception) {
                    println("提取游戏数据失败: ${e.message}")
                    return null
                }
            }
        })
    }
    
    /**
     * 添加HTTP请求拦截器
     */
    protected fun addHttpRequestInterceptor(interceptor: HttpRequestInterceptor) {
        httpInterceptors.add(interceptor)
    }
    
    /**
     * 添加游戏数据检测器
     */
    protected fun addGameDataDetector(detector: TenhouGameDataDetector) {
        gameDataDetectors.add(detector)
    }
    
    /**
     * 处理接收到的WebSocket消息
     */
    protected fun processMessage(message: ByteArray) {
        try {
            // 首先尝试将消息转换为文本，检查是否为XML格式（雀魂游戏消息）
            try {
                val messageText = String(message, Charsets.UTF_8)
                println("接收到WebSocket消息，尝试解析为文本")
                
                // 检查是否看起来像XML格式（雀魂游戏消息通常是XML）
                if (messageText.startsWith("<") || 
                    messageText.contains("<TAIKYOKU") || 
                    messageText.contains("<INIT") || 
                    messageText.contains("<HAI") || 
                    messageText.contains("<n ")) {
                    println("消息看起来是XML格式，直接使用文本处理")
                    processTextMessage(messageText)
                    return
                } else {
                    println("消息不像是XML格式，尝试protobuf解码")
                }
            } catch (textException: Exception) {
                println("文本转换失败，尝试protobuf解码: ${textException.message}")
            }
            
            // 如果不是XML格式或文本转换失败，尝试解码protobuf消息
            try {
                val gameMessage = protobufDecoder.decode(message)
                println("protobuf消息解码成功")
                
                // 更新游戏状态
                CoroutineScope(Dispatchers.Default).launch {
                    mutex.withLock {
                        gameState.updateFromMessage(gameMessage)
                    }
                    
                    // 通知游戏状态更新
                    onGameStateUpdated?.invoke(gameState)
                }
            } catch (protoException: Exception) {
                println("protobuf解码失败，忽略消息: ${protoException.message}")
            }
        } catch (e: Exception) {
            println("处理WebSocket消息时出错: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 处理文本消息
     */
    protected fun processTextMessage(message: String) {
        try {
            println("开始处理文本消息，长度: ${message.length} 字符")
            
            // 检查是否包含游戏数据
            for (detector in gameDataDetectors) {
                if (detector.containsGameData(message)) {
                    println("游戏数据检测器确认包含雀魂游戏数据")
                    
                    val gameData = detector.extractGameData(message)
                    if (gameData != null) {
                        println("游戏数据提取成功，准备更新游戏状态")
                        
                        // 使用提取的游戏数据更新游戏状态
                        CoroutineScope(Dispatchers.Default).launch {
                            mutex.withLock {
                                // 直接从文本消息更新游戏状态
                                println("开始从雀魂消息更新游戏状态")
                                gameState.updateFromTenhouMessage(message)
                                println("游戏状态更新完成")
                                
                                // 打印一些关键游戏状态信息用于调试
                                println("当前玩家手牌数量: ${gameState.playerHand.size}")
                                println("当前回合: ${gameState.roundWind} ${gameState.roundNumber}局")
                                println("是否轮到自己: ${gameState.isMyTurn}")
                            }
                            
                            // 通知游戏状态更新
                            println("准备通知游戏状态更新回调")
                            onGameStateUpdated?.invoke(gameState)
                            println("游戏状态更新回调已触发")
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            println("处理文本消息时出错: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 处理HTTP请求
     */
    protected fun processHttpRequest(request: String, headers: Map<String, String>): Boolean {
        for (interceptor in httpInterceptors) {
            if (interceptor.interceptRequest(request, headers)) {
                // 记录被拦截的请求
                println("拦截到HTTP请求: ${headers["host"]}")
                return true
            }
        }
        return false
    }
    
    /**
     * 处理HTTP响应
     */
    protected fun processHttpResponse(response: String, headers: Map<String, String>): Boolean {
        for (interceptor in httpInterceptors) {
            if (interceptor.interceptResponse(response, headers)) {
                // 记录被拦截的响应
                println("拦截到HTTP响应: ${headers["content-type"]}")
                return true
            }
        }
        return false
    }
    
    /**
     * 处理连接状态变化
     */
    protected fun notifyConnectionStatus(isConnected: Boolean) {
        running = isConnected
        onConnectionStatusChanged?.invoke(isConnected)
    }
    
    /**
     * 通知证书已安装
     */
    protected fun notifyCertificateInstalled() {
        onCertificateInstalled?.invoke()
    }
    
    /**
     * 处理错误
     */
    protected fun handleError(error: Throwable) {
        println("代理错误: ${error.message}")
        error.printStackTrace()
        onError?.invoke(error)
    }
    
    override fun isRunning(): Boolean {
        return running
    }
}