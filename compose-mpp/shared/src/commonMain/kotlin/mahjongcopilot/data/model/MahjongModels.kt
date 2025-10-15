package mahjongcopilot.data.model

/**
 * 麻将牌类型
 */
enum class TileType(val value: String) {
    // 万子 (Manzu)
    MAN_1("1m"), MAN_2("2m"), MAN_3("3m"), MAN_4("4m"), MAN_5("5m"),
    MAN_6("6m"), MAN_7("7m"), MAN_8("8m"), MAN_9("9m"),
    
    // 筒子 (Pinzu)
    PIN_1("1p"), PIN_2("2p"), PIN_3("3p"), PIN_4("4p"), PIN_5("5p"),
    PIN_6("6p"), PIN_7("7p"), PIN_8("8p"), PIN_9("9p"),
    
    // 索子 (Souzu)
    SOU_1("1s"), SOU_2("2s"), SOU_3("3s"), SOU_4("4s"), SOU_5("5s"),
    SOU_6("6s"), SOU_7("7s"), SOU_8("8s"), SOU_9("9s"),
    
    // 风牌 (Kazehai)
    EAST("E"), SOUTH("S"), WEST("W"), NORTH("N"),
    
    // 三元牌 (Sangenpai)
    WHITE("P"), GREEN("F"), RED("C"),
    
    // 红宝牌 (Akadora)
    MAN_5_RED("5mr"), PIN_5_RED("5pr"), SOU_5_RED("5sr"),
    
    // 未知牌
    UNKNOWN("?");
    
    companion object {
        fun fromString(value: String): TileType? {
            return values().find { it.value == value }
        }
    }
}

/**
 * 麻将牌
 */
data class Tile(
    val type: TileType,
    val isRed: Boolean = false
) {
    val displayName: String
        get() = when (type) {
            TileType.MAN_1 -> "一万"
            TileType.MAN_2 -> "二万"
            TileType.MAN_3 -> "三万"
            TileType.MAN_4 -> "四万"
            TileType.MAN_5 -> if (isRed) "五万(红)" else "五万"
            TileType.MAN_6 -> "六万"
            TileType.MAN_7 -> "七万"
            TileType.MAN_8 -> "八万"
            TileType.MAN_9 -> "九万"
            
            TileType.PIN_1 -> "一筒"
            TileType.PIN_2 -> "二筒"
            TileType.PIN_3 -> "三筒"
            TileType.PIN_4 -> "四筒"
            TileType.PIN_5 -> if (isRed) "五筒(红)" else "五筒"
            TileType.PIN_6 -> "六筒"
            TileType.PIN_7 -> "七筒"
            TileType.PIN_8 -> "八筒"
            TileType.PIN_9 -> "九筒"
            
            TileType.SOU_1 -> "一索"
            TileType.SOU_2 -> "二索"
            TileType.SOU_3 -> "三索"
            TileType.SOU_4 -> "四索"
            TileType.SOU_5 -> if (isRed) "五索(红)" else "五索"
            TileType.SOU_6 -> "六索"
            TileType.SOU_7 -> "七索"
            TileType.SOU_8 -> "八索"
            TileType.SOU_9 -> "九索"
            
            TileType.EAST -> "东"
            TileType.SOUTH -> "南"
            TileType.WEST -> "西"
            TileType.NORTH -> "北"
            
            TileType.WHITE -> "白"
            TileType.GREEN -> "发"
            TileType.RED -> "中"
            
            TileType.MAN_5_RED -> "五万(红)"
            TileType.PIN_5_RED -> "五筒(红)"
            TileType.SOU_5_RED -> "五索(红)"
            
            TileType.UNKNOWN -> "?"
        }
}

/**
 * 游戏模式
 */
enum class GameMode {
    FOUR_PLAYER,  // 四人麻将
    THREE_PLAYER  // 三人麻将
}

/**
 * 玩家座位
 */
enum class PlayerSeat(val index: Int) {
    EAST(0),   // 东家
    SOUTH(1),  // 南家
    WEST(2),    // 西家
    NORTH(3);  // 北家
    
    companion object {
        fun fromIndex(index: Int): PlayerSeat? {
            return values().find { it.index == index }
        }
    }
}

/**
 * 游戏状态
 */
data class GameState(
    val gameId: String,
    val mode: GameMode,
    val currentPlayer: PlayerSeat,
    val players: List<Player>,
    val currentKyoku: Int,
    val currentHonba: Int,
    val currentBakaze: TileType,
    val doraMarkers: List<Tile>,
    val scores: List<Int>,
    val isGameActive: Boolean = true
)

/**
 * 玩家信息
 */
data class Player(
    val seat: PlayerSeat,
    val name: String,
    val hand: List<Tile>,
    val melds: List<Meld>,
    val isReach: Boolean = false,
    val isDealer: Boolean = false
)

/**
 * 面子（吃、碰、杠）
 */
data class Meld(
    val type: MeldType,
    val tiles: List<Tile>,
    val fromPlayer: PlayerSeat? = null
)

/**
 * 面子类型
 */
enum class MeldType {
    CHI,      // 吃
    PON,      // 碰
    KAN,      // 杠
    ANKAN,    // 暗杠
    KAKAN,    // 加杠
    DAIMINKAN // 大明杠
}
