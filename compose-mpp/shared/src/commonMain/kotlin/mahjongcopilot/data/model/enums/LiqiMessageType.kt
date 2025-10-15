package mahjongcopilot.data.model.enums

/**
 * Liqi协议消息类型枚举
 */
enum class LiqiMessageType {
    AUTH_GAME,         // 游戏认证
    ACTION_PROTOTYPE,  // 操作原型
    GAME_END,          // 游戏结束
    UNKNOWN            // 未知类型
}