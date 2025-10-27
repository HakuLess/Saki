package decoder

import model.*
import java.util.Base64
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable

/**
 * 雀魂（MahjongSoul）消息解码器：
 * - 解析雀魂的网络数据包（WebSocket消息）
 * - 提取游戏状态信息，特别是手牌数据
 */
object MajsoulDecoder {

    /**
     * 输入原始帧字节，返回解析出的麻将事件
     */
    fun decodeFrame(frame: ByteArray): List<GameEvent> {
        return try {
            // 尝试解析为文本消息（雀魂使用WebSocket文本消息）
            val text = frame.toString(Charsets.UTF_8)
            parseTextMessage(text)
        } catch (e: Exception) {
            // 如果不是文本消息，尝试其他解析方式
            parseBinaryMessage(frame)
        }
    }

    /**
     * 解析文本消息（JSON格式）
     */
    private fun parseTextMessage(text: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        
        try {
            // 雀魂的消息通常是JSON格式
            val json = Json.parseToJsonElement(text)
            
            if (json is JsonObject) {
                // 检查消息类型
                val method = json["method"]?.jsonPrimitive?.content
                val data = json["data"]?.jsonObject
                
                when (method) {
                    "ActionPrototype" -> {
                        // 游戏动作消息
                        parseGameAction(data, events)
                    }
                    "NotifyGameEndResult" -> {
                        // 游戏结束消息
                        parseGameEnd(data, events)
                    }
                    "NotifyPlayerLoadGameReady" -> {
                        // 游戏开始消息
                        events.add(RoundStarted(System.currentTimeMillis()))
                    }
                    // 增强：更多雀魂协议消息类型
                    "NotifyGameStart" -> {
                        // 游戏开始通知
                        events.add(RoundStarted(System.currentTimeMillis()))
                        println("[Decoder] 游戏开始通知")
                    }
                    "NotifyPlayerEnter" -> {
                        // 玩家进入游戏
                        println("[Decoder] 玩家进入游戏")
                    }
                    "NotifyDealTile" -> {
                        // 发牌通知 - 这是获取手牌的关键消息
                        parseDealTile(data, events)
                    }
                    "NotifyDiscardTile" -> {
                        // 打牌通知
                        parseDiscardTile(data, events)
                    }
                    "NotifyChiPengGang" -> {
                        // 吃碰杠通知
                        parseChiPengGang(data, events)
                    }
                    "NotifyGameReady" -> {
                        // 游戏准备
                        println("[Decoder] 游戏准备")
                    }
                    else -> {
                        // 尝试解析通用游戏状态
                        parseGeneralGameState(json, events)
                    }
                }
            }
            
            // 记录解析到的消息
            if (text.length < 200) {
                println("[Decoder] 解析消息: $text")
            } else {
                println("[Decoder] 解析长消息 (${text.length} 字符): ${text.take(100)}...")
            }
            
        } catch (e: Exception) {
            println("[Decoder] JSON解析失败: ${e.message}")
        }
        
        return events
    }

    /**
     * 解析二进制消息（雀魂的Protobuf协议）
     */
    private fun parseBinaryMessage(data: ByteArray): List<GameEvent> {
        if (data.size < 3) {
            println("[MajsoulDecoder] 数据太短，无法解析: ${data.size} bytes")
            return emptyList()
        }
        
        println("[MajsoulDecoder] ===== 开始解析二进制消息 =====")
        println("[MajsoulDecoder] 数据大小: ${data.size} bytes")
        println("[MajsoulDecoder] 数据预览: ${data.take(16).joinToString(" ") { "%02X".format(it) }}")
        
        try {
            var offset = 0
            
            // 读取消息类型 (1 byte)
            val messageType = data[offset].toInt() and 0xFF
            offset++
            println("[MajsoulDecoder] 消息类型: $messageType")
            
            // 读取消息ID长度 (varint)
            val (messageIdLength, lengthBytes) = readVarint(data, offset)
            offset += lengthBytes
            println("[MajsoulDecoder] 消息ID长度: $messageIdLength")
            
            if (offset + messageIdLength > data.size) {
                println("[MajsoulDecoder] 消息ID长度超出数据范围")
                return emptyList()
            }
            
            // 读取消息ID
            val messageId = String(data, offset, messageIdLength, Charsets.UTF_8)
            offset += messageIdLength
            println("[MajsoulDecoder] 消息ID: $messageId")
            
            // 剩余数据为Protobuf负载
            val protobufData = data.sliceArray(offset until data.size)
            println("[MajsoulDecoder] Protobuf数据大小: ${protobufData.size} bytes")
            println("[MajsoulDecoder] Protobuf数据预览: ${protobufData.take(32).joinToString(" ") { "%02X".format(it) }}")
            
            // 根据消息类型和ID处理
            val events = when (messageType) {
                1 -> handleNotifyMessage(messageId, protobufData)
                2 -> handleRequestMessage(messageId, protobufData)
                3 -> handleResponseMessage(messageId, protobufData)
                else -> {
                    println("[MajsoulDecoder] 未知消息类型: $messageType")
                    emptyList()
                }
            }
            
            println("[MajsoulDecoder] 解析完成，生成 ${events.size} 个事件")
            println("[MajsoulDecoder] =============================")
            
            return events
            
        } catch (e: Exception) {
            println("[MajsoulDecoder] 解析异常: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }
    
    /**
     * 处理通知消息
     */
    private fun handleNotifyMessage(messageId: String, data: ByteArray): List<GameEvent> {
        println("[MajsoulDecoder] 处理通知消息: $messageId")
        
        return when {
            messageId.contains("NotifyGameStart", ignoreCase = true) -> {
                println("[MajsoulDecoder] 检测到游戏开始通知")
                listOf(RoundStarted(timestamp = System.currentTimeMillis()))
            }
            messageId.contains("NotifyDealTile", ignoreCase = true) -> {
                println("[MajsoulDecoder] 检测到发牌通知")
                parseHandTilesFromProtobuf(data, "NotifyDealTile")
            }
            messageId.contains("NotifyDiscardTile", ignoreCase = true) -> {
                println("[MajsoulDecoder] 检测到打牌通知")
                parseDiscardFromProtobuf(data)
            }
            messageId.contains("NotifyChiPengGang", ignoreCase = true) -> {
                println("[MajsoulDecoder] 检测到副露通知")
                parseMeldFromProtobuf(data)
            }
            else -> {
                println("[MajsoulDecoder] 尝试从未知通知中提取手牌数据: $messageId")
                parseHandTilesFromProtobuf(data, messageId)
            }
        }
    }
    
    /**
     * 处理请求消息
     */
    private fun handleRequestMessage(messageId: String, data: ByteArray): List<GameEvent> {
        println("[MajsoulDecoder] 处理请求消息: $messageId")
        // 请求消息通常不包含游戏状态更新
        return emptyList()
    }
    
    /**
     * 处理响应消息
     */
    private fun handleResponseMessage(messageId: String, data: ByteArray): List<GameEvent> {
        println("[MajsoulDecoder] 处理响应消息: $messageId")
        
        return when {
            messageId.contains("authGame", ignoreCase = true) -> {
                println("[MajsoulDecoder] 检测到游戏认证响应")
                parseHandTilesFromProtobuf(data, "authGame")
            }
            messageId.contains("syncGame", ignoreCase = true) -> {
                println("[MajsoulDecoder] 检测到游戏同步响应")
                parseHandTilesFromProtobuf(data, "syncGame")
            }
            else -> {
                println("[MajsoulDecoder] 尝试从响应消息中提取手牌数据: $messageId")
                parseHandTilesFromProtobuf(data, messageId)
            }
        }
    }
    
    /**
     * 从Protobuf数据中解析手牌信息
     */
    private fun parseHandTilesFromProtobuf(data: ByteArray, context: String): List<GameEvent> {
        println("[MajsoulDecoder] 开始从Protobuf解析手牌数据 (上下文: $context)")
        
        if (data.isEmpty()) {
            println("[MajsoulDecoder] Protobuf数据为空")
            return emptyList()
        }
        
        val events = mutableListOf<GameEvent>()
        
        try {
            // 搜索可能的手牌数据
            val handTiles = searchForHandTilesInProtobuf(data)
            println("[MajsoulDecoder] 搜索到 ${handTiles.size} 组手牌数据")
            
            handTiles.forEachIndexed { index, tiles ->
                if (tiles.isNotEmpty()) {
                    println("[MajsoulDecoder] 玩家 $index 手牌原始数据: $tiles")
                    
                    // 转换为标准格式
                    val convertedTiles = tiles.map { convertMajsoulTileToString(it) }
                    println("[MajsoulDecoder] 玩家 $index 转换后手牌: $convertedTiles")
                    
                    events.add(
                        HandTilesUpdated(
                            playerId = index,
                            tiles = convertedTiles,
                            fieldName = context,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            println("[MajsoulDecoder] 解析手牌数据异常: ${e.message}")
            e.printStackTrace()
        }
        
        return events
    }
    
    /**
     * 解析打牌通知的Protobuf数据
     */
    private fun parseDiscardFromProtobuf(data: ByteArray): List<GameEvent> {
        println("[MajsoulDecoder] 解析打牌Protobuf数据")
        // 暂时返回空，可以后续实现
        return emptyList()
    }
    
    /**
     * 解析副露通知的Protobuf数据
     */
    private fun parseMeldFromProtobuf(data: ByteArray): List<GameEvent> {
        println("[MajsoulDecoder] 解析副露Protobuf数据")
        // 暂时返回空，可以后续实现
        return emptyList()
    }
    
    /**
     * 在Protobuf数据中搜索手牌信息
     */
    private fun searchForHandTilesInProtobuf(data: ByteArray): List<List<Int>> {
        println("[MajsoulDecoder] 开始搜索Protobuf手牌数据...")
        
        val results = mutableListOf<List<Int>>()
        var offset = 0
        
        while (offset < data.size - 1) {
            try {
                // 查找可能的字段标识
                val fieldTag = data[offset].toInt() and 0xFF
                val wireType = fieldTag and 0x07
                val fieldNumber = fieldTag shr 3
                
                println("[MajsoulDecoder] 偏移 $offset: 字段标签=$fieldTag, 线路类型=$wireType, 字段号=$fieldNumber")
                
                when (wireType) {
                    0 -> { // Varint
                        val (value, bytes) = readVarint(data, offset + 1)
                        println("[MajsoulDecoder] Varint值: $value")
                        
                        // 检查是否是有效的麻将牌值
                        if (isMajsoulTileValue(value)) {
                            println("[MajsoulDecoder] 发现可能的单张牌: $value")
                            results.add(listOf(value))
                        }
                        
                        offset = offset + 1 + bytes
                    }
                    2 -> { // Length-delimited
                        if (offset + 1 >= data.size) break
                        
                        val (length, lengthBytes) = readVarint(data, offset + 1)
                        val dataStart = offset + 1 + lengthBytes
                        val dataEnd = dataStart + length
                        
                        println("[MajsoulDecoder] 长度限定字段: 长度=$length, 数据范围=$dataStart-$dataEnd")
                        
                        if (dataEnd <= data.size && length > 0) {
                            val fieldData = data.sliceArray(dataStart until dataEnd)
                            
                            // 尝试解析为牌列表
                            val tiles = tryParseAsTileList(fieldData)
                            if (tiles.isNotEmpty()) {
                                println("[MajsoulDecoder] 发现手牌列表: $tiles")
                                results.add(tiles)
                            }
                        }
                        
                        offset = dataEnd
                    }
                    else -> {
                        println("[MajsoulDecoder] 跳过未知线路类型: $wireType")
                        offset++
                    }
                }
                
            } catch (e: Exception) {
                println("[MajsoulDecoder] 搜索异常，跳过: ${e.message}")
                offset++
            }
        }
        
        println("[MajsoulDecoder] 搜索完成，找到 ${results.size} 组数据")
        return results
    }
    
    /**
     * 尝试将字节数据解析为牌列表
     */
    private fun tryParseAsTileList(data: ByteArray): List<Int> {
        val tiles = mutableListOf<Int>()
        var offset = 0
        
        println("[MajsoulDecoder] 尝试解析牌列表，数据大小: ${data.size}")
        
        while (offset < data.size) {
            try {
                val (value, bytes) = readVarint(data, offset)
                
                if (isMajsoulTileValue(value)) {
                    tiles.add(value)
                    println("[MajsoulDecoder] 解析到牌: $value")
                } else if (value > 0 && value < 256) {
                    // 可能是其他格式的牌值
                    tiles.add(value)
                    println("[MajsoulDecoder] 解析到可能的牌值: $value")
                }
                
                offset += bytes
                
            } catch (e: Exception) {
                println("[MajsoulDecoder] 解析牌值异常: ${e.message}")
                break
            }
        }
        
        println("[MajsoulDecoder] 解析完成，共 ${tiles.size} 张牌")
        return tiles
    }
    
    /**
     * 检查是否是有效的雀魂牌值
     */
    private fun isMajsoulTileValue(value: Int): Boolean {
        return when (value) {
            in 11..19 -> true  // 万子
            in 21..29 -> true  // 筒子
            in 31..39 -> true  // 索子
            in 41..47 -> true  // 字牌
            in 1..9 -> true    // 简化格式
            in 51..57 -> true  // 可能的字牌变体
            else -> false
        }
    }
    
    /**
     * 将雀魂牌值转换为字符串
     */
    private fun convertMajsoulTileToString(tileValue: Int): String {
        println("[MajsoulDecoder] 转换牌值: $tileValue")
        
        return when (tileValue) {
            in 11..19 -> "${tileValue - 10}m"  // 万子
            in 21..29 -> "${tileValue - 20}p"  // 筒子
            in 31..39 -> "${tileValue - 30}s"  // 索子
            in 41..47 -> "${tileValue - 40}z"  // 字牌
            in 1..9 -> "${tileValue}m"         // 默认万子
            in 51..57 -> "${tileValue - 50}z"  // 字牌变体
            else -> {
                println("[MajsoulDecoder] 未知牌值: $tileValue")
                tileValue.toString()
            }
        }
    }
    
    /**
     * 读取变长整数 (varint)
     */
    private fun readVarint(data: ByteArray, startOffset: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var offset = startOffset
        
        while (offset < data.size) {
            val byte = data[offset].toInt() and 0xFF
            result = result or ((byte and 0x7F) shl shift)
            offset++
            
            if ((byte and 0x80) == 0) {
                break
            }
            shift += 7
        }
        
        return Pair(result, offset - startOffset)
    }

    /**
     * 解析发牌通知 - 获取手牌数据的关键消息
     */
    private fun parseDealTile(data: JsonObject?, events: MutableList<GameEvent>) {
        data ?: return
        
        // 雀魂发牌消息可能包含以下字段：
        // - seat: 玩家座位号
        // - tile: 发的牌
        // - tiles: 手牌列表（初始发牌时）
        // - hand: 手牌数据
        
        val seat = data["seat"]?.jsonPrimitive?.int ?: 0
        
        // 检查是否有手牌列表（初始发牌）
        val tilesArray = data["tiles"]?.jsonArray
        if (tilesArray != null) {
            val tiles = tilesArray.mapNotNull { it.jsonPrimitive?.contentOrNull }
            if (tiles.isNotEmpty()) {
                events.add(HandTilesUpdated(
                    playerId = seat,
                    tiles = tiles,
                    fieldName = "tiles",
                    timestamp = System.currentTimeMillis()
                ))
                println("[Decoder] 玩家 $seat 初始手牌: ${tiles.joinToString(",")}")
            }
        }
        
        // 检查单张牌（摸牌）
        val tile = data["tile"]?.jsonPrimitive?.content
        if (tile != null) {
            events.add(TileDrawn(seat, tile, System.currentTimeMillis()))
            println("[Decoder] 玩家 $seat 摸牌: $tile")
        }
        
        // 检查手牌字段
        val handData = data["hand"]?.jsonArray
        if (handData != null) {
            val handTiles = handData.mapNotNull { it.jsonPrimitive?.contentOrNull }
            if (handTiles.isNotEmpty()) {
                events.add(HandTilesUpdated(
                    playerId = seat,
                    tiles = handTiles,
                    fieldName = "hand",
                    timestamp = System.currentTimeMillis()
                ))
                println("[Decoder] 玩家 $seat 手牌更新: ${handTiles.joinToString(",")}")
            }
        }
    }

    /**
     * 解析打牌通知
     */
    private fun parseDiscardTile(data: JsonObject?, events: MutableList<GameEvent>) {
        data ?: return
        
        val seat = data["seat"]?.jsonPrimitive?.int ?: 0
        val tile = data["tile"]?.jsonPrimitive?.content
        
        if (tile != null) {
            events.add(TileDiscarded(seat, tile, System.currentTimeMillis()))
            println("[Decoder] 玩家 $seat 打牌: $tile")
        }
    }

    /**
     * 解析吃碰杠通知
     */
    private fun parseChiPengGang(data: JsonObject?, events: MutableList<GameEvent>) {
        data ?: return
        
        val seat = data["seat"]?.jsonPrimitive?.int ?: 0
        val type = data["type"]?.jsonPrimitive?.int ?: 0
        val tiles = data["tiles"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content } ?: emptyList()
        
        val meldType = when (type) {
            0 -> "chi"
            1 -> "pon"
            2, 3 -> "kan"
            else -> "unknown"
        }
        
        if (tiles.isNotEmpty()) {
            events.add(MeldMade(seat, meldType, tiles, System.currentTimeMillis()))
            println("[Decoder] 玩家 $seat 副露 $meldType: ${tiles.joinToString(",")}")
        }
    }
    private fun parseGameAction(data: JsonObject?, events: MutableList<GameEvent>) {
        data ?: return
        
        val actionType = data["name"]?.jsonPrimitive?.content
        val step = data["step"]?.jsonPrimitive?.int ?: 0
        
        when (actionType) {
            "ActionDealTile" -> {
                // 摸牌动作
                val seat = data["seat"]?.jsonPrimitive?.int ?: 0
                val tile = data["tile"]?.jsonPrimitive?.content
                if (tile != null) {
                    events.add(TileDrawn(seat, tile, System.currentTimeMillis()))
                    println("[Decoder] 玩家 $seat 摸牌: $tile")
                }
            }
            "ActionDiscardTile" -> {
                // 打牌动作
                val seat = data["seat"]?.jsonPrimitive?.int ?: 0
                val tile = data["tile"]?.jsonPrimitive?.content
                if (tile != null) {
                    events.add(TileDiscarded(seat, tile, System.currentTimeMillis()))
                    println("[Decoder] 玩家 $seat 打牌: $tile")
                }
            }
            "ActionChiPengGang" -> {
                // 吃碰杠动作
                val seat = data["seat"]?.jsonPrimitive?.int ?: 0
                val type = data["type"]?.jsonPrimitive?.int ?: 0
                val tiles = data["tiles"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                
                val meldType = when (type) {
                    0 -> "chi"
                    1 -> "pon" 
                    2, 3 -> "kan"
                    else -> "unknown"
                }
                
                events.add(MeldMade(seat, meldType, tiles, System.currentTimeMillis()))
                println("[Decoder] 玩家 $seat 副露 $meldType: ${tiles.joinToString(",")}")
            }
        }
    }

    /**
     * 解析游戏结束
     */
    private fun parseGameEnd(data: JsonObject?, events: MutableList<GameEvent>) {
        data ?: return
        
        val result = data["result"]?.jsonObject
        if (result != null) {
            // 检查是否有人和牌
            val players = result["players"]?.jsonArray
            players?.forEachIndexed { index, player ->
                val playerObj = player.jsonObject
                val handResult = playerObj["hand_result"]?.jsonObject
                if (handResult != null) {
                    val isWin = handResult["is_win"]?.jsonPrimitive?.boolean ?: false
                    if (isWin) {
                        val winType = if (handResult["is_zimo"]?.jsonPrimitive?.boolean == true) "tsumo" else "ron"
                        events.add(HandWon(index, winType, System.currentTimeMillis()))
                        println("[Decoder] 玩家 $index 和牌: $winType")
                    }
                }
            }
        }
    }

    /**
     * 解析通用游戏状态（寻找手牌信息）
     */
    private fun parseGeneralGameState(json: JsonElement, events: MutableList<GameEvent>) {
        // 递归搜索手牌相关的字段
        searchForHandTiles(json, events)
    }

    /**
     * 递归搜索手牌信息 - 增强版
     */
    private fun searchForHandTiles(element: JsonElement, events: MutableList<GameEvent>) {
        when (element) {
            is JsonObject -> {
                element.forEach { key: String, value: JsonElement ->
                    // 扩展手牌字段匹配规则
                    val isHandTileField = key.contains("hand", ignoreCase = true) || 
                        key.contains("tile", ignoreCase = true) ||
                        key == "tiles" || key == "pai" ||
                        key == "tehai" || // 日文：手牌
                        key == "cards" ||
                        key == "my_tiles" ||
                        key == "player_tiles" ||
                        key.matches(Regex(".*hand.*", RegexOption.IGNORE_CASE)) ||
                        key.matches(Regex(".*tile.*", RegexOption.IGNORE_CASE))
                    
                    if (isHandTileField) {
                        when (value) {
                            is JsonArray -> {
                                val tiles = value.mapNotNull { jsonElement -> 
                                    when (jsonElement) {
                                        is JsonPrimitive -> jsonElement.contentOrNull
                                        is JsonObject -> {
                                            // 有些协议中牌可能是对象格式 {"type": 1, "value": 5}
                                            val type = jsonElement["type"]?.jsonPrimitive?.intOrNull
                                            val num = jsonElement["value"]?.jsonPrimitive?.intOrNull ?: 
                                                     jsonElement["number"]?.jsonPrimitive?.intOrNull
                                            if (type != null && num != null) {
                                                "${type}${num}" // 转换为字符串格式
                                            } else null
                                        }
                                        else -> null
                                    }
                                }
                                if (tiles.isNotEmpty()) {
                                    println("[Decoder] 发现手牌数据 ($key): ${tiles.joinToString(",")}")
                                    // 尝试从上下文推断玩家ID
                                    val playerId = inferPlayerId(element, key)
                                    events.add(HandTilesUpdated(
                                        playerId = playerId,
                                        tiles = tiles,
                                        fieldName = key,
                                        timestamp = System.currentTimeMillis()
                                    ))
                                }
                            }
                            is JsonPrimitive -> {
                                // 单个牌的情况
                                val tileStr = value.contentOrNull
                                if (tileStr != null) {
                                    println("[Decoder] 发现单张牌数据 ($key): $tileStr")
                                    val playerId = inferPlayerId(element, key)
                                    events.add(HandTilesUpdated(
                                        playerId = playerId,
                                        tiles = listOf(tileStr),
                                        fieldName = key,
                                        timestamp = System.currentTimeMillis()
                                    ))
                                }
                            }
                            is JsonObject -> {
                                // 对象格式的牌数据
                                val type = value["type"]?.jsonPrimitive?.intOrNull
                                val num = value["value"]?.jsonPrimitive?.intOrNull ?: 
                                         value["number"]?.jsonPrimitive?.intOrNull
                                if (type != null && num != null) {
                                    val tileStr = "${type}${num}"
                                    println("[Decoder] 发现对象格式牌数据 ($key): $tileStr")
                                    val playerId = inferPlayerId(element, key)
                                    events.add(HandTilesUpdated(
                                        playerId = playerId,
                                        tiles = listOf(tileStr),
                                        fieldName = key,
                                        timestamp = System.currentTimeMillis()
                                    ))
                                }
                            }
                            else -> {
                                // 其他类型不处理
                            }
                        }
                    }
                    
                    // 递归搜索
                    searchForHandTiles(value, events)
                }
            }
            is JsonArray -> {
                element.forEach { jsonElement -> searchForHandTiles(jsonElement, events) }
            }
            is JsonPrimitive, is JsonNull -> {
                // 基本类型和null不需要处理
            }
        }
    }

    /**
     * 从JSON上下文推断玩家ID
     */
    private fun inferPlayerId(parentObject: JsonObject, fieldName: String): Int {
        // 尝试从父对象中找到玩家ID相关字段
        val possibleIdFields = listOf("seat", "player_id", "playerId", "id", "index", "position")
        
        for (field in possibleIdFields) {
            val id = parentObject[field]?.jsonPrimitive?.intOrNull
            if (id != null && id in 0..3) { // 麻将通常是4人游戏
                return id
            }
        }
        
        // 如果字段名包含玩家信息，尝试提取
        val playerPattern = Regex("player(\\d+)", RegexOption.IGNORE_CASE)
        val match = playerPattern.find(fieldName)
        if (match != null) {
            return match.groupValues[1].toIntOrNull() ?: 0
        }
        
        // 默认返回玩家0（通常是自己）
        return 0
    }

    /**
     * 尝试从 Base64 或 Hex 字符串解析为字节（方便调试）
     */
    fun tryParseHexOrBase64(input: String): ByteArray? {
        val s = input.trim()
        // 先尝试 Base64
        try {
            val decoded = Base64.getDecoder().decode(s)
            if (decoded.isNotEmpty()) return decoded
        } catch (_: Exception) {
            // ignore
        }
        // 再尝试 Hex（去空格）
        val hex = s.replace(" ", "")
        val hexRegex = Regex("^[0-9a-fA-F]+$")
        if (hex.length % 2 == 0 && hexRegex.matches(hex)) {
            val out = ByteArray(hex.length / 2)
            var i = 0
            while (i < hex.length) {
                val b = hex.substring(i, i + 2)
                out[i / 2] = b.toInt(16).toByte()
                i += 2
            }
            return out
        }
        return null
    }
}