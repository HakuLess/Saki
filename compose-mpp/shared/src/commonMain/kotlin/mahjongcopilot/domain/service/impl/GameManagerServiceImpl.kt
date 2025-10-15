package mahjongcopilot.domain.service.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mahjongcopilot.data.model.*
import mahjongcopilot.domain.service.GameManagerService
import mahjongcopilot.domain.service.NetworkManagerService
import mahjongcopilot.domain.service.GameStateManagerService
import mahjongcopilot.domain.repository.GameStateRepository
import mahjongcopilot.platform.WindowsNetworkManagerServiceImpl

/**
 * 游戏管理服务实现
 */
/**
 * 游戏管理服务实现
 * 负责管理游戏的整体状态，包括网络拦截、游戏状态管理等
 * 根据游戏进程状态决定是否启动网络拦截
 */
class GameManagerServiceImpl(
    private val networkManager: NetworkManagerService,
    private val gameStateManager: GameStateManagerService,
    private val gameStateRepository: GameStateRepository
) : GameManagerService {
    
    private val _appState = MutableStateFlow(AppState())
    private val appState: Flow<AppState> = _appState.asStateFlow()
    
    private val mutex = Mutex()
    private var isRunning = false
    private var managerJob: Job? = null
    
    override suspend fun startGameManager(): Result<Unit> {
        return try {
            mutex.withLock {
                if (isRunning) {
                    return Result.success(Unit)
                }
                
                println("Starting game manager...")
                
                isRunning = true
                managerJob = CoroutineScope(Dispatchers.IO).launch {
                    startGameLoop()
                }
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to start game manager: ${e.message}"
            println(errorMsg)
            Result.failure(RuntimeException(errorMsg, e))
        }
    }
    
    override suspend fun stopGameManager(): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isRunning) {
                    return Result.success(Unit)
                }
                
                println("Stopping game manager...")
                
                isRunning = false
                managerJob?.cancel()
                managerJob = null
                
                // 停止网络拦截
                try {
                    val stopResult = networkManager.stopNetworkInterception()
                    if (stopResult.isFailure) {
                        println("Warning: Failed to stop network interception: ${stopResult.exceptionOrNull()?.message}")
                    } else {
                        println("Network interception stopped successfully")
                    }
                } catch (e: Exception) {
                    println("Error stopping network interception: ${e.message}")
                }
                
                // 清除游戏状态
                try {
                    val clearResult = gameStateRepository.clearGameState()
                    if (clearResult.isFailure) {
                        println("Warning: Failed to clear game state: ${clearResult.exceptionOrNull()?.message}")
                    } else {
                        println("Game state cleared successfully")
                    }
                } catch (e: Exception) {
                    println("Error clearing game state: ${e.message}")
                }
                
                // 更新应用状态
                _appState.value = AppState()
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to stop game manager: ${e.message}"
            println(errorMsg)
            Result.failure(RuntimeException(errorMsg, e))
        }
    }
    
    override suspend fun isInGame(): Boolean {
        return mutex.withLock { 
            val gameState = gameStateRepository.getCurrentGameState()
            gameState?.isGameActive == true
        }
    }
    
    override suspend fun getCurrentGameState(): GameState? {
        return gameStateRepository.getCurrentGameState()
    }
    
    override fun observeGameState(): Flow<GameState?> {
        return gameStateRepository.observeGameState()
    }
    
    override fun observeAppState(): Flow<AppState> {
        return appState
    }
    
    /**
     * 游戏主循环
     */
    private suspend fun startGameLoop() {
        try {
            println("Starting game loop...")
            
            // 检查是否为Windows平台且有进程监控功能
            val isWindowsPlatform = System.getProperty("os.name").lowercase().contains("windows")
            var isGameProcessRunning = false
            var currentNetworkStatus = NetworkStatus.DISCONNECTED
            
            if (isWindowsPlatform && networkManager is WindowsNetworkManagerServiceImpl) {
                // 在协程作用域内启动监听
                coroutineScope {
                    // 监听游戏进程状态
                    launch {
                        networkManager.observeGameProcessStatus().collect { processRunning ->
                            isGameProcessRunning = processRunning
                            println("Game process status: $processRunning")
                            
                            if (processRunning && currentNetworkStatus != NetworkStatus.CONNECTED) {
                                // 游戏进程运行且网络未连接，启动网络拦截
                                println("Game process detected, starting network interception...")
                                val networkResult = networkManager.startNetworkInterception()
                                
                                if (networkResult.isFailure) {
                                    val errorMsg = "Failed to start network interception: ${networkResult.exceptionOrNull()?.message}"
                                    println(errorMsg)
                                    // 提供更友好的错误信息
                                    val userFriendlyError = if (errorMsg.contains("mitmdump")) {
                                        "网络拦截启动失败：请确保已安装mitmproxy并添加到系统PATH中。参考README_mitmproxy.md获取安装说明。"
                                    } else {
                                        errorMsg
                                    }
                                    _appState.value = _appState.value.copy(
                                        errorMessage = userFriendlyError
                                    )
                                    return@collect
                                }
                                
                                println("Network interception started successfully")
                                
                                // 更新应用状态
                                _appState.value = _appState.value.copy(
                                    isConnected = true,
                                    errorMessage = null
                                )
                            } else if (!processRunning && currentNetworkStatus == NetworkStatus.CONNECTED) {
                                // 游戏进程未运行且网络已连接，停止网络拦截
                                println("Game process not detected, stopping network interception...")
                                val stopResult = networkManager.stopNetworkInterception()
                                
                                if (stopResult.isFailure) {
                                    println("Warning: Failed to stop network interception: ${stopResult.exceptionOrNull()?.message}")
                                } else {
                                    println("Network interception stopped successfully")
                                }
                                
                                // 更新应用状态
                                _appState.value = _appState.value.copy(
                                    isConnected = false,
                                    errorMessage = null
                                )
                            }
                        }
                    }
                    
                    // 监听网络状态
                    launch {
                        networkManager.observeNetworkStatus().collect { status ->
                            currentNetworkStatus = status
                            println("Network status updated: $status")
                        }
                    }
                    
                    // 监听网络消息
                    launch {
                        (networkManager as WindowsNetworkManagerServiceImpl).capturedMessages
                            .catch { e ->
                                val errorMsg = "Network error: ${e.message}"
                                println(errorMsg)
                                _appState.value = _appState.value.copy(
                                    errorMessage = errorMsg
                                )
                            }
                            .collect { message ->
                                processNetworkMessage(message)
                            }
                    }
                }
            } else {
                // 非Windows平台或无进程监控功能，直接启动网络拦截
                println("Starting network interception...")
                val networkResult = networkManager.startNetworkInterception()
                
                if (networkResult.isFailure) {
                    val errorMsg = "Failed to start network interception: ${networkResult.exceptionOrNull()?.message}"
                    println(errorMsg)
                    // 提供更友好的错误信息
                    val userFriendlyError = if (errorMsg.contains("mitmdump")) {
                        "网络拦截启动失败：请确保已安装mitmproxy并添加到系统PATH中。参考README_mitmproxy.md获取安装说明。"
                    } else {
                        errorMsg
                    }
                    _appState.value = _appState.value.copy(
                        errorMessage = userFriendlyError
                    )
                    return
                }
                
                println("Network interception started successfully")
                
                // 更新应用状态
                _appState.value = _appState.value.copy(
                    isConnected = true,
                    errorMessage = null
                )
                
                // 在协程作用域内监听网络状态
                coroutineScope {
                    launch {
                        networkManager.observeNetworkStatus().collect { status ->
                            currentNetworkStatus = status
                            println("Network status updated: $status")
                        }
                    }
                }
            }
                
        } catch (e: Exception) {
            val errorMsg = "Game manager error: ${e.message}"
            println(errorMsg)
            e.printStackTrace()
            _appState.value = _appState.value.copy(
                errorMessage = errorMsg
            )
        }
    }
    
    /**
     * 处理网络消息
     */
    private suspend fun processNetworkMessage(message: LiqiMessage) {
        try {
            println("Processing network message: ${message.method}")
            
            // 更新游戏状态
            val gameStateResult = gameStateManager.updateGameState(message)
            
            if (gameStateResult.isSuccess) {
                // 获取当前游戏状态
                val gameState = gameStateRepository.getCurrentGameState()
                _appState.value = _appState.value.copy(
                    isInGame = gameState?.isGameActive == true,
                    currentGame = gameState,
                    errorMessage = null
                )
            } else {
                val errorMsg = "Failed to update game state: ${gameStateResult.exceptionOrNull()?.message}"
                println(errorMsg)
                _appState.value = _appState.value.copy(
                    errorMessage = errorMsg
                )
            }
            
        } catch (e: Exception) {
            val errorMsg = "Error processing message: ${e.message}"
            println(errorMsg)
            e.printStackTrace()
            _appState.value = _appState.value.copy(
                errorMessage = errorMsg
            )
        }
    }
}