package mahjongcopilot.data.model

/**
 * Liqi消息类型枚举
 * 参考MahjongCopilot的liqi.py实现
 */
enum class LiqiMessageType {
    NOTIFY,    // 通知消息
    REQ,       // 请求消息  
    RES,       // 响应消息
    UNKNOWN    // 未知类型
}