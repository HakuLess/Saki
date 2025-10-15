package mahjongcopilot.data.model.enums

/**
 * 麻将牌类型枚举
 */
enum class TileType(val value: String) {
    // 万子
    M1("1m"), M2("2m"), M3("3m"), M4("4m"), M5("5m"), M6("6m"), M7("7m"), M8("8m"), M9("9m"),
    
    // 筒子
    P1("1p"), P2("2p"), P3("3p"), P4("4p"), P5("5p"), P6("6p"), P7("7p"), P8("8p"), P9("9p"),
    
    // 索子
    S1("1s"), S2("2s"), S3("3s"), S4("4s"), S5("5s"), S6("6s"), S7("7s"), S8("8s"), S9("9s"),
    
    // 风牌
    EAST("E"), SOUTH("S"), WEST("W"), NORTH("N"),
    
    // 三元牌
    WHITE("P"), GREEN("F"), RED("C");
    
    companion object {
        /**
         * 从字符串解析牌类型
         */
        fun fromString(str: String): TileType? {
            return when (str) {
                "1m" -> M1
                "2m" -> M2
                "3m" -> M3
                "4m" -> M4
                "5m" -> M5
                "6m" -> M6
                "7m" -> M7
                "8m" -> M8
                "9m" -> M9
                "1p" -> P1
                "2p" -> P2
                "3p" -> P3
                "4p" -> P4
                "5p" -> P5
                "6p" -> P6
                "7p" -> P7
                "8p" -> P8
                "9p" -> P9
                "1s" -> S1
                "2s" -> S2
                "3s" -> S3
                "4s" -> S4
                "5s" -> S5
                "6s" -> S6
                "7s" -> S7
                "8s" -> S8
                "9s" -> S9
                "E" -> EAST
                "S" -> SOUTH
                "W" -> WEST
                "N" -> NORTH
                "P" -> WHITE
                "F" -> GREEN
                "C" -> RED
                else -> null
            }
        }
    }
}