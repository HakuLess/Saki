package decoder

import model.GameEvent
import java.util.Base64

/**
 * 雀魂（MahjongSoul）消息解码器骨架：
 * - 后续使用 protobuf/自定义协议进行解析
 * - 目前返回空列表，仅提供输入管道与便捷解析工具
 */
object MajsoulDecoder {

    /**
     * 输入原始帧字节，返回解析出的麻将事件（占位实现）。
     */
    fun decodeFrame(frame: ByteArray): List<GameEvent> {
        // TODO: 解析 MahjongSoul 的 protobuf / JSON 封包
        return emptyList()
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