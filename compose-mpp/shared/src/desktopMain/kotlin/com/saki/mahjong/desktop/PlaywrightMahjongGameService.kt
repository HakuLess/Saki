package com.saki.mahjong.desktop

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Response
import com.microsoft.playwright.Route
import com.saki.mahjong.data.GameState
import com.saki.mahjong.service.MahjongGameService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.io.File
import java.util.regex.Pattern

class PlaywrightMahjongGameService : MahjongGameService {
    private var onGameStateUpdate: ((GameState) -> Unit)? = null
    private var scope: CoroutineScope? = null
    private var playwright: Playwright? = null
    private var context: BrowserContext? = null
    private var browser: Browser? = null
    private var isIntercepting = false
    private var job: Job? = null

    // Enhanced Mahjong Soul API matching patterns
    private val mahjongSoulApiPatterns = listOf(
        Pattern.compile("https://game\\.mahjongsoul\\.com/"),
        Pattern.compile("https://game\\.maj-soul\\.com/"),
        Pattern.compile("https://majsoul\\.unison-gaming\\.com/"),
        Pattern.compile("https://mahjongsoul\\.tcplayer\\.tv/")
    )

    // 添加更广泛的雀魂API URL模式
    private val mahjongSoulApiEndpoints = listOf(
        "https://game.mahjongsoul.com/",
        "https://game.maj-soul.com/",
        "https://majsoul.unison-gaming.com/",
        "https://mahjongsoul.tcplayer.tv/",
        "https://game.maj-soul.com/1/",
        "https://game.mahjongsoul.com/1/"
    )

    override fun initialize(onGameStateUpdate: (GameState) -> Unit) {
        this.onGameStateUpdate = onGameStateUpdate
        this.scope = CoroutineScope(Dispatchers.IO)
        println("[Playwright Service] Service initialized")
    }

    override fun startNetworkInterceptor() {
        if (isIntercepting) {
            println("[Playwright Service] Network interceptor is already running")
            return
        }

        job = scope?.launch {
            try {
                // Start Playwright
                println("[Playwright Service] Starting Playwright...")
                playwright = Playwright.create()
                println("[Playwright Service] Playwright started")
                
                // 只尝试连接到已有的Chrome浏览器（远程调试模式）
                println("[Playwright Service] Trying to connect to existing Chrome browser (remote debugging mode)...")
                val connectionSuccess = tryConnectToExistingChrome()
                
                // 如果连接成功，设置网络拦截
                if (connectionSuccess && context != null) {
                    setupNetworkInterception()
                    isIntercepting = true
                    println("[Playwright Service] Network interception successfully started!")
                    println("[Playwright Service] Monitoring Mahjong Soul traffic")
                } else {
                    println("[Playwright Service] Warning: Could not connect to Chrome browser for network interception")
                    println("[Playwright Service] Please ensure:")
                    println("[Playwright Service] 1. Chrome browser is closed")
                    println("[Playwright Service] 2. Open Command Prompt and run:")
                    println("[Playwright Service]    \"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe\" --remote-debugging-port=9222")
                    println("[Playwright Service] 3. In the opened Chrome, visit https://game.maj-soul.com/1/")
                    println("[Playwright Service] 4. Return to the application and click 'Start Interception' again")
                    
                    // 即使没有浏览器连接，也设置isIntercepting为true并发送初始状态
                    // 这样UI仍然可以正常响应
                    isIntercepting = true
                }
                
                // 发送初始游戏状态
                onGameStateUpdate?.invoke(createInitialGameState())
                println("[Playwright Service] Initial game state sent")
                
            } catch (e: Exception) {
                println("[Playwright Service ERROR] Failed to start network interception: ${e.message}")
                e.printStackTrace()
                cleanupResources()
                
                // 即使出现异常，也设置基本状态以保持UI响应
                isIntercepting = true
                onGameStateUpdate?.invoke(createInitialGameState())
            }
        }
    }
    
    /**
     * 尝试连接到已有的Chrome浏览器（远程调试模式）
     * @return 是否连接成功
     */
    private fun tryConnectToExistingChrome(): Boolean {
        val debugPort = 9222
        val debugUrl = "http://127.0.0.1:$debugPort"
        
        try {
            println("[Playwright Service] Trying to connect to debug port $debugPort...")
            // 使用channel参数连接到Chrome浏览器
            browser = playwright?.chromium()?.connectOverCDP(debugUrl)
            
            if (browser != null) {
                println("[Playwright Service] Successfully connected to existing Chrome browser!")
                context = browser?.newContext()
                println("[Playwright Service] Created new browser context for interception")
                return true
            } else {
                println("[Playwright Service] Failed to connect to Chrome - browser object is null")
            }
        } catch (connectError: Exception) {
            println("[Playwright Service WARNING] Failed to connect to Chrome: ${connectError.message}")
            connectError.printStackTrace()
        }
        
        return false
    }
    
    private fun setupNetworkInterception() {
        println("[Playwright Service] Configuring request interceptor...")
        // Set up route interception to monitor Mahjong Soul API requests
        context?.route("**/*", java.util.function.Consumer<Route> { route ->
            val url = route.request().url()
            
            // Check if it's a Mahjong Soul related API request
            val isMahjongSoulRequest = mahjongSoulApiPatterns.any { it.matcher(url).find() } ||
                                     mahjongSoulApiEndpoints.any { url.startsWith(it) }
            
            // 打印所有请求URL，便于调试
            println("[Playwright Service] Request URL: $url")
            
            if (isMahjongSoulRequest) {
                println("[Playwright Service] Intercepted Mahjong Soul request: $url")
                println("[Playwright Service] Request method: ${route.request().method()}")
                println("[Playwright Service] Request headers: ${route.request().headers()}")
            }
            
            // Continue all requests
            route.resume()
        })
        
        println("[Playwright Service] Configuring response listener...")
        // Add response listener
        context?.onResponse(java.util.function.Consumer<Response> { response ->
            val url = response.url()
            val isMahjongSoulRequest = mahjongSoulApiPatterns.any { it.matcher(url).find() } ||
                                     mahjongSoulApiEndpoints.any { url.startsWith(it) }
            
            // 打印所有响应的URL，便于调试
            println("[Playwright Service] Response URL: $url")
            
            if (isMahjongSoulRequest) {
                println("[Playwright Service] Received Mahjong Soul API response: $url")
                handleApiResponse(url, response)
            } else {
                // 检查是否是WebSocket连接
                if (url.contains("ws://") || url.contains("wss://") || url.contains("socket")) {
                    println("[Playwright Service] WebSocket connection detected: $url")
                    // 尝试处理WebSocket消息
                    handleWebSocketResponse(url, response)
                }
            }
        })
        println("[Playwright Service] Network interception setup completed")
    }
    
    private fun handleWebSocketResponse(url: String, response: Response) {
        try {
            println("[Playwright Service] Processing WebSocket response: $url")
            // 对于WebSocket连接，我们只记录信息
            println("[Playwright Service] WebSocket headers: ${response.headers()}")
        } catch (e: Exception) {
            println("[Playwright Service ERROR] Failed to process WebSocket response: ${e.message}")
        }
    }
    
    private fun handleApiResponse(url: String, response: Response) {
        try {
            val contentType = response.headers().get("content-type")
            println("[Playwright Service] Processing API response: $url, Content-Type: $contentType")
            
            // 特别处理version.json请求，即使内容类型不是JSON也尝试解析
            val isVersionJson = url.contains("version.json")
            
            // 检查是否为JSON内容或version.json
            if (isVersionJson || (contentType != null && (
                contentType.contains("application/json") || 
                contentType.contains("text/json")
            ))) {
                println("[Playwright Service] Attempting to extract JSON content from response")
                try {
                    val jsonText = response.text()
                    println("[Playwright Service] Successfully extracted text content, length: ${jsonText.length}")
                    
                    // Print only part of the JSON content to avoid long logs
                    val previewText = if (jsonText.length > 200) jsonText.substring(0, 200) + "..." else jsonText
                    println("[Playwright Service] Response content preview: $previewText")
                    
                    // 扩大匹配范围，捕获更多雀魂API响应
                    // 1. 检查URL是否包含游戏相关关键词
                    val isGameRelatedUrl = url.contains("game") || 
                                          url.contains("round") || 
                                          url.contains("state") ||
                                          url.contains("player") ||
                                          url.contains("room") ||
                                          url.contains("match") ||
                                          url.contains("action") ||
                                          url.contains("operation") ||
                                          url.endsWith(".json") ||
                                          url.contains("version.json") ||
                                          url.contains("liqi") ||  // 雀魂特有的API
                                          url.contains("oauth") ||  // 认证相关
                                          url.contains("socket")    // WebSocket连接
                    
                    // 2. 检查响应内容是否包含游戏状态相关信息
                    val hasGameData = jsonText.contains("round") || 
                                     jsonText.contains("player") ||
                                     jsonText.contains("score") ||
                                     jsonText.contains("status") ||
                                     jsonText.contains("version") ||
                                     jsonText.contains("tiles") ||
                                     jsonText.contains("hand") ||
                                     jsonText.contains("operation") ||
                                     jsonText.contains("liqi")
                    
                    println("[Playwright Service] URL analysis: game-related=$isGameRelatedUrl, data-content=$hasGameData")
                    
                    // 对于version.json请求，总是尝试更新游戏状态以测试UI更新
                    if (isVersionJson || isGameRelatedUrl || hasGameData) {
                        println("[Playwright Service] Detected relevant data, updating game state")
                        
                        // 特别处理version.json请求
                        val updatedGameState = if (isVersionJson) {
                            println("[Playwright Service] Processing version.json request - FORCING TEST STATE UPDATE")
                            createTestGameStateForVersionCheck()
                        } else {
                            parseGameStateFromResponse(jsonText)
                        }
                        
                        // 确保更新回调被调用
                        onGameStateUpdate?.let { callback ->
                            println("[Playwright Service] Invoking game state update callback")
                            if (isVersionJson) {
                                println("[Playwright Service] Forcing game state update for version.json")
                            }
                            callback(updatedGameState)
                            println("[Playwright Service] Game state update callback completed")
                        } ?: run {
                            println("[Playwright Service] WARNING: onGameStateUpdate callback is null")
                        }
                    } else {
                        println("[Playwright Service] JSON response doesn't appear to contain relevant game data")
                    }
                } catch (textError: Exception) {
                    println("[Playwright Service WARNING] Failed to extract text from response: ${textError.message}")
                    textError.printStackTrace()
                }
            } else {
                // 即使不是JSON内容，也检查是否是游戏相关的API响应
                val isGameRelatedUrl = url.contains("game") || 
                                      url.contains("round") || 
                                      url.contains("state") ||
                                      url.contains("player") ||
                                      url.contains("room") ||
                                      url.contains("match") ||
                                      url.contains("action") ||
                                      url.contains("operation") ||
                                      url.contains("liqi") ||  // 雀魂特有的API
                                      url.contains("oauth") ||  // 认证相关
                                      url.contains("socket")    // WebSocket连接
                
                if (isGameRelatedUrl) {
                    println("[Playwright Service] Non-JSON game-related response detected: $url")
                    // 尝试获取响应内容
                    try {
                        val responseText = response.text()
                        println("[Playwright Service] Response content preview: ${responseText.take(200)}...")
                        
                        // 检查是否包含游戏数据
                        if (responseText.contains("round") || 
                            responseText.contains("player") ||
                            responseText.contains("score") ||
                            responseText.contains("tiles") ||
                            responseText.contains("liqi")) {
                            println("[Playwright Service] Detected game data in response, updating game state")
                            val updatedGameState = parseGameStateFromResponse(responseText)
                            onGameStateUpdate?.invoke(updatedGameState)
                        }
                    } catch (e: Exception) {
                        println("[Playwright Service] Failed to read response content: ${e.message}")
                    }
                } else {
                    println("[Playwright Service] Response is not JSON content and not version.json, skipping")
                }
            }
        } catch (e: Exception) {
            println("[Playwright Service ERROR] Failed to process API response: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 创建一个特殊的测试游戏状态，用于在version.json响应时测试UI更新
     */
    private fun createTestGameStateForVersionCheck(): GameState {
        println("[Playwright Service] Creating test game state for version check")
        
        // 创建一个带有更明显标记的测试状态
        return GameState(
            status = com.saki.mahjong.data.GameStatus.IN_PROGRESS,
            round = 2,
            honba = 1,
            kyotaku = 2,
            currentPlayerIndex = 1,
            players = listOf(
                com.saki.mahjong.data.PlayerInfo("Player", 27000, 0, true, false),
                com.saki.mahjong.data.PlayerInfo("Opponent1", 24000, 1, false, false),
                com.saki.mahjong.data.PlayerInfo("Opponent2", 26000, 2, false, false),
                com.saki.mahjong.data.PlayerInfo("Opponent3", 23000, 3, false, false)
            ),
            handTiles = emptyList(),
            drawnTile = null,
            river = emptyList(),
            doraIndicators = emptyList(),
            uradoraIndicators = emptyList()
        )
    }

    private fun parseGameStateFromResponse(jsonText: String): GameState {
        println("[Playwright Service] Parsing game state from response")
        // 这里应该根据实际的API响应格式解析游戏状态
        // 目前返回一个基本的模拟状态
        return GameState(
            status = com.saki.mahjong.data.GameStatus.IN_PROGRESS,
            round = 1,
            honba = 0,
            kyotaku = 1,
            currentPlayerIndex = 0,
            players = listOf(
                com.saki.mahjong.data.PlayerInfo("Player1", 25000, 0, true, false),
                com.saki.mahjong.data.PlayerInfo("Player2", 25000, 1, false, false),
                com.saki.mahjong.data.PlayerInfo("Player3", 25000, 2, false, false),
                com.saki.mahjong.data.PlayerInfo("Player4", 25000, 3, false, false)
            ),
            handTiles = emptyList(),
            drawnTile = null,
            river = emptyList(),
            doraIndicators = emptyList(),
            uradoraIndicators = emptyList()
        )
    }

    private fun createInitialGameState(): GameState {
        return GameState(
            status = com.saki.mahjong.data.GameStatus.NOT_STARTED,
            round = 0,
            honba = 0,
            kyotaku = 0,
            currentPlayerIndex = 0,
            players = emptyList(),
            handTiles = emptyList(),
            drawnTile = null,
            river = emptyList(),
            doraIndicators = emptyList(),
            uradoraIndicators = emptyList()
        )
    }

    private fun cleanupResources() {
        try {
            println("[Playwright Service] Cleaning up resources...")
            
            // Close browser context
            context?.close()
            println("[Playwright Service] Browser context closed")
            context = null
            
            // Close browser
            browser?.close()
            println("[Playwright Service] Browser closed")
            browser = null
            
            // Close Playwright
            playwright?.close()
            println("[Playwright Service] Playwright closed")
            playwright = null
        } catch (e: Exception) {
            println("[Playwright Service ERROR] Failed to clean up resources: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun stopNetworkInterceptor() {
        println("[Playwright Service] Stopping network interceptor...")
        job?.cancel()
        cleanupResources()
        isIntercepting = false
        println("[Playwright Service] Network interceptor stopped")
    }

    override fun getCurrentGameState(): GameState {
        // 返回当前游戏状态
        return createInitialGameState()
    }

    override fun dispose() {
        stopNetworkInterceptor()
        scope?.cancel()
        onGameStateUpdate = null
        scope = null
    }
}