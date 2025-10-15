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
            
            val id = jsonObject["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val typeString = jsonObject["type"]?.jsonPrimitive?.content ?: "NOTIFY"
            val methodString = jsonObject["method"]?.jsonPrimitive?.content ?: ""
            
            val type = when (typeString) {
                "REQ" -> LiqiMessageType.REQ
                "RES" -> LiqiMessageType.RES
                "NOTIFY" -> LiqiMessageType.NOTIFY
                else -> LiqiMessageType.NOTIFY
            }
            
            val method = LiqiMethod.fromString(methodString) ?: LiqiMethod.OAUTH2_LOGIN
            
            val dataMap = jsonObject["data"]?.jsonObject?.let { dataObj ->
                dataObj.entries.associate { (key, value) ->
                    key to value.toString()
                }
            } ?: emptyMap()
            
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
            val mjaiMessage = when (liqiMessage.method) {
                LiqiMethod.AUTH_GAME -> MjaiMessage(
                    type = MjaiMessageType.START_GAME,
                    id = liqiMessage.id
                )
                LiqiMethod.ACTION_PROTOTYPE -> {
                    val operation = liqiMessage.data["operation"] as? String
                    when (operation) {
                        "discard" -> MjaiMessage(
                            type = MjaiMessageType.DAHAI,
                            actor = liqiMessage.data["actor"]?.toString()?.toIntOrNull(),
                            pai = liqiMessage.data["pai"] as? String
                        )
                        "chi" -> MjaiMessage(
                            type = MjaiMessageType.CHI,
                            actor = liqiMessage.data["actor"]?.toString()?.toIntOrNull(),
                            target = liqiMessage.data["target"]?.toString()?.toIntOrNull(),
                            pai = liqiMessage.data["pai"] as? String,
                            consumed = (liqiMessage.data["consumed"] as? List<*>)?.map { it.toString() }
                        )
                        "pon" -> MjaiMessage(
                            type = MjaiMessageType.PON,
                            actor = liqiMessage.data["actor"]?.toString()?.toIntOrNull(),
                            target = liqiMessage.data["target"]?.toString()?.toIntOrNull(),
                            pai = liqiMessage.data["pai"] as? String,
                            consumed = (liqiMessage.data["consumed"] as? List<*>)?.map { it.toString() }
                        )
                        "kan" -> MjaiMessage(
                            type = MjaiMessageType.KAN,
                            actor = liqiMessage.data["actor"]?.toString()?.toIntOrNull(),
                            pai = liqiMessage.data["pai"] as? String,
                            consumed = (liqiMessage.data["consumed"] as? List<*>)?.map { it.toString() }
                        )
                        "reach" -> MjaiMessage(
                            type = MjaiMessageType.REACH,
                            actor = liqiMessage.data["actor"]?.toString()?.toIntOrNull()
                        )
                        "agari" -> MjaiMessage(
                            type = MjaiMessageType.HORA,
                            actor = liqiMessage.data["actor"]?.toString()?.toIntOrNull(),
                            target = liqiMessage.data["target"]?.toString()?.toIntOrNull(),
                            pai = liqiMessage.data["pai"] as? String
                        )
                        else -> MjaiMessage(
                            type = MjaiMessageType.NONE,
                            actor = liqiMessage.data["actor"]?.toString()?.toIntOrNull()
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