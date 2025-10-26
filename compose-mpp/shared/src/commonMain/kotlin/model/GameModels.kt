package model

/**
 * 麻将牌类型
 */
enum class TileType {
    MAN,    // 万子
    PIN,    // 筒子
    SOU,    // 索子
    HONOR   // 字牌（东南西北白发中）
}

/**
 * 麻将牌
 */
data class Tile(
    val type: TileType,
    val number: Int, // 1-9 for MAN/PIN/SOU, 1-7 for HONOR (东南西北白发中)
    val isRed: Boolean = false // 红宝牌标记
) {
    override fun toString(): String {
        return when (type) {
            TileType.MAN -> "${number}m"
            TileType.PIN -> "${number}p"
            TileType.SOU -> "${number}s"
            TileType.HONOR -> when (number) {
                1 -> "东"
                2 -> "南"
                3 -> "西"
                4 -> "北"
                5 -> "白"
                6 -> "发"
                7 -> "中"
                else -> "?"
            }
        }
    }
}

/**
 * 玩家信息
 */
data class Player(
    val id: Int,
    val name: String,
    val score: Int,
    val wind: Wind, // 风位
    val handTiles: List<Tile> = emptyList(),
    val discardedTiles: List<Tile> = emptyList(),
    val melds: List<Meld> = emptyList() // 副露
)

/**
 * 风位
 */
enum class Wind {
    EAST, SOUTH, WEST, NORTH
}

/**
 * 副露类型
 */
enum class MeldType {
    CHI,    // 吃
    PON,    // 碰
    KAN     // 杠
}

/**
 * 副露
 */
data class Meld(
    val type: MeldType,
    val tiles: List<Tile>,
    val fromPlayer: Int? = null // 来自哪个玩家（自摸杠为null）
)

/**
 * 游戏阶段
 */
enum class GameRound {
    EAST_1, EAST_2, EAST_3, EAST_4,
    SOUTH_1, SOUTH_2, SOUTH_3, SOUTH_4
}

/**
 * 游戏状态
 */
data class GameState(
    val isInGame: Boolean = false,
    val round: GameRound = GameRound.EAST_1,
    val currentPlayer: Int = 0, // 当前出牌玩家ID
    val players: List<Player> = emptyList(),
    val remainingTiles: Int = 70, // 剩余牌数
    val doraIndicators: List<Tile> = emptyList(), // 宝牌指示牌
    val lastDiscardedTile: Tile? = null,
    val canChi: Boolean = false,
    val canPon: Boolean = false,
    val canKan: Boolean = false,
    val canRon: Boolean = false,
    val canTsumo: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 游戏动作类型
 */
enum class GameAction {
    DISCARD,    // 打牌
    CHI,        // 吃
    PON,        // 碰
    KAN,        // 杠
    RON,        // 荣和
    TSUMO,      // 自摸
    RIICHI,     // 立直
    PASS        // 跳过
}