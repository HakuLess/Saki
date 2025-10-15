package mahjongcopilot.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import mahjongcopilot.data.model.*
import mahjongcopilot.domain.repository.GameStateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 游戏状态仓库实现
 */
class GameStateRepositoryImpl : GameStateRepository {
    
    private val _currentGameState = MutableStateFlow<GameState?>(null)
    private val currentGameState: Flow<GameState?> = _currentGameState.asStateFlow()
    
    private val mutex = Mutex()
    private var currentState: GameState? = null
    
    override suspend fun updateGameState(message: LiqiMessage): Result<GameState> {
        return try {
            mutex.withLock {
                val newState = processMessage(message)
                currentState = newState
                _currentGameState.value = newState
                Result.success(newState)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getCurrentGameState(): GameState? {
        return mutex.withLock { currentState }
    }
    
    override suspend fun clearGameState(): Result<Unit> {
        return try {
            mutex.withLock {
                currentState = null
                _currentGameState.value = null
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun observeGameState(): Flow<GameState?> {
        return currentGameState
    }
    
    /**
     * 处理消息并更新游戏状态
     */
    private fun processMessage(message: LiqiMessage): GameState {
        val current = currentState ?: createInitialGameState()
        
        return when (message.method) {
            LiqiMethod.AUTH_GAME -> {
                // 游戏开始
                current.copy(
                    gameId = message.data["gameId"]?.toString() ?: "mock_game_${System.currentTimeMillis()}",
                    isGameActive = true
                )
            }
            LiqiMethod.INPUT_OPERATION -> {
                // 处理游戏操作
                val operation = message.data["operation"] as? String
                when (operation) {
                    "discard" -> {
                        val actor = message.data["actor"] as? Int ?: 0
                        val pai = message.data["pai"] as? String
                        updatePlayerHand(current, actor, pai, isDiscard = true)
                    }
                    "chi", "pon", "kan" -> {
                        val actor = message.data["actor"] as? Int ?: 0
                        val pai = message.data["pai"] as? String
                        val consumed = (message.data["consumed"] as? List<*>)?.map { it.toString() } ?: emptyList()
                        updatePlayerMeld(current, actor, pai, consumed, operation)
                    }
                    else -> current
                }
            }
            else -> current
        }
    }
    
    /**
     * 创建初始游戏状态
     */
    private fun createInitialGameState(): GameState {
        val players = listOf(
            Player(
                seat = PlayerSeat.EAST,
                name = "玩家1",
                hand = emptyList(),
                melds = emptyList(),
                isDealer = true
            ),
            Player(
                seat = PlayerSeat.SOUTH,
                name = "玩家2",
                hand = emptyList(),
                melds = emptyList()
            ),
            Player(
                seat = PlayerSeat.WEST,
                name = "玩家3",
                hand = emptyList(),
                melds = emptyList()
            ),
            Player(
                seat = PlayerSeat.NORTH,
                name = "玩家4",
                hand = emptyList(),
                melds = emptyList()
            )
        )
        
        return GameState(
            gameId = "initial_game",
            mode = GameMode.FOUR_PLAYER,
            currentPlayer = PlayerSeat.EAST,
            players = players,
            currentKyoku = 1,
            currentHonba = 0,
            currentBakaze = TileType.EAST,
            doraMarkers = emptyList(),
            scores = listOf(25000, 25000, 25000, 25000),
            isGameActive = false
        )
    }
    
    /**
     * 更新玩家手牌
     */
    private fun updatePlayerHand(gameState: GameState, actor: Int, pai: String?, isDiscard: Boolean): GameState {
        val seat = PlayerSeat.fromIndex(actor) ?: return gameState
        val players = gameState.players.map { player ->
            if (player.seat == seat) {
                val tile = pai?.let { TileType.fromString(it) }?.let { Tile(it) }
                val newHand = if (isDiscard && tile != null) {
                    player.hand.filter { it.type != tile.type }
                } else if (!isDiscard && tile != null) {
                    player.hand + tile
                } else {
                    player.hand
                }
                player.copy(hand = newHand)
            } else {
                player
            }
        }
        
        return gameState.copy(players = players)
    }
    
    /**
     * 更新玩家面子
     */
    private fun updatePlayerMeld(gameState: GameState, actor: Int, pai: String?, consumed: List<String>, operation: String): GameState {
        val seat = PlayerSeat.fromIndex(actor) ?: return gameState
        val players = gameState.players.map { player ->
            if (player.seat == seat) {
                val meldType = when (operation) {
                    "chi" -> MeldType.CHI
                    "pon" -> MeldType.PON
                    "kan" -> MeldType.KAN
                    else -> MeldType.PON
                }
                
                val tiles = consumed.mapNotNull { TileType.fromString(it) }.map { Tile(it) }
                val newMeld = Meld(
                    type = meldType,
                    tiles = tiles
                )
                
                player.copy(melds = player.melds + newMeld)
            } else {
                player
            }
        }
        
        return gameState.copy(players = players)
    }
}
