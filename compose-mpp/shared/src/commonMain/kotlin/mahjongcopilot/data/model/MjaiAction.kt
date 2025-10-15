package mahjongcopilot.data.model

import kotlinx.serialization.Serializable

/**
 * Mjai动作类型枚举
 */
enum class MjaiActionType {
    DAHAI,  // 打牌
    CHI,    // 吃
    PON,    // 碰
    KAN,    // 杠
    REACH,  // 立直
    HORA,   // 和牌
    NONE    // 无操作
}

/**
 * Mjai动作数据类
 */
@Serializable
data class MjaiAction(
    val type: MjaiActionType,
    val pai: String? = null,
    val target: Int? = null,
    val consumed: List<String>? = null,
    val confidence: Float = 0.5f
)