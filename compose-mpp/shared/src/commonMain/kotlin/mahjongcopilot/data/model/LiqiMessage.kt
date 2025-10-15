package mahjongcopilot.data.model

import kotlinx.serialization.Serializable

/**
 * Liqi消息数据类
 * 参考MahjongCopilot的liqi.py实现
 */
@Serializable
data class LiqiMessage(
    val id: Int,
    val type: LiqiMessageType,
    val method: LiqiMethod,
    val data: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis()
)