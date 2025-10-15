package mahjongcopilot.data.repository

import mahjongcopilot.data.model.*
import mahjongcopilot.domain.repository.ProtocolParserRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 协议解析器实现
 */
class ProtocolParserRepositoryImpl : ProtocolParserRepository {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override suspend fun parseLiqiMessage(data: ByteArray): Result<LiqiMessage> {
        return try {
            val jsonString = data.decodeToString()
            val jsonObject = json.parseToJsonElement(jsonString).jsonObject
            
            // 解析新的消息格式
            val typeString = jsonObject["type"]?.jsonPrimitive?.content ?: "notify"
            val id = jsonObject["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val methodString = jsonObject["method"]?.jsonPrimitive?.content ?: ""
            val fromClient = jsonObject["fromClient"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val host = jsonObject["host"]?.jsonPrimitive?.content ?: ""
            val timestamp = jsonObject["timestamp"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            
            val type = when (typeString) {
                "req" -> LiqiMessageType.REQ
                "res" -> LiqiMessageType.RES
                "notify" -> LiqiMessageType.NOTIFY
                "websocket_start" -> LiqiMessageType.WEBSOCKET_START
                "websocket_end" -> LiqiMessageType.WEBSOCKET_END
                else -> LiqiMessageType.NOTIFY
            }
            
            val method = LiqiMethod.fromString(methodString) ?: LiqiMethod.OAUTH2_LOGIN
            
            // 构建数据映射
            val dataMap = mutableMapOf<String, String>().apply {
                put("fromClient", fromClient.toString())
                put("host", host)
                put("timestamp", timestamp.toString())
                
                // 添加原始数据
                jsonObject["data"]?.jsonPrimitive?.content?.let { put("rawData", it) }
                jsonObject["actionName"]?.jsonPrimitive?.content?.let { put("actionName", it) }
                jsonObject["actionData"]?.jsonPrimitive?.content?.let { put("actionData", it) }
            }
            
            val message = LiqiMessage(
                id = id,
                type = type,
                method = method,
                data = dataMap
            )
            
            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun convertToMjai(liqiMessage: LiqiMessage): Result<MjaiMessage> {
        return try {
            val mjaiMessage = when (liqiMessage.type) {
                LiqiMessageType.WEBSOCKET_START -> MjaiMessage(
                    type = MjaiMessageType.CONNECTED,
                    id = liqiMessage.id
                )
                LiqiMessageType.WEBSOCKET_END -> MjaiMessage(
                    type = MjaiMessageType.DISCONNECTED,
                    id = liqiMessage.id
                )
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