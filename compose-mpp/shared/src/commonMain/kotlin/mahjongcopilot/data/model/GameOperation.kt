package mahjongcopilot.data.model

import kotlinx.serialization.Serializable

/**
 * 游戏操作类型枚举
 */
enum class GameOperationType {
    DISCARD, // 打牌
    CHI,     // 吃
    PON,     // 碰
    KAN,     // 杠
    REACH,   // 立直
    AGARI,   // 和牌
    PASS     // 过
}

/**
 * 游戏操作数据类
 */
@Serializable
data class GameOperation(
    val type: GameOperationType,
    val tile: String? = null,
    val target: Int? = null,
    val consumed: List<String>? = null,
    val position: Pair<Int, Int>? = null
)