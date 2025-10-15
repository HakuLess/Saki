package mahjongcopilot.domain.service.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mahjongcopilot.data.model.*
import mahjongcopilot.domain.repository.*
import mahjongcopilot.domain.service.GameManagerService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 游戏管理服务实现
 */
class GameManagerServiceImpl(
    private val networkInterceptor: NetworkInterceptorRepository,
    private val protocolParser: ProtocolParserRepository,
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
                    val stopResult = networkInterceptor.stopInterception()
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
            
            // 启动网络拦截
            println("Starting network interception...")
            val networkResult = networkInterceptor.startInterception(
                NetworkSettings()
            )
            
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
            
            // 监听网络消息
            networkInterceptor.observeNetworkMessages()
                .catch { e ->
                    val errorMsg = "Network error: ${e.message}"
                    println(errorMsg)
                    _appState.value = _appState.value.copy(
                        errorMessage = errorMsg
                    )
                }
                .collect { liqiMessage ->
                    processNetworkMessage(liqiMessage)
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
    private suspend fun processNetworkMessage(liqiMessage: LiqiMessage) {
        try {
            println("Processing network message: method=${liqiMessage.method}")
            
            // 更新游戏状态
            val gameStateResult = gameStateRepository.updateGameState(liqiMessage)
            
            if (gameStateResult.isSuccess) {
                val gameState = gameStateResult.getOrNull()
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