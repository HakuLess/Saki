package com.saki.mahjong.service

import com.saki.mahjong.core.GameState
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.concurrent.thread

/**
 * 创建桌面端Python服务实例 - 在正确的包中实现
 */
actual fun createPythonService(): PythonService {
    return DesktopPythonServiceImpl()
}

/**
 * 桌面端Python服务实现
 */
class DesktopPythonServiceImpl : PythonService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocketClient: WebSocketClientWrapper? = null
    private val isConnectedAtomic = AtomicBoolean(false)
    private val messageMutex = Mutex()
    private val analysisResultMutex = Mutex()
    private var currentAnalysisResult: AnalysisResult? = null
    private var reconnectJob: Job? = null
    private val RECONNECT_DELAY_MS = 5000L
    private val PYTHON_SERVER_URL = "ws://localhost:8765"
    private val analysisResponseWaiters = mutableMapOf<Int, CompletableDeferred<AnalysisResult>>()
    private var requestIdCounter = 0

    // 回调：分析结果更新时触发
    override var onAnalysisUpdate: ((AnalysisResult) -> Unit)? = null

    override suspend fun connect() {
        println("开始连接到Python服务器: $PYTHON_SERVER_URL")
        try {
            // 关闭现有的连接
            println("关闭现有连接...")
            disconnect()
            
            // 创建新的WebSocket客户端包装器
            println("创建WebSocket客户端...")
            webSocketClient = WebSocketClientWrapper(
                uri = URI(PYTHON_SERVER_URL),
                onOpen = { handleConnectionEstablished() },
                onMessage = { message -> handleMessage(message) },
                onClose = { code, reason, remote -> handleConnectionClosed(code, reason, remote) },
                onError = { ex -> handleError(ex) }
            )
            
            // 连接到Python服务器
            println("调用WebSocket客户端连接方法...")
            webSocketClient?.connect()
            
            // 等待连接建立
            println("等待连接建立...")
            val connectionDeferred = CompletableDeferred<Boolean>()
            scope.launch {
                var attempts = 0
                while (attempts < 10 && !isConnectedAtomic.get()) {
                    println("连接尝试 $attempts: 当前连接状态: ${isConnectedAtomic.get()}")
                    delay(500)
                    attempts++
                }
                println("连接等待完成: ${isConnectedAtomic.get()}")
                connectionDeferred.complete(isConnectedAtomic.get())
            }
            
            if (!connectionDeferred.await()) {
                println("连接超时，无法连接到Python服务器")
                throw Exception("Failed to connect to Python server within timeout")
            }
        } catch (e: Exception) {
            println("连接异常: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun disconnect() {
        try {
            // 取消重连任务
            reconnectJob?.cancel()
            reconnectJob = null
            
            // 关闭WebSocket连接
            webSocketClient?.close()
            webSocketClient = null
            
            // 更新连接状态
            isConnectedAtomic.set(false)
            
            // 清除分析结果
            scope.launch {
                analysisResultMutex.withLock {
                    currentAnalysisResult = null
                }
            }
            
            // 取消所有等待分析结果的任务
            analysisResponseWaiters.forEach { _, waiter ->
                waiter.completeExceptionally(CancellationException("Connection closed"))
            }
            analysisResponseWaiters.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun sendGameState(gameState: GameState): AnalysisResult {
        return withContext(Dispatchers.IO) {
            if (!isConnectedAtomic.get()) {
                throw Exception("Not connected to Python server")
            }
            
            // 创建一个等待分析结果的CompletableDeferred
            val requestId = requestIdCounter++
            val responseDeferred = CompletableDeferred<AnalysisResult>()
            
            // 添加到等待队列
            analysisResponseWaiters[requestId] = responseDeferred
            
            try {
                messageMutex.withLock {
                    if (webSocketClient?.isOpen == true) {
                        // 构建包含requestId的消息
                        val gameStateMap = gameState.toMap()
                        val message = mapOf(
                            "type" to "game_state",
                            "request_id" to requestId,
                            "data" to gameStateMap
                        )
                        val gameStateJson = convertToJson(message)
                        println("发送游戏状态: $gameStateJson")
                        webSocketClient?.send(gameStateJson)
                    }
                }
                
                // 等待分析结果，设置超时
                withTimeoutOrNull(10000) { // 10秒超时
                    responseDeferred.await()
                } ?: throw Exception("Analysis timeout")
            } catch (e: Exception) {
                analysisResponseWaiters.remove(requestId)
                throw e
            }
        }
    }

    override fun isConnected(): Boolean {
        return isConnectedAtomic.get()
    }

    /**
     * 处理连接建立事件
     */
    private fun handleConnectionEstablished() {
        scope.launch {
            messageMutex.withLock {
                isConnectedAtomic.set(true)
                println("Python服务连接成功")
                
                // 发送连接确认消息
                val connectMessage = mapOf(
                    "type" to "connect",
                    "data" to "kotlin_client_connected"
                )
                webSocketClient?.send(convertToJson(connectMessage))
            }
            // 启动定时获取分析结果
            startAnalysisPolling()
        }
    }

    /**
     * 定时轮询获取当前分析结果
     */
    private fun startAnalysisPolling() {
        scope.launch {
            while (isConnectedAtomic.get()) {
                try {
                    requestAnalysisOnce()
                } catch (_: Exception) {
                    // 忽略单次错误，继续轮询
                }
                delay(1000L)
            }
        }
    }

    /**
     * 向Python请求当前分析结果
     */
    private suspend fun requestAnalysisOnce() {
        messageMutex.withLock {
            if (webSocketClient?.isOpen == true) {
                val msg = mapOf("type" to "get_analysis")
                webSocketClient?.send(convertToJson(msg))
            }
        }
    }

    /**
     * 处理接收到的消息
     */
    private fun handleMessage(message: String) {
        scope.launch {
            try {
                println("Received message: $message")
                
                // 实际解析JSON响应
                val result = parseAnalysisResultFromJson(message)
                
                // 保存分析结果
                analysisResultMutex.withLock {
                    currentAnalysisResult = result
                }
                
                // 触发分析更新回调
                onAnalysisUpdate?.invoke(result)
                
                // 通知等待的请求
                if (analysisResponseWaiters.isNotEmpty()) {
                    val (requestId, waiter) = analysisResponseWaiters.entries.first()
                    analysisResponseWaiters.remove(requestId)
                    waiter.complete(result)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 处理连接关闭事件
     */
    private fun handleConnectionClosed(code: Int, reason: String, remote: Boolean) {
        scope.launch {
            messageMutex.withLock {
                isConnectedAtomic.set(false)
                println("Python服务连接关闭: $reason (代码: $code)")
                
                // 启动重连机制
                startReconnect()
            }
        }
    }

    /**
     * 处理错误事件
     */
    private fun handleError(ex: Exception) {
        println("Python服务错误: ${ex.message}")
        
        // 错误发生后尝试重连
        if (isConnectedAtomic.get()) {
            startReconnect()
        }
    }

    /**
     * 启动重连机制
     */
    private fun startReconnect() {
        // 取消现有的重连任务
        reconnectJob?.cancel()
        
        // 创建新的重连任务
        reconnectJob = scope.launch {
            try {
                println("尝试重新连接Python服务...")
                delay(RECONNECT_DELAY_MS)
                connect()
            } catch (e: CancellationException) {
                // 任务被取消，忽略
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 将游戏状态转换为JSON字符串
     */
    private fun convertGameStateToJson(gameState: GameState): String {
        val gameStateMap = gameState.toMap()
        val message = mapOf(
            "type" to "game_state",
            "data" to gameStateMap
        )
        return convertToJson(message)
    }

    /**
     * 通用Map转换为JSON字符串
     */
    private fun convertToJson(data: Map<String, Any>): String {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{")
        var firstEntry = true
        for ((key, value) in data) {
            if (!firstEntry) jsonBuilder.append(",")
            firstEntry = false
            jsonBuilder.append("\"")
            jsonBuilder.append(escapeString(key))
            jsonBuilder.append("\":")
            when (value) {
                is String -> {
                    jsonBuilder.append("\"")
                    jsonBuilder.append(escapeString(value))
                    jsonBuilder.append("\"")
                }
                is Number, is Boolean -> {
                    jsonBuilder.append(value.toString())
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    jsonBuilder.append(convertToJson(value as Map<String, Any>))
                }
                is List<*> -> {
                    jsonBuilder.append("[")
                    var first = true
                    for (item in value) {
                        if (!first) jsonBuilder.append(",")
                        first = false
                        when (item) {
                            is String -> {
                                jsonBuilder.append("\"")
                                jsonBuilder.append(escapeString(item))
                                jsonBuilder.append("\"")
                            }
                            is Number, is Boolean -> {
                                jsonBuilder.append(item.toString())
                            }
                            is Map<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                jsonBuilder.append(convertToJson(item as Map<String, Any>))
                            }
                            else -> {
                                jsonBuilder.append("null")
                            }
                        }
                    }
                    jsonBuilder.append("]")
                }
                else -> {
                    jsonBuilder.append("null")
                }
            }
        }
        jsonBuilder.append("}")
        return jsonBuilder.toString()
    }

    private fun escapeString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * 解析Python返回的分析结果JSON，构造AnalysisResult对象
     */
    private fun parseAnalysisResultFromJson(jsonString: String): AnalysisResult {
        // 简化版解析逻辑（不依赖第三方库），解析我们关心的字段
        val tilesField = extractField(jsonString, "tiles")
        val discardsField = extractField(jsonString, "discards")
        val recommendationsField = extractField(jsonString, "recommendations")
        val warningField = extractField(jsonString, "warning")
        val scoreField = extractField(jsonString, "score")
        val gameStageField = extractField(jsonString, "game_stage")

        val tilesMap = parseTilesField(tilesField)
        val discardsMap = parseDiscardsField(discardsField)
        val recommendationsList = parseListField(recommendationsField)
        val warningList = parseListField(warningField)
        val scoreValue = scoreField?.toIntOrNull() ?: 0
        val gameStageValue = gameStageField ?: "unknown"

        return AnalysisResult(
            tiles = tilesMap,
            discards = discardsMap,
            recommendations = recommendationsList,
            warning = warningList,
            score = scoreValue,
            gameStage = gameStageValue
        )
    }

    private fun extractField(json: String, fieldName: String): String? {
        // 非严格的字段提取
        val pattern = Regex("\"$fieldName\"\\s*:\\s*(\\{.*?\\}|\\[.*?\\]|\".*?\"|[0-9]+|true|false)", RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(json)
        return match?.groups?.get(1)?.value
    }

    private fun parseListField(fieldValue: String?): List<String> {
        if (fieldValue.isNullOrBlank()) return emptyList()
        val trimmed = fieldValue.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val content = trimmed.substring(1, trimmed.length - 1)
            if (content.isBlank()) return emptyList()
            return content.split(Regex(",\\s*"))
                .map { it.trim().trim('"') }
        }
        return emptyList()
    }

    private fun parseTilesField(fieldValue: String?): Map<String, List<String>> {
        if (fieldValue.isNullOrBlank()) return emptyMap()
        val trimmed = fieldValue.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return emptyMap()
        val content = trimmed.substring(1, trimmed.length - 1)
        if (content.isBlank()) return emptyMap()
    
        val result = mutableMapOf<String, List<String>>()
        val entries = content.split(Regex(",(?![^\\{]*\\})"))
        for (entry in entries) {
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().trim('"')
                val value = parts[1].trim()
                result[key] = parseListField(value)
            }
        }
        return result
    }

    private fun parseDiscardsField(fieldValue: String?): Map<String, List<String>> {
        if (fieldValue.isNullOrBlank()) return emptyMap()
        val trimmed = fieldValue.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return emptyMap()
        val content = trimmed.substring(1, trimmed.length - 1)
        if (content.isBlank()) return emptyMap()
    
        val result = mutableMapOf<String, List<String>>()
        val entries = content.split(Regex(",(?![^\\{]*\\})"))
        for (entry in entries) {
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().trim('"')
                val value = parts[1].trim()
                result[key] = parseListField(value)
            }
        }
        return result
    }
}

class WebSocketClientWrapper(
    private val uri: URI,
    private val onOpen: () -> Unit,
    private val onMessage: (String) -> Unit,
    private val onClose: (Int, String, Boolean) -> Unit,
    private val onError: (Exception) -> Unit
) {
    private var webSocket: WebSocket? = null
    var isOpen: Boolean = false
        private set

    fun connect() {
        try {
            val client = HttpClient.newHttpClient()
            val listener = object : WebSocket.Listener {
                override fun onOpen(webSocket: WebSocket) {
                    println("WebSocket连接已打开")
                    this@WebSocketClientWrapper.webSocket = webSocket
                    isOpen = true
                    onOpen.invoke()
                    webSocket.request(1)
                }

                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): java.util.concurrent.CompletionStage<*> {
                    println("WebSocket收到消息: $data")
                    onMessage.invoke(data.toString())
                    webSocket.request(1)
                    return java.util.concurrent.CompletableFuture.completedFuture(null)
                }

                override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String?): java.util.concurrent.CompletionStage<*> {
                    println("WebSocket关闭: $statusCode, 原因: $reason")
                    isOpen = false
                    onClose.invoke(statusCode, reason ?: "", true)
                    return java.util.concurrent.CompletableFuture.completedFuture(null)
                }
            }
            client.newWebSocketBuilder().buildAsync(uri, listener)
        } catch (e: Exception) {
            onError.invoke(e)
        }
    }

    fun send(text: String) {
        try {
            webSocket?.sendText(text, true)
        } catch (e: Exception) {
            onError.invoke(e)
        }
    }

    fun close() {
        try {
            webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing")
            isOpen = false
        } catch (e: Exception) {
            onError.invoke(e)
        }
    }
}