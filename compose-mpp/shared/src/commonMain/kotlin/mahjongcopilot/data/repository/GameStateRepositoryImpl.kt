package mahjongcopilot.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import mahjongcopilot.data.model.*
import mahjongcopilot.data.model.LiqiMethod
import mahjongcopilot.domain.repository.GameStateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 游戏状态仓库实现
 * 处理真实的Liqi消息并更新游戏状态
 */
class GameStateRepositoryImpl : GameStateRepository {
    
    private val _currentGameState = MutableStateFlow<GameState?>(null)
    private val currentGameState: Flow<GameState?> = _currentGameState.asStateFlow()
    
    private val mutex = Mutex()
    private var currentState: GameState? = null
    
    override suspend fun updateGameState(message: LiqiMessage): Result<GameState> {
        return try {
            mutex.withLock {
                println("Processing Liqi message: method=${message.method}, id=${message.id}")
                val newState = processMessage(message)
                currentState = newState
                _currentGameState.value = newState
                
                // 添加日志信息
                if (newState.isGameActive) {
                    println("Game state updated - Game ID: ${newState.gameId}")
                    println("Current player: ${newState.currentPlayer}")
                    println("Players count: ${newState.players.size}")
                    newState.players.forEach { player ->
                        println("Player ${player.seat}: ${player.name}, Hand size: ${player.hand.size}, Melds: ${player.melds.size}")
                    }
                }
                
                Result.success(newState)
            }
        } catch (e: Exception) {
            println("Error updating game state: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    override suspend fun getCurrentGameState(): GameState? {
        return mutex.withLock { currentState }
    }
    
    override suspend fun clearGameState(): Result<Unit> {
        return try {
            mutex.withLock {
                println("Clearing game state")
                currentState = null
                _currentGameState.value = null
                Result.success(Unit)
            }
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
        
        println("Processing message method: ${message.method}")
        
        return when (message.method) {
            LiqiMethod.AUTH_GAME -> {
                println("Processing AUTH_GAME message")
                // 游戏开始，初始化游戏状态
                val gameId = message.data["gameId"]?.toString() ?: "game_${System.currentTimeMillis()}"
                println("Game ID: $gameId")
                createInitialGameState().copy(
                    gameId = gameId,
                    isGameActive = true
                )
            }
            LiqiMethod.ACTION_PROTOTYPE -> {
                println("Processing ACTION_PROTOTYPE message")
                // 处理游戏操作
                val operationName = message.data["name"] as? String
                println("Operation name: $operationName")
                when (operationName) {
                    "ActionNewRound" -> {
                        println("Processing ActionNewRound")
                        // 处理新局开始，提取手牌信息
                        processNewRound(current, message)
                    }
                    "ActionDealTile" -> {
                        println("Processing ActionDealTile")
                        // 处理摸牌
                        processDealTile(current, message)
                    }
                    "ActionDiscardTile" -> {
                        println("Processing ActionDiscardTile")
                        // 处理打牌
                        processDiscardTile(current, message)
                    }
                    "ActionChiPengGang" -> {
                        println("Processing ActionChiPengGang")
                        // 处理吃碰杠
                        processChiPengGang(current, message)
                    }
                    else -> {
                        println("Unknown operation: $operationName")
                        current
                    }
                }
            }
            else -> {
                println("Unknown message method: ${message.method}")
                current
            }
        }
    }
    
    /**
     * 处理新局开始消息，提取手牌信息
     */
    private fun processNewRound(currentState: GameState, message: LiqiMessage): GameState {
        try {
            println("Processing new round message")
            // 从消息中提取数据
            val data = message.data["data"] as? Map<*, *>
            if (data != null) {
                println("New round data: $data")
                // 提取场风
                val chang = (data["chang"] as? Number)?.toInt() ?: 0
                val bakaze = parseBakaze(chang)
                println("Bakaze: $bakaze")
                
                // 提取局数和本场
                val kyoku = (data["ju"] as? Number)?.toInt() ?: 0
                val honba = (data["ben"] as? Number)?.toInt() ?: 0
                println("Kyoku: $kyoku, Honba: $honba")
                
                // 提取宝牌指示牌
                val doraMarkers = parseDoraMarkers(data["doras"] as? List<*>)
                println("Dora markers count: ${doraMarkers.size}")
                
                // 提取分数
                val scores = parseScores(data["scores"] as? List<*>)
                println("Scores: $scores")
                
                // 提取手牌
                val tiles = parseTilesFromList(data["tiles"] as? List<*>)
                println("Tiles count: ${tiles.size}")
                tiles.forEach { tile ->
                    println("Tile: ${tile.type.value}")
                }
                
                // 更新当前玩家的手牌
                val currentPlayerSeat = currentState.currentPlayer
                val updatedPlayers = currentState.players.map { player ->
                    if (player.seat == currentPlayerSeat) {
                        println("Updating hand for current player ${player.seat}")
                        player.copy(hand = tiles)
                    } else {
                        player
                    }
                }
                
                val newState = currentState.copy(
                    players = updatedPlayers,
                    currentKyoku = kyoku + 1, // 东一局是1
                    currentHonba = honba,
                    currentBakaze = bakaze,
                    doraMarkers = doraMarkers,
                    scores = scores,
                    isGameActive = true
                )
                
                println("New round processed successfully")
                return newState
            } else {
                println("No data found in new round message")
            }
        } catch (e: Exception) {
            println("Error processing new round: ${e.message}")
            e.printStackTrace()
        }
        return currentState
    }
    
    /**
     * 处理摸牌消息
     */
    private fun processDealTile(currentState: GameState, message: LiqiMessage): GameState {
        try {
            println("Processing deal tile message")
            val data = message.data["data"] as? Map<*, *>
            if (data != null) {
                println("Deal tile data: $data")
                val seat = (data["seat"] as? Number)?.toInt() ?: 0
                val tileStr = data["tile"] as? String
                
                println("Seat: $seat, Tile: $tileStr")
                
                if (tileStr != null && seat == currentState.currentPlayer.index) {
                    val tile = parseTileFromString(tileStr)
                    if (tile != null) {
                        println("Parsed tile: ${tile.type.value}")
                        // 更新当前玩家的手牌（添加摸到的牌）
                        val currentPlayerSeat = currentState.currentPlayer
                        val updatedPlayers = currentState.players.map { player ->
                            if (player.seat == currentPlayerSeat) {
                                println("Adding tile to player ${player.seat} hand")
                                player.copy(hand = player.hand + tile)
                            } else {
                                player
                            }
                        }
                        println("Deal tile processed successfully")
                        return currentState.copy(players = updatedPlayers)
                    } else {
                        println("Failed to parse tile: $tileStr")
                    }
                } else {
                    println("Tile not for current player or tile string is null")
                }
            } else {
                println("No data found in deal tile message")
            }
        } catch (e: Exception) {
            println("Error processing deal tile: ${e.message}")
            e.printStackTrace()
        }
        return currentState
    }
    
    /**
     * 处理打牌消息
     */
    private fun processDiscardTile(currentState: GameState, message: LiqiMessage): GameState {
        try {
            println("Processing discard tile message")
            val data = message.data["data"] as? Map<*, *>
            if (data != null) {
                println("Discard tile data: $data")
                val seat = (data["seat"] as? Number)?.toInt() ?: 0
                val tileStr = data["tile"] as? String
                
                println("Seat: $seat, Tile: $tileStr")
                
                if (tileStr != null && seat == currentState.currentPlayer.index) {
                    val tile = parseTileFromString(tileStr)
                    if (tile != null) {
                        println("Parsed tile: ${tile.type.value}")
                        // 更新当前玩家的手牌（移除打出的牌）
                        val currentPlayerSeat = currentState.currentPlayer
                        val updatedPlayers = currentState.players.map { player ->
                            if (player.seat == currentPlayerSeat) {
                                val updatedHand = player.hand.filter { it.type != tile.type }
                                println("Removing tile from player ${player.seat} hand, new hand size: ${updatedHand.size}")
                                player.copy(hand = updatedHand)
                            } else {
                                player
                            }
                        }
                        println("Discard tile processed successfully")
                        return currentState.copy(players = updatedPlayers)
                    } else {
                        println("Failed to parse tile: $tileStr")
                    }
                } else {
                    println("Tile not for current player or tile string is null")
                }
            } else {
                println("No data found in discard tile message")
            }
        } catch (e: Exception) {
            println("Error processing discard tile: ${e.message}")
            e.printStackTrace()
        }
        return currentState
    }
    
    /**
     * 处理吃碰杠消息
     */
    private fun processChiPengGang(currentState: GameState, message: LiqiMessage): GameState {
        try {
            println("Processing chi peng gang message")
            val data = message.data["data"] as? Map<*, *>
            if (data != null) {
                println("Chi peng gang data: $data")
                val seat = (data["seat"] as? Number)?.toInt() ?: 0
                val type = (data["type"] as? Number)?.toInt() ?: 0
                val tilesData = data["tiles"] as? List<*>
                
                println("Seat: $seat, Type: $type, Tiles: $tilesData")
                
                if (seat == currentState.currentPlayer.index && tilesData != null) {
                    // 解析面子牌
                    val meldTiles = tilesData.mapNotNull { tileStr ->
                        (tileStr as? String)?.let { parseTileFromString(it) }
                    }
                    
                    println("Meld tiles count: ${meldTiles.size}")
                    meldTiles.forEach { tile ->
                        println("Meld tile: ${tile.type.value}")
                    }
                    
                    if (meldTiles.isNotEmpty()) {
                        val meldType = when (type) {
                            0 -> MeldType.CHI
                            1 -> MeldType.PON
                            2 -> MeldType.KAN
                            else -> MeldType.PON
                        }
                        
                        println("Meld type: $meldType")
                        
                        val meld = Meld(
                            type = meldType,
                            tiles = meldTiles
                        )
                        
                        // 更新当前玩家的面子
                        val currentPlayerSeat = currentState.currentPlayer
                        val updatedPlayers = currentState.players.map { player ->
                            if (player.seat == currentPlayerSeat) {
                                println("Adding meld to player ${player.seat}, new melds count: ${player.melds.size + 1}")
                                player.copy(melds = player.melds + meld)
                            } else {
                                player
                            }
                        }
                        println("Chi peng gang processed successfully")
                        return currentState.copy(players = updatedPlayers)
                    } else {
                        println("No meld tiles found")
                    }
                } else {
                    println("Meld not for current player or tiles data is null")
                }
            } else {
                println("No data found in chi peng gang message")
            }
        } catch (e: Exception) {
            println("Error processing chi peng gang: ${e.message}")
            e.printStackTrace()
        }
        return currentState
    }
    
    /**
     * 从列表解析手牌
     */
    private fun parseTilesFromList(tilesData: List<*>?): List<Tile> {
        if (tilesData == null) {
            println("Tiles data is null")
            return emptyList()
        }
        
        println("Parsing tiles from list, count: ${tilesData.size}")
        val tiles = tilesData.mapNotNull { tileStr ->
            val str = tileStr as? String
            if (str != null) {
                val tile = parseTileFromString(str)
                if (tile != null) {
                    println("Parsed tile: $str -> ${tile.type.value}")
                } else {
                    println("Failed to parse tile: $str")
                }
                tile
            } else {
                println("Tile string is null: $tileStr")
                null
            }
        }
        println("Parsed tiles count: ${tiles.size}")
        return tiles
    }
    
    /**
     * 从字符串解析单张牌
     */
    private fun parseTileFromString(tileStr: String): Tile? {
        println("Parsing tile from string: $tileStr")
        val tileType = TileType.fromString(tileStr)
        if (tileType != null) {
            val tile = Tile(tileType)
            println("Parsed tile: $tileStr -> ${tile.type.value}")
            return tile
        } else {
            println("Failed to parse tile string: $tileStr")
            return null
        }
    }
    
    /**
     * 解析场风
     */
    private fun parseBakaze(chang: Int): TileType {
        val bakaze = when (chang) {
            0 -> TileType.EAST
            1 -> TileType.SOUTH
            2 -> TileType.WEST
            3 -> TileType.NORTH
            else -> TileType.EAST
        }
        println("Parsing bakaze: $chang -> ${bakaze.value}")
        return bakaze
    }
    
    /**
     * 解析宝牌指示牌
     */
    private fun parseDoraMarkers(dorasData: List<*>?): List<Tile> {
        if (dorasData == null) {
            println("Dora markers data is null")
            return emptyList()
        }
        
        println("Parsing dora markers, count: ${dorasData.size}")
        val doraMarkers = dorasData.mapNotNull { doraStr ->
            val str = doraStr as? String
            if (str != null) {
                val tile = parseTileFromString(str)
                if (tile != null) {
                    println("Parsed dora marker: $str -> ${tile.type.value}")
                } else {
                    println("Failed to parse dora marker: $str")
                }
                tile
            } else {
                println("Dora marker string is null: $doraStr")
                null
            }
        }
        println("Parsed dora markers count: ${doraMarkers.size}")
        return doraMarkers
    }
    
    /**
     * 解析分数
     */
    private fun parseScores(scoresData: List<*>?): List<Int> {
        if (scoresData == null) {
            println("Scores data is null, returning default scores")
            return listOf(25000, 25000, 25000, 25000)
        }
        
        println("Parsing scores, count: ${scoresData.size}")
        val scores = scoresData.map { score ->
            val intScore = (score as? Number)?.toInt() ?: 25000
            println("Parsed score: $score -> $intScore")
            intScore
        }
        println("Parsed scores: $scores")
        return scores
    }
    
    /**
     * 创建初始游戏状态
     */
    private fun createInitialGameState(): GameState {
        println("Creating initial game state")
        val players = listOf(
            Player(
                seat = PlayerSeat.EAST,
                name = "东家",
                hand = emptyList(),
                melds = emptyList(),
                isDealer = true
            ),
            Player(
                seat = PlayerSeat.SOUTH,
                name = "南家",
                hand = emptyList(),
                melds = emptyList()
            ),
            Player(
                seat = PlayerSeat.WEST,
                name = "西家",
                hand = emptyList(),
                melds = emptyList()
            ),
            Player(
                seat = PlayerSeat.NORTH,
                name = "北家",
                hand = emptyList(),
                melds = emptyList()
            )
        )
        
        val initialState = GameState(
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
        
        println("Initial game state created")
        return initialState
    }
}