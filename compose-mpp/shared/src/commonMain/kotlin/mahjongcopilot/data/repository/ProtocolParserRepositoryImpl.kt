package mahjongcopilot.data.repository

import mahjongcopilot.data.model.*
import mahjongcopilot.domain.repository.ProtocolParserRepository
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.Serializable

/**
 * 协议解析器实现 - 基于MahjongCopilot的liqi.py逻辑
 */
class ProtocolParserRepositoryImpl : ProtocolParserRepository {
    
    // XOR解密密钥，来自MahjongCopilot的liqi.py
    private val xorKeys = byteArrayOf(0x84.toByte(), 0x5e.toByte(), 0x4e.toByte(), 0x42.toByte(), 0x39.toByte(), 0xa2.toByte(), 0x1f.toByte(), 0x60.toByte(), 0x1c.toByte())
    
    override suspend fun parseLiqiMessage(data: ByteArray): Result<LiqiMessage> {
        return try {
            // 检查数据长度
            if (data.isEmpty()) {
                return Result.failure(Exception("空数据"))
            }
            
            // 调试：打印原始数据的前几个字节
            println("原始数据前16字节: ${data.take(16).joinToString("") { "%02x".format(it) }}")
            
            // 雀魂消息格式参考MahjongCopilot的liqi.py：
            // 消息结构: [消息类型(1字节)][消息ID(2字节小端序)][Protobuf数据]
            val msgTypeByte = data[0].toInt() and 0xFF
            
            // 雀魂使用的消息类型：
            // 0x01: NOTIFICATION (通知)
            // 0x02: REQUEST (请求) 
            // 0x03: RESPONSE (响应)
            val msgType = when (msgTypeByte) {
                0x01 -> LiqiMessageType.NOTIFY
                0x02 -> LiqiMessageType.REQ
                0x03 -> LiqiMessageType.RES
                else -> {
                    println("未知消息类型: 0x${msgTypeByte.toString(16).padStart(2, '0')}, 数据长度: ${data.size}")
                    
                    // 尝试检查是否是WebSocket握手数据
                    if (data.size > 3 && data[0] == 0x7b.toByte() && data[1] == 0x22.toByte()) {
                        println("检测到可能是JSON格式的HTTP/WebSocket握手数据")
                    }
                    
                    // 返回未知类型消息，但包含详细调试信息
                    val dataMap = mutableMapOf<String, String>().apply {
                        put("method", "unknown_protocol")
                        put("rawData", data.toHexString())
                        put("messageType", "0x${msgTypeByte.toString(16).padStart(2, '0')}")
                        put("messageSize", data.size.toString())
                        put("firstBytes", data.take(10).joinToString("") { "%02x".format(it) })
                    }
                    
                    val message = LiqiMessage(
                        id = 0,
                        type = LiqiMessageType.UNKNOWN,
                        method = LiqiMethod.UNKNOWN,
                        data = dataMap
                    )
                    
                    return Result.success(message)
                }
            }
            
            // 解析消息ID（对于REQ/RES类型）
            var id = 0
            var offset = 1
            
            if (msgType == LiqiMessageType.REQ || msgType == LiqiMessageType.RES) {
                if (data.size < 3) {
                    return Result.failure(Exception("REQ/RES消息数据太短"))
                }
                // 小端序解析消息ID
                id = ((data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8))
                offset = 3
            }
            
            // 解析Protobuf数据
            val protobufData = if (offset < data.size) data.copyOfRange(offset, data.size) else byteArrayOf()
            println("Protobuf数据长度: ${protobufData.size}")
            
            if (protobufData.isEmpty()) {
                println("无Protobuf数据，创建基础消息")
                val dataMap = mutableMapOf<String, String>().apply {
                    put("method", "empty_protobuf")
                    put("messageType", msgType.name)
                    put("messageId", id.toString())
                }
                return Result.success(LiqiMessage(id, msgType, LiqiMethod.UNKNOWN, dataMap))
            }
            
            val parsedBlocks = parseProtobuf(protobufData)
            
            if (parsedBlocks.isEmpty()) {
                println("无法解析Protobuf数据，数据长度: ${protobufData.size}")
                println("Protobuf数据前16字节: ${protobufData.take(16).joinToString("") { "%02x".format(it) }}")
                
                // 返回包含原始数据的消息，而不是失败
                val dataMap = mutableMapOf<String, String>().apply {
                    put("method", "unparsable_protobuf")
                    put("rawData", data.toHexString())
                    put("messageType", msgType.name)
                    put("messageId", id.toString())
                    put("protobufSize", protobufData.size.toString())
                }
                return Result.success(LiqiMessage(id, msgType, LiqiMethod.UNKNOWN, dataMap))
            }
            
            println("解析出 ${parsedBlocks.size} 个Protobuf块")
            
            // 查找方法名（通常在第一个字符串块）
            var methodName = ""
            for ((index, block) in parsedBlocks.withIndex()) {
                println("块 $index: id=${block.id}, type=${block.type}, data长度=${block.data.size}")
                
                if (block.type == "string" && block.id == 1) {
                    try {
                        methodName = String(block.data, Charsets.UTF_8)
                        println("找到方法名: $methodName")
                        break
                    } catch (e: Exception) {
                        println("UTF-8解码失败: ${e.message}")
                    }
                }
            }
            
            if (methodName.isEmpty()) {
                println("未找到方法名，尝试其他块")
                // 尝试其他可能的块
                for (block in parsedBlocks) {
                    if (block.type == "string") {
                        try {
                            val potentialName = String(block.data, Charsets.UTF_8)
                            if (potentialName.isNotBlank()) {
                                methodName = potentialName
                                println("找到备选方法名: $methodName")
                                break
                            }
                        } catch (e: Exception) {
                            // 忽略解码错误
                        }
                    }
                }
            }
            
            if (methodName.isEmpty()) {
                println("最终未找到方法名，使用unknown")
                methodName = "unknown"
            }
            
            val method = fromString(methodName) ?: LiqiMethod.OAUTH2_LOGIN
            
            // 构建数据映射
            val dataMap = mutableMapOf<String, String>().apply {
                put("method", methodName)
                put("rawData", data.toHexString())
                put("protobufBlocks", parsedBlocks.size.toString())
                put("messageType", msgType.toString())
                put("messageId", id.toString())
            }
            
            val message = LiqiMessage(
                id = id,
                type = msgType,
                method = method,
                data = dataMap
            )
            
            println("解析消息成功: type=$msgType, id=$id, method=$methodName")
            Result.success(message)
        } catch (e: Exception) {
            println("解析消息失败: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 解析Protobuf格式数据
     */
    private fun parseProtobuf(data: ByteArray): List<ProtobufBlock> {
        val blocks = mutableListOf<ProtobufBlock>()
        var position = 0
        
        println("开始解析Protobuf数据，总长度: ${data.size}")
        
        while (position < data.size) {
            if (position >= data.size) break
            
            val blockInfo = data[position].toInt() and 0xFF
            val blockId = blockInfo shr 3
            val blockType = blockInfo and 0x07
            position++
            
            println("解析块: position=$position, blockInfo=0x${blockInfo.toString(16)}, id=$blockId, type=$blockType")
            
            when (blockType) {
                0 -> { // varint
                    var value = 0L
                    var shift = 0
                    var byte: Int
                    do {
                        if (position >= data.size) break
                        byte = data[position].toInt() and 0xFF
                        position++
                        value = value or ((byte and 0x7F).toLong() shl shift)
                        shift += 7
                    } while ((byte and 0x80) != 0)
                    
                    println("解析varint: value=$value")
                    blocks.add(ProtobufBlock(blockId, "varint", value.toString().toByteArray()))
                }
                2 -> { // string/bytes
                    var length = 0
                    var shift = 0
                    var byte: Int
                    do {
                        if (position >= data.size) break
                        byte = data[position].toInt() and 0xFF
                        position++
                        length = length or ((byte and 0x7F) shl shift)
                        shift += 7
                    } while ((byte and 0x80) != 0)
                    
                    println("解析字符串: length=$length, position=$position")
                    
                    if (position + length > data.size) {
                        println("字符串长度超出数据范围: position=$position, length=$length, dataSize=${data.size}")
                        break
                    }
                    
                    val blockData = data.copyOfRange(position, position + length)
                    position += length
                    
                    // 尝试解码为UTF-8字符串
                    try {
                        val stringContent = String(blockData, Charsets.UTF_8)
                        println("字符串内容: $stringContent")
                    } catch (e: Exception) {
                        println("UTF-8解码失败，使用原始字节数据")
                    }
                    
                    blocks.add(ProtobufBlock(blockId, "string", blockData))
                }
                1 -> { // 64-bit
                    println("解析64-bit类型，跳过8字节")
                    if (position + 8 <= data.size) {
                        position += 8
                    } else {
                        break
                    }
                }
                5 -> { // 32-bit
                    println("解析32-bit类型，跳过4字节")
                    if (position + 4 <= data.size) {
                        position += 4
                    } else {
                        break
                    }
                }
                else -> {
                    println("未知Protobuf类型: $blockType，跳过解析")
                    // 跳过未知类型，继续解析下一个块
                    break
                }
            }
        }
        
        println("Protobuf解析完成，共解析 ${blocks.size} 个块")
        return blocks
    }
    
    /**
     * 字节数组转十六进制字符串
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Protobuf数据块
     */
    private data class ProtobufBlock(
        val id: Int,
        val type: String,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as ProtobufBlock
            
            if (id != other.id) return false
            if (type != other.type) return false
            if (!data.contentEquals(other.data)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = id
            result = 31 * result + type.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
    
    override suspend fun convertToMjai(liqiMessage: LiqiMessage): Result<MjaiMessage> {
        return try {
            val mjaiMessage = when (liqiMessage.type) {
                LiqiMessageType.NOTIFY -> {
                    when (liqiMessage.method) {
                        LiqiMethod.ACTION_PROTOTYPE -> {
                            // 解析ActionPrototype消息
                            val actionName = liqiMessage.data["actionName"]?.toString()
                            val rawData = liqiMessage.data["rawData"]?.toString()
                            
                            when (actionName) {
                                ".lq.ActionDiscardTile" -> MjaiMessage(
                                    type = MjaiMessageType.DAHAI,
                                    actor = extractActorFromData(rawData),
                                    pai = extractTileFromData(rawData)
                                )
                                ".lq.ActionChi" -> MjaiMessage(
                                    type = MjaiMessageType.CHI,
                                    actor = extractActorFromData(rawData),
                                    target = extractTargetFromData(rawData),
                                    pai = extractTileFromData(rawData)
                                )
                                ".lq.ActionPon" -> MjaiMessage(
                                    type = MjaiMessageType.PON,
                                    actor = extractActorFromData(rawData),
                                    target = extractTargetFromData(rawData),
                                    pai = extractTileFromData(rawData)
                                )
                                ".lq.ActionKan" -> MjaiMessage(
                                    type = MjaiMessageType.KAN,
                                    actor = extractActorFromData(rawData),
                                    pai = extractTileFromData(rawData)
                                )
                                ".lq.ActionReach" -> MjaiMessage(
                                    type = MjaiMessageType.REACH,
                                    actor = extractActorFromData(rawData)
                                )
                                ".lq.ActionHora" -> MjaiMessage(
                                    type = MjaiMessageType.HORA,
                                    actor = extractActorFromData(rawData),
                                    target = extractTargetFromData(rawData),
                                    pai = extractTileFromData(rawData)
                                )
                                ".lq.NotifyGameStart" -> MjaiMessage(
                                    type = MjaiMessageType.START_GAME,
                                    id = liqiMessage.id
                                )
                                ".lq.NotifyGameEndResult" -> MjaiMessage(
                                    type = MjaiMessageType.END_GAME,
                                    id = liqiMessage.id
                                )
                                else -> MjaiMessage(
                                    type = MjaiMessageType.NONE,
                                    id = liqiMessage.id
                                )
                            }
                        }
                        else -> MjaiMessage(
                            type = MjaiMessageType.NONE,
                            id = liqiMessage.id
                        )
                    }
                }
                else -> MjaiMessage(
                    type = MjaiMessageType.NONE,
                    id = liqiMessage.id
                )
            }
            
            Result.success(mjaiMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun extractActorFromData(rawData: String?): Int? {
        // 从原始数据中提取玩家索引
        // 这里需要根据实际的Protobuf数据结构来解析
        return 0 // 暂时返回默认值
    }
    
    private fun extractTargetFromData(rawData: String?): Int? {
        // 从原始数据中提取目标玩家索引
        return null // 暂时返回null
    }
    
    private fun extractTileFromData(rawData: String?): String? {
        // 从原始数据中提取牌的信息
        return null // 暂时返回null
    }
    
    override suspend fun convertToGameOperation(mjaiAction: MjaiAction): Result<GameOperation> {
        return try {
            val operation = when (mjaiAction.type) {
                MjaiActionType.DAHAI -> GameOperation(
                    type = GameOperationType.DISCARD,
                    tile = mjaiAction.pai,
                    position = Pair(400, 300) // 模拟屏幕坐标
                )
                MjaiActionType.CHI -> GameOperation(
                    type = GameOperationType.CHI,
                    tile = mjaiAction.pai,
                    consumed = mjaiAction.consumed,
                    target = mjaiAction.target,
                    position = Pair(300, 250)
                )
                MjaiActionType.PON -> GameOperation(
                    type = GameOperationType.PON,
                    tile = mjaiAction.pai,
                    consumed = mjaiAction.consumed,
                    target = mjaiAction.target,
                    position = Pair(350, 250)
                )
                MjaiActionType.KAN -> GameOperation(
                    type = GameOperationType.KAN,
                    tile = mjaiAction.pai,
                    consumed = mjaiAction.consumed,
                    position = Pair(400, 200)
                )
                MjaiActionType.REACH -> GameOperation(
                    type = GameOperationType.REACH,
                    position = Pair(500, 300)
                )
                MjaiActionType.HORA -> GameOperation(
                    type = GameOperationType.AGARI,
                    position = Pair(450, 300)
                )
                else -> GameOperation(
                    type = GameOperationType.PASS,
                    position = Pair(400, 300)
                )
            }
            
            Result.success(operation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}