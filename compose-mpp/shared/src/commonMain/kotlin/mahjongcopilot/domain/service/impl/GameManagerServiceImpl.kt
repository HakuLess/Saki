package mahjongcopilot.domain.service.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mahjongcopilot.data.model.GameState
import mahjongcopilot.data.model.GameAction
import mahjongcopilot.data.model.WindowInfo
import mahjongcopilot.data.model.Wind
import mahjongcopilot.data.model.GamePhase
import mahjongcopilot.data.repository.GameStateRepository
import mahjongcopilot.domain.service.GameManagerService
import mahjongcopilot.domain.service.ClientState
import java.util.logging.Logger

class GameManagerServiceImpl(
    private val gameStateRepository: GameStateRepository
) : GameManagerService {
    private val log = Logger.getLogger(javaClass.name)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _currentGameState = MutableStateFlow<GameState>(
        GameState(
            round = 1,
            honba = 0,
            deposit = 0,
            playerWind = Wind.EAST,
            roundWind = Wind.EAST,
            activePlayer = 0,
            remainingTiles = 136,
            scores = listOf(25000, 25000, 25000, 25000),
            handTiles = emptyList(),
            discardedTiles = listOf(emptyList(), emptyList(), emptyList(), emptyList()),
            openMelds = listOf(emptyList(), emptyList(), emptyList(), emptyList()),
            doras = emptyList()
        )
    )
    val currentGameState: StateFlow<GameState> = _currentGameState.asStateFlow()
    override val gameStateFlow: StateFlow<GameState?> = _currentGameState.asStateFlow()
    
    private val _clientState = MutableStateFlow<ClientState>(ClientState.NotConnected)
    override val clientStateFlow: StateFlow<ClientState> = _clientState.asStateFlow()
    
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    
    private val _gameEvents = MutableStateFlow<List<String>>(emptyList())
    val gameEvents: StateFlow<List<String>> = _gameEvents.asStateFlow()
    
    init {
        // 从仓库加载最后保存的游戏状态
        scope.launch {
            gameStateRepository.gameStateFlow.collect { savedState ->
                if (savedState != null) {
                    _currentGameState.value = savedState
                }
            }
        }
    }
    
    override suspend fun startMonitoring() {
        if (!_isMonitoring.value) {
            _isMonitoring.value = true
            log.info("开始监控游戏状态")
            
            // 启动游戏状态监控协程
            scope.launch {
                // 这里将在后续实现与网络拦截服务的集成
                // 目前只是模拟状态更新
                simulateGameStateChanges()
            }
        }
    }
    
    override suspend fun stopMonitoring() {
        if (_isMonitoring.value) {
            _isMonitoring.value = false
            log.info("停止监控游戏状态")
        }
    }
    
    override suspend fun connectToClient(): WindowInfo? {
        // TODO: 实现客户端连接逻辑
        return null
    }
    
    override fun getCurrentClientInfo(): WindowInfo? {
        // TODO: 实现获取当前客户端信息逻辑
        return null
    }
    
    override suspend fun refreshGameState() {
        // TODO: 实现刷新游戏状态逻辑
    }
    
    suspend fun processGameAction(action: GameAction): Boolean {
        return try {
            // 处理游戏动作并更新状态
            val newState = _currentGameState.value.copy(
                lastAction = action
            )
            
            _currentGameState.value = newState
            
            // 保存到仓库
            gameStateRepository.updateGameState(newState)
            
            // 记录事件
            addGameEvent("执行动作: ${action.type}")
            
            true
        } catch (e: Exception) {
            log.severe("处理游戏动作失败: ${e.message}")
            false
        }
    }
    
    suspend fun resetGameState() {
        val emptyState = GameState(
                round = 1,
                honba = 0,
                deposit = 0,
                playerWind = Wind.EAST,
                roundWind = Wind.EAST,
                activePlayer = 0,
                remainingTiles = 136,
                scores = listOf(25000, 25000, 25000, 25000),
                handTiles = emptyList(),
                discardedTiles = listOf(emptyList(), emptyList(), emptyList(), emptyList()),
                openMelds = listOf(emptyList(), emptyList(), emptyList(), emptyList()),
                doras = emptyList()
            )
        _currentGameState.value = emptyState
        gameStateRepository.updateGameState(emptyState)
        _gameEvents.value = emptyList()
        addGameEvent("游戏状态已重置")
    }
    
    fun dispose() {
        scope.cancel()
        log.info("GameManagerService已释放")
    }
    
    private suspend fun addGameEvent(event: String) {
        val timestamp = System.currentTimeMillis()
        val formattedEvent = "[$timestamp] $event"
        _gameEvents.value = _gameEvents.value + formattedEvent
        
        // 限制事件历史记录长度
        if (_gameEvents.value.size > 100) {
            _gameEvents.value = _gameEvents.value.takeLast(100)
        }
    }
    
    // 模拟游戏状态变化，用于测试
    private suspend fun simulateGameStateChanges() {
        while (_isMonitoring.value) {
            kotlinx.coroutines.delay(5000) // 每5秒模拟一次状态更新
            
            val newState = _currentGameState.value.copy(
                gamePhase = GamePhase.PLAYING // 模拟游戏进行中
            )
            
            _currentGameState.value = newState
            gameStateRepository.updateGameState(newState)
            
            addGameEvent("游戏状态已更新")
        }
    }
}