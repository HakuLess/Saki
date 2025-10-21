package mahjongcopilot.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mahjongcopilot.data.model.GameState

/**
 * 游戏状态仓库的默认实现
 */
class GameStateRepositoryImpl : GameStateRepository {
    
    // 游戏状态流
    private val _gameStateFlow = MutableStateFlow<GameState?>(null)
    override val gameStateFlow: StateFlow<GameState?> = _gameStateFlow.asStateFlow()
    
    // 游戏记录列表
    private val gameRecords = mutableListOf<GameState>()
    
    // 互斥锁，确保线程安全
    private val mutex = Mutex()
    
    override suspend fun updateGameState(newState: GameState) {
        mutex.withLock {
            _gameStateFlow.value = newState
        }
    }
    
    override suspend fun resetGameState() {
        mutex.withLock {
            _gameStateFlow.value = null
        }
    }
    
    override suspend fun saveGameRecord(gameState: GameState) {
        mutex.withLock {
            gameRecords.add(gameState)
            // 限制记录数量，避免内存占用过高
            if (gameRecords.size > 100) {
                gameRecords.removeAt(0)
            }
        }
    }
    
    /**
     * 获取最近的游戏记录
     * @param limit 限制数量
     */
    suspend fun getRecentRecords(limit: Int = 10): List<GameState> {
        return mutex.withLock {
            gameRecords.takeLast(limit)
        }
    }
    
    /**
     * 清空所有游戏记录
     */
    suspend fun clearAllRecords() {
        mutex.withLock {
            gameRecords.clear()
        }
    }
}