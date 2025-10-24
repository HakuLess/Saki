package com.saki.mahjong.core

/**
 * 游戏状态类，用于存储雀魂游戏的完整信息
 */
class GameState {
    // 玩家手牌
    var playerHand: List<Tile> = emptyList()
    // 对手手牌数量（无法直接看到，只能看到数量）
    var opponentHandCounts: List<Int> = listOf(13, 13, 13)
    // 河牌信息（已打出的牌）
    var discards: List<List<Tile>> = emptyList()
    // 场风（东南西北）
    var roundWind: Wind = Wind.EAST
    // 局数（1-4）
    var roundNumber: Int = 1
    // 本场数
    var honba: Int = 0
    // 供托
    var deposit: Int = 0
    // 玩家分数
    var playerScores: List<Int> = listOf(25000, 25000, 25000, 25000)
    // 当前玩家
    var currentPlayer: Int = 0
    // 剩余牌数量
    var remainingTiles: Int = 70
    // 是否处于自己的回合
    var isMyTurn: Boolean = false
    // 是否已经立直
    var isRiichi: Boolean = false
    // 最后一张打出的牌
    var lastDiscard: Tile? = null
    // 可能的操作（吃、碰、杠、和等）
    var availableActions: List<Action> = emptyList()
    // 危险牌列表
    var dangerTiles: List<String> = emptyList()
    
    /**
     * 将GameState转换为可序列化的Map对象，用于与Python服务通信
     */
    fun toMap(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        result["player_hand"] = playerHand.map { "${it.type.symbol}${it.value}" }
        result["opponent_hand_counts"] = opponentHandCounts
        result["discards"] = discards.map { it.map { tile -> "${tile.type.symbol}${tile.value}" } }
        result["round_wind"] = roundWind.name
        result["round_number"] = roundNumber
        result["honba"] = honba
        result["deposit"] = deposit
        result["player_scores"] = playerScores
        result["current_player"] = currentPlayer
        result["remaining_tiles"] = remainingTiles
        result["is_my_turn"] = isMyTurn
        result["is_riichi"] = isRiichi
        
        // 只添加非null的last_discard
        lastDiscard?.let {
            result["last_discard"] = "${it.type.symbol}${it.value}"
        }
        
        result["available_actions"] = availableActions.map { it.name }
        result["danger_tiles"] = dangerTiles
        
        return result
    }

    /**
     * 更新游戏状态
     */
    fun updateFromMessage(message: GameMessage) {
        // 根据消息类型更新不同的游戏状态
        when (message.type) {
            MessageType.GAME_START -> updateGameStart(message)
            MessageType.ACTION_DISCARD -> updateActionDiscard(message)
            MessageType.ACTION_CHI_PENG_GANG -> updateActionChiPengGang(message)
            MessageType.ACTION_HULE -> updateActionHule(message)
            MessageType.ACTION_RIICHI -> updateActionRiichi(message)
            MessageType.ACTION_TSUMO -> updateActionTsumo(message)
            MessageType.ROUND_END -> updateRoundEnd(message)
            MessageType.GAME_END -> updateGameEnd(message)
        }
    }

    private fun updateGameStart(message: GameMessage) {
        // 初始化游戏状态
        playerHand = message.data["playerHand"] as? List<Tile> ?: emptyList()
        playerScores = message.data["playerScores"] as? List<Int> ?: listOf(25000, 25000, 25000, 25000)
        roundWind = Wind.values()[(message.data["roundWind"] as? Int ?: 0)]
        roundNumber = message.data["roundNumber"] as? Int ?: 1
    }

    private fun updateActionDiscard(message: GameMessage) {
        val playerIndex = message.data["playerIndex"] as? Int ?: 0
        val tile = message.data["tile"] as? Tile
        
        if (tile != null) {
            lastDiscard = tile
            // 更新河牌信息
            if (discards.size <= playerIndex) {
                discards = discards + listOf(listOf(tile))
            } else {
                discards = discards.toMutableList().apply {
                    this[playerIndex] = this[playerIndex] + tile
                }
            }
        }
        
        currentPlayer = (playerIndex + 1) % 4
        isMyTurn = currentPlayer == 0 // 假设自己总是0号玩家
    }

    private fun updateActionChiPengGang(message: GameMessage) {
        // 更新吃碰杠相关状态
        val actionType = message.data["actionType"] as? String
        val playerIndex = message.data["playerIndex"] as? Int ?: 0
        
        when (actionType) {
            "chi" -> updateChi(message)
            "peng" -> updatePeng(message)
            "gang" -> updateGang(message)
        }
    }

    private fun updateChi(message: GameMessage) {
        // 更新吃牌状态
    }

    private fun updatePeng(message: GameMessage) {
        // 更新碰牌状态
    }

    private fun updateGang(message: GameMessage) {
        // 更新杠牌状态
    }

    private fun updateActionHule(message: GameMessage) {
        // 更新和牌状态
        val winnerIndex = message.data["winnerIndex"] as? Int ?: 0
        // 更新分数等信息
    }
    
    private fun updateActionRiichi(message: GameMessage) {
        // 更新立直状态
        val playerIndex = message.data["playerIndex"] as? Int ?: 0
        if (playerIndex == 0) {
            isRiichi = true
        }
    }
    
    private fun updateActionTsumo(message: GameMessage) {
        // 更新摸牌状态
        val playerIndex = message.data["playerIndex"] as? Int ?: 0
        if (playerIndex == 0) {
            val newTile = message.data["tile"] as? Tile
            if (newTile != null) {
                playerHand = playerHand + newTile
            }
            isMyTurn = true
        }
        remainingTiles--
    }
    
    private fun updateRoundEnd(message: GameMessage) {
        // 更新回合结束状态
        playerScores = message.data["playerScores"] as? List<Int> ?: playerScores
        honba = message.data["honba"] as? Int ?: 0
        deposit = message.data["deposit"] as? Int ?: 0
    }
    
    private fun updateGameEnd(message: GameMessage) {
        // 更新游戏结束状态
        playerScores = message.data["finalScores"] as? List<Int> ?: playerScores
    }
    
    /**
     * 从雀魂原始消息更新游戏状态
     * 处理雀魂WebSocket发送的XML/JSON格式消息
     */
    fun updateFromTenhouMessage(tenhouMessage: String) {
        try {
            // 清理消息（移除可能的包装）
            val cleanMessage = tenhouMessage.trim()
            
            // 根据消息内容判断类型并更新状态
            when {
                cleanMessage.contains("<TAIKYOKU") -> parseTaikyokuMessage(cleanMessage)
                cleanMessage.contains("<INIT") -> parseInitMessage(cleanMessage)
                cleanMessage.contains("<TILE") -> parseTileMessage(cleanMessage)
                cleanMessage.contains("<DORA") -> parseDoraMessage(cleanMessage)
                cleanMessage.contains("<HAI") -> parseHaiMessage(cleanMessage)
                cleanMessage.contains("<N") -> parseNMessage(cleanMessage)
                cleanMessage.contains("<REACH") -> parseReachMessage(cleanMessage)
                cleanMessage.contains("<AGARI") -> parseAgariMessage(cleanMessage)
                cleanMessage.contains("<RYUUKYOKU") -> parseRyuukyokuMessage(cleanMessage)
                cleanMessage.contains("<OWARI") -> parseOwariMessage(cleanMessage)
                else -> parseGenericMessage(cleanMessage)
            }
        } catch (e: Exception) {
            println("解析雀魂消息失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 解析对局开始消息
     */
    private fun parseTaikyokuMessage(message: String) {
        // 提取场风、局数信息
        // <TAIKYOKU ba="0" kyoku="1" honba="0" scores="25000,25000,25000,25000" oya="0" hai0="128" hai1="128" hai2="128" hai3="128"/>
        val ba = extractAttribute(message, "ba").toIntOrNull() ?: 0
        val kyoku = extractAttribute(message, "kyoku").toIntOrNull() ?: 1
        val honba = extractAttribute(message, "honba").toIntOrNull() ?: 0
        val scores = extractAttribute(message, "scores").split(",").map { it.toIntOrNull() ?: 25000 }
        val oya = extractAttribute(message, "oya").toIntOrNull() ?: 0
        
        // 更新游戏状态
        roundWind = Wind.values()[ba % 4]
        roundNumber = kyoku
        this.honba = honba
        if (scores.size == 4) {
            playerScores = scores
        }
        currentPlayer = oya
        isMyTurn = currentPlayer == 0
        
        // 重置其他状态
        playerHand = emptyList()
        discards = listOf(emptyList(), emptyList(), emptyList(), emptyList())
        opponentHandCounts = listOf(13, 13, 13)
        remainingTiles = 70
        isRiichi = false
        lastDiscard = null
        availableActions = emptyList()
    }
    
    /**
     * 解析初始化消息
     */
    private fun parseInitMessage(message: String) {
        // 提取手牌信息
        // <INIT s="1" haipai="1111111111111"/>
        val haipai = extractAttribute(message, "haipai")
        if (haipai.isNotEmpty()) {
            playerHand = parseTiles(haipai)
        }
    }
    
    /**
     * 解析摸牌消息
     */
    private fun parseTileMessage(message: String) {
        // <TILE who="0" tile="11"/>
        val who = extractAttribute(message, "who").toIntOrNull() ?: 0
        val tileValue = extractAttribute(message, "tile")
        
        if (who == 0 && tileValue.isNotEmpty()) {
            val newTile = parseSingleTile(tileValue)
            if (newTile != null) {
                playerHand = playerHand + newTile
                isMyTurn = true
            }
        }
        remainingTiles--
    }
    
    /**
     * 解析打牌消息
     */
    private fun parseHaiMessage(message: String) {
        // <HAI who="0" hai="11" tsumo="1"/>
        val who = extractAttribute(message, "who").toIntOrNull() ?: 0
        val hai = extractAttribute(message, "hai")
        
        if (hai.isNotEmpty()) {
            val discardedTile = parseSingleTile(hai)
            if (discardedTile != null) {
                lastDiscard = discardedTile
                
                // 更新河牌
                if (discards.size > who) {
                    val newDiscards = discards.toMutableList()
                    newDiscards[who] = newDiscards[who] + discardedTile
                    discards = newDiscards
                }
                
                // 如果是自己打牌，更新手牌
                if (who == 0) {
                    playerHand = playerHand.filter { it != discardedTile }
                    isMyTurn = false
                }
                
                // 更新当前玩家
                currentPlayer = (who + 1) % 4
            }
        }
    }
    
    /**
     * 解析玩家操作消息
     */
    private fun parseNMessage(message: String) {
        // <N who="0" m="0"/>
        val who = extractAttribute(message, "who").toIntOrNull() ?: 0
        // 可以根据操作类型更新状态
    }
    
    /**
     * 解析立直消息
     */
    private fun parseReachMessage(message: String) {
        // <REACH who="0" step="1"/>
        val who = extractAttribute(message, "who").toIntOrNull() ?: 0
        val step = extractAttribute(message, "step").toIntOrNull() ?: 0
        
        if (who == 0 && step == 2) {  // step=2表示立直确认
            isRiichi = true
        }
    }
    
    /**
     * 解析和牌消息
     */
    private fun parseAgariMessage(message: String) {
        // <AGARI who="0" fromWho="1" han="1" fu="30" sc="1300" yaku="1"/>
        // 更新得分等信息
    }
    
    /**
     * 解析流局消息
     */
    private fun parseRyuukyokuMessage(message: String) {
        // <RYUUKYOKU ba="0" kyoku="1" honba="1" scores="25000,25000,25000,25000" ten="2000,2000,2000,2000" type="4"/>
        val honba = extractAttribute(message, "honba").toIntOrNull() ?: 0
        val scores = extractAttribute(message, "scores").split(",").map { it.toIntOrNull() ?: 25000 }
        
        this.honba = honba
        if (scores.size == 4) {
            playerScores = scores
        }
    }
    
    /**
     * 解析局结束消息
     */
    private fun parseOwariMessage(message: String) {
        // <OWARI ba="0" kyoku="1" honba="0" scores="26300,24000,24000,25700" ten="1300,-1000,-1000,700" oya="0"/>
        val honba = extractAttribute(message, "honba").toIntOrNull() ?: 0
        val scores = extractAttribute(message, "scores").split(",").map { it.toIntOrNull() ?: 25000 }
        
        this.honba = honba
        if (scores.size == 4) {
            playerScores = scores
        }
    }
    
    /**
     * 解析宝牌消息
     */
    private fun parseDoraMessage(message: String) {
        // <DORA dora="11"/>
        // 可以更新宝牌信息
    }
    
    /**
     * 解析通用消息
     */
    private fun parseGenericMessage(message: String) {
        // 处理其他类型的消息
    }
    
    /**
     * 从XML属性中提取值
     */
    private fun extractAttribute(xml: String, attribute: String): String {
        val pattern = "$attribute=\"([^\"]*)\""
        val regex = pattern.toRegex()
        return regex.find(xml)?.groupValues?.get(1) ?: ""
    }
    
    /**
     * 解析多张牌
     */
    private fun parseTiles(tileString: String): List<Tile> {
        val tiles = mutableListOf<Tile>()
        // 雀魂的牌表示方式：万子1-9, 筒子11-19, 索子21-29, 字牌31-37
        for (i in tileString.indices step 2) {
            if (i + 2 <= tileString.length) {
                val tileValue = tileString.substring(i, i + 2)
                val tile = parseSingleTile(tileValue)
                if (tile != null) {
                    tiles.add(tile)
                }
            }
        }
        return tiles
    }
    
    /**
     * 解析单张牌
     */
    private fun parseSingleTile(tileValue: String): Tile? {
        try {
            val value = tileValue.toInt()
            return when {
                value in 1..9 -> Tile(TileType.MANZU, value)
                value in 11..19 -> Tile(TileType.PINZU, value - 10)
                value in 21..29 -> Tile(TileType.SOUZU, value - 20)
                value in 31..37 -> {
                    // 字牌: 31-37 分别代表东南西北白发中
                    Tile(TileType.HONOR, value - 30)
                }
                else -> null
            }
        } catch (e: Exception) {
            return null
        }
    }
}

/**
 * 麻将牌类
 */
data class Tile(val type: TileType, val value: Int) {
    override fun toString(): String {
        return "${type.symbol}${value}"
    }
}

/**
 * 牌的类型
 */
enum class TileType(val symbol: String) {
    MANZU("m"), // 万子
    PINZU("p"), // 筒子
    SOUZU("s"), // 索子
    HONOR("z")  // 字牌
}

/**
 * 风向
 */
enum class Wind {
    EAST,   // 东
    SOUTH,  // 南
    WEST,   // 西
    NORTH   // 北
}

/**
 * 游戏动作
 */
enum class Action {
    Tsumo,      // 摸牌
    Discard,    // 打牌
    Riichi,     // 立直
    Chi,        // 吃
    Peng,       // 碰
    Kang,       // 杠
    Hule,       // 和
    Pass        // 跳过
}

/**
 * 游戏消息类型
 */
enum class MessageType {
    GAME_START,         // 游戏开始
    ACTION_DISCARD,     // 打牌
    ACTION_CHI_PENG_GANG, // 吃碰杠
    ACTION_HULE,        // 和牌
    ACTION_RIICHI,      // 立直
    ACTION_TSUMO,       // 摸牌
    ROUND_END,          // 回合结束
    GAME_END            // 游戏结束
}

/**
 * 游戏消息类
 */
data class GameMessage(val type: MessageType, val data: Map<String, Any>)