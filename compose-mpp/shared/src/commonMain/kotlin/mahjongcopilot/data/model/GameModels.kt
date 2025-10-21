package mahjongcopilot.data.model

import kotlinx.serialization.Serializable

/**
 * 麻将牌花色
 */
@Serializable
enum class Suit {
    MANZU, // 万子
    PINZU, // 筒子
    SOUZU, // 索子
    HONOUR // 字牌
}

/**
 * 风向
 */
@Serializable
enum class Wind {
    EAST, // 东家
    SOUTH, // 南家
    WEST, // 西家
    NORTH // 北家
}

/**
 * 副露类型
 */
@Serializable
enum class MeldType {
    CHII,  // 吃
    PON,   // 碰
    MIN_KAN, // 明杠
    AN_KAN,  // 暗杠
    KA_KAN   // 加杠
}

/**
 * 麻将牌
 */
@Serializable
data class Tile(
    val suit: Suit,
    val value: Int, // 1-9或字牌值
    val dora: Boolean = false,
    val red: Boolean = false // 赤宝牌
) {
    /**
     * 获取牌的唯一标识符
     */
    fun getId(): String = "${suit.name}_${value}_${if (red) "r" else "n"}"
    
    /**
     * 获取牌的显示名称
     */
    fun getDisplayName(): String {
        val suitChar = when (suit) {
            Suit.MANZU -> "万"
            Suit.PINZU -> "筒"
            Suit.SOUZU -> "索"
            Suit.HONOUR -> ""
        }
        
        val valueStr = when (suit) {
            Suit.HONOUR -> {
                when (value) {
                    1 -> "東"
                    2 -> "南"
                    3 -> "西"
                    4 -> "北"
                    5 -> "白"
                    6 -> "發"
                    7 -> "中"
                    else -> "字"
                }
            }
            else -> value.toString()
        }
        
        val redMark = if (red) "赤" else ""
        
        return "$redMark$valueStr$suitChar"
    }
}

/**
 * 副露（吃碰杠）
 */
@Serializable
data class Meld(
    val type: MeldType,
    val tiles: List<Tile>,
    val calledTile: Tile?,
    val calledFrom: Int // 被鸣的玩家索引（0-3）
)

/**
 * 游戏状态
 */
@Serializable
data class GameState(
    val round: Int,          // 局数
    val honba: Int,          // 本场数
    val deposit: Int,        // 场棒数
    val playerWind: Wind,    // 自风
    val roundWind: Wind,     // 场风
    val activePlayer: Int,   // 当前行动玩家索引
    val remainingTiles: Int, // 剩余牌数
    val scores: List<Int>,   // 各玩家分数
    val handTiles: List<Tile>, // 手牌
    val discardedTiles: List<List<Tile>>, // 各玩家的弃牌
    val openMelds: List<List<Meld>>, // 各玩家的副露
    val doras: List<Tile>,   // 宝牌指示牌
    val visibleTiles: Set<Tile> = emptySet(), // 可见的牌（用于AI分析）
    val gamePhase: GamePhase = GamePhase.PREPARATION, // 游戏阶段
    val lastAction: GameAction? = null // 最后一个动作
)

/**
 * 游戏阶段
 */
@Serializable
enum class GamePhase {
    PREPARATION, // 准备阶段
    DEALING,     // 发牌阶段
    PLAYING,     // 游戏进行中
    AGARI,       // 和牌
    RYUUKYOKU    // 流局
}

/**
 * 游戏动作类型
 */
@Serializable
enum class GameActionType {
    Tsumo,      // 摸牌
    Discard,    // 打牌
    Chi,        // 吃
    Pon,        // 碰
    Kan,        // 杠
    Riichi,     // 立直
    Agari,      // 和牌
    Nuki,       // 拔北
    Ryukyoku    // 流局
}

/**
 * 游戏动作
 */
@Serializable
data class GameAction(
    val type: GameActionType,
    val playerIndex: Int, // 执行动作的玩家索引
    val tile: Tile?,      // 相关的牌
    val targetPlayerIndex: Int? = null, // 动作目标玩家索引
    val meld: Meld? = null // 相关的副露
)

/**
 * 决策类型
 */
@Serializable
enum class DecisionType {
    DISCARD,    // 打牌
    CHI,        // 吃
    PON,        // 碰
    KAN,        // 杠
    RIICHI,     // 立直
    AGARI,      // 和牌
    PASS        // 跳过
}

/**
 * AI决策
 */
@Serializable
data class Decision(
    val type: DecisionType,
    val tile: Tile?, // 相关的牌
    val confidence: Double, // 决策置信度 (0-1)
    val explanation: String? = null // 决策解释
)

/**
 * 窗口信息
 */
@Serializable
data class WindowInfo(
    val title: String,
    val processName: String,
    val windowHandle: Long,
    val position: Position,
    val size: Size
)

/**
 * 位置
 */
@Serializable
data class Position(val x: Int, val y: Int)

/**
 * 尺寸
 */
@Serializable
data class Size(val width: Int, val height: Int)