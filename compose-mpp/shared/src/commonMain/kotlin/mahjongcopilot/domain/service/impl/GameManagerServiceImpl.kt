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
                
                isRunning = true
                managerJob = CoroutineScope(Dispatchers.IO).launch {
                    startGameLoop()
                }
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun stopGameManager(): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isRunning) {
                    return Result.success(Unit)
                }
                
                isRunning = false
                managerJob?.cancel()
                managerJob = null
                
                // 停止网络拦截
                networkInterceptor.stopInterception()
                
                // 清除游戏状态
                gameStateRepository.clearGameState()
                
                // 更新应用状态
                _appState.value = AppState()
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
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
            // 启动网络拦截
            val networkResult = networkInterceptor.startInterception(
                NetworkSettings()
            )
            
            if (networkResult.isFailure) {
                _appState.value = _appState.value.copy(
                    errorMessage = "Failed to start network interception: ${networkResult.exceptionOrNull()?.message}"
                )
                return
            }
            
            // 更新应用状态
            _appState.value = _appState.value.copy(
                isConnected = true,
                errorMessage = null
            )
            
            // 监听网络消息
            networkInterceptor.observeNetworkMessages()
                .catch { e ->
                    _appState.value = _appState.value.copy(
                        errorMessage = "Network error: ${e.message}"
                    )
                }
                .collect { liqiMessage ->
                    processNetworkMessage(liqiMessage)
                }
                
        } catch (e: Exception) {
            _appState.value = _appState.value.copy(
                errorMessage = "Game manager error: ${e.message}"
            )
        }
    }
    
    /**
     * 处理网络消息
     */
    private suspend fun processNetworkMessage(liqiMessage: LiqiMessage) {
        try {
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
                _appState.value = _appState.value.copy(
                    errorMessage = "Failed to update game state: ${gameStateResult.exceptionOrNull()?.message}"
                )
            }
            
        } catch (e: Exception) {
            _appState.value = _appState.value.copy(
                errorMessage = "Error processing message: ${e.message}"
            )
        }
    }
}
