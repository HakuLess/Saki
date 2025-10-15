package mahjongcopilot.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import mahjongcopilot.data.model.*
import mahjongcopilot.domain.repository.GameStateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.Result

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
                // 游戏开始，返回初始游戏状态，但标记为活动状态
                current.copy(
                    gameId = message.data["gameId"]?.toString() ?: "game_${System.currentTimeMillis()}",
                    isGameActive = true
                )
            }
            LiqiMethod.ACTION_PROTOTYPE -> {
                // 处理游戏操作
                val operation = message.data["operation"] as? String
                when (operation) {
                    "newRound" -> {
                        // 处理新局开始，提取手牌信息
                        processNewRound(current, message)
                    }
                    "dealTile" -> {
                        // 处理摸牌
                        processDealTile(current, message)
                    }
                    "discardTile" -> {
                        // 处理打牌
                        processDiscardTile(current, message)
                    }
                    "chiPengGang" -> {
                        // 处理吃碰杠
                        processChiPengGang(current, message)
                    }
                    else -> current
                }
            }
            else -> current
        }
    }
    
    /**
     * 处理新局开始消息，提取手牌信息
     */
    private fun processNewRound(currentState: GameState, message: LiqiMessage): GameState {
        try {
            // 从消息中提取手牌信息
            val tilesData = message.data["tiles"] as? String
            if (tilesData != null) {
                // 解析手牌数据（这里需要根据实际数据格式进行解析）
                val tiles = parseTilesFromString(tilesData)
                
                // 更新当前玩家的手牌
                val currentPlayerSeat = currentState.currentPlayer
                val updatedPlayers = currentState.players.map { player ->
                    if (player.seat == currentPlayerSeat) {
                        player.copy(hand = tiles)
                    } else {
                        player
                    }
                }
                
                return currentState.copy(
                    players = updatedPlayers,
                    currentKyoku = message.data["ju"]?.toString()?.toIntOrNull() ?: currentState.currentKyoku,
                    currentHonba = message.data["ben"]?.toString()?.toIntOrNull() ?: currentState.currentHonba,
                    currentBakaze = parseBakaze(message.data["chang"]?.toString()?.toIntOrNull() ?: 0),
                    doraMarkers = parseDoraMarkers(message.data["doras"] as? String)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return currentState
    }
    
    /**
     * 处理摸牌消息
     */
    private fun processDealTile(currentState: GameState, message: LiqiMessage): GameState {
        try {
            val seat = message.data["seat"]?.toString()?.toIntOrNull() ?: 0
            val tileStr = message.data["tile"] as? String
            
            if (tileStr != null && seat == currentState.currentPlayer.index) {
                val tile = parseTileFromString(tileStr)
                if (tile != null) {
                    // 更新当前玩家的手牌（添加摸到的牌）
                    val currentPlayerSeat = currentState.currentPlayer
                    val updatedPlayers = currentState.players.map { player ->
                        if (player.seat == currentPlayerSeat) {
                            player.copy(hand = player.hand + tile)
                        } else {
                            player
                        }
                    }
                    return currentState.copy(players = updatedPlayers)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return currentState
    }
    
    /**
     * 处理打牌消息
     */
    private fun processDiscardTile(currentState: GameState, message: LiqiMessage): GameState {
        try {
            val seat = message.data["seat"]?.toString()?.toIntOrNull() ?: 0
            val tileStr = message.data["tile"] as? String
            
            if (tileStr != null && seat == currentState.currentPlayer.index) {
                val tile = parseTileFromString(tileStr)
                if (tile != null) {
                    // 更新当前玩家的手牌（移除打出的牌）
                    val currentPlayerSeat = currentState.currentPlayer
                    val updatedPlayers = currentState.players.map { player ->
                        if (player.seat == currentPlayerSeat) {
                            val updatedHand = player.hand.filter { it.type != tile.type }
                            player.copy(hand = updatedHand)
                        } else {
                            player
                        }
                    }
                    return currentState.copy(players = updatedPlayers)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return currentState
    }
    
    /**
     * 处理吃碰杠消息
     */
    private fun processChiPengGang(currentState: GameState, message: LiqiMessage): GameState {
        try {
            val seat = message.data["seat"]?.toString()?.toIntOrNull() ?: 0
            val type = message.data["type"]?.toString()?.toIntOrNull() ?: 0
            val tilesData = message.data["tiles"] as? List<*>
            
            if (seat == currentState.currentPlayer.index && tilesData != null) {
                // 解析面子牌
                val meldTiles = tilesData.mapNotNull { tileStr ->
                    (tileStr as? String)?.let { parseTileFromString(it) }
                }
                
                if (meldTiles.isNotEmpty()) {
                    val meldType = when (type) {
                        0 -> MeldType.CHI
                        1 -> MeldType.PON
                        2 -> MeldType.KAN
                        else -> MeldType.PON
                    }
                    
                    val meld = Meld(
                        type = meldType,
                        tiles = meldTiles
                    )
                    
                    // 更新当前玩家的面子
                    val currentPlayerSeat = currentState.currentPlayer
                    val updatedPlayers = currentState.players.map { player ->
                        if (player.seat == currentPlayerSeat) {
                            player.copy(melds = player.melds + meld)
                        } else {
                            player
                        }
                    }
                    return currentState.copy(players = updatedPlayers)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return currentState
    }
    
    /**
     * 从字符串解析手牌
     */
    private fun parseTilesFromString(tilesData: String): List<Tile> {
        // 这里需要根据实际的数据格式实现解析逻辑
        // 暂时返回空列表，后续根据实际数据格式实现
        return emptyList()
    }
    
    /**
     * 从字符串解析单张牌
     */
    private fun parseTileFromString(tileStr: String): Tile? {
        return TileType.fromString(tileStr)?.let { Tile(it) }
    }
    
    /**
     * 解析场风
     */
    private fun parseBakaze(chang: Int): TileType {
        return when (chang) {
            0 -> TileType.EAST
            1 -> TileType.SOUTH
            2 -> TileType.WEST
            3 -> TileType.NORTH
            else -> TileType.EAST
        }
    }
    
    /**
     * 解析宝牌指示牌
     */
    private fun parseDoraMarkers(dorasData: String?): List<Tile> {
        // 这里需要根据实际的数据格式实现解析逻辑
        // 暂时返回空列表，后续根据实际数据格式实现
        return emptyList()
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
            isGameActive = true
        )
    }
}