package mahjongcopilot.data.model

import kotlinx.serialization.Serializable

/**
 * Mjai消息类型枚举
 */
enum class MjaiMessageType {
    CONNECTED,    // 连接建立
    DISCONNECTED, // 连接断开
    DAHAI,        // 打牌
    CHI,          // 吃
    PON,          // 碰
    KAN,          // 杠
    REACH,        // 立直
    HORA,         // 和牌
    START_GAME,   // 游戏开始
    END_GAME,     // 游戏结束
    NONE          // 无操作
}

/**
 * Mjai消息数据类
 */
@Serializable
data class MjaiMessage(
    val type: MjaiMessageType,
    val id: Int = 0,
    val actor: Int? = null,
    val target: Int? = null,
    val pai: String? = null,
    val consumed: List<String>? = null
)