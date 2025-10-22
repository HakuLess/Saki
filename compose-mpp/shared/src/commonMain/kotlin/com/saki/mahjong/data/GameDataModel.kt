package com.saki.mahjong.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 雀魂游戏状态数据模型
 */

// 游戏状态
enum class GameStatus {
    NOT_STARTED,
    IN_PROGRESS,
    ROUND_END,
    GAME_END
}

// 麻将牌类型
enum class TileType {
    MANZU, // 万子
    PINZU, // 饼子
    SOUZU, // 索子
    JIHAI // 字牌
}

// 麻将牌值
enum class TileValue(val value: Int, val type: TileType) {
    // 万子
    MAN_1(1, TileType.MANZU),
    MAN_2(2, TileType.MANZU),
    MAN_3(3, TileType.MANZU),
    MAN_4(4, TileType.MANZU),
    MAN_5(5, TileType.MANZU),
    MAN_6(6, TileType.MANZU),
    MAN_7(7, TileType.MANZU),
    MAN_8(8, TileType.MANZU),
    MAN_9(9, TileType.MANZU),
    
    // 饼子
    PIN_1(1, TileType.PINZU),
    PIN_2(2, TileType.PINZU),
    PIN_3(3, TileType.PINZU),
    PIN_4(4, TileType.PINZU),
    PIN_5(5, TileType.PINZU),
    PIN_6(6, TileType.PINZU),
    PIN_7(7, TileType.PINZU),
    PIN_8(8, TileType.PINZU),
    PIN_9(9, TileType.PINZU),
    
    // 索子
    SOU_1(1, TileType.SOUZU),
    SOU_2(2, TileType.SOUZU),
    SOU_3(3, TileType.SOUZU),
    SOU_4(4, TileType.SOUZU),
    SOU_5(5, TileType.SOUZU),
    SOU_6(6, TileType.SOUZU),
    SOU_7(7, TileType.SOUZU),
    SOU_8(8, TileType.SOUZU),
    SOU_9(9, TileType.SOUZU),
    
    // 字牌
    EAST(1, TileType.JIHAI),
    SOUTH(2, TileType.JIHAI),
    WEST(3, TileType.JIHAI),
    NORTH(4, TileType.JIHAI),
    WHITE(5, TileType.JIHAI),
    GREEN(6, TileType.JIHAI),
    RED(7, TileType.JIHAI)
}

// 麻将牌数据类
@Serializable
class Tile(val value: TileValue, val isRed: Boolean = false) {
    override fun toString(): String {
        val prefix = when (value.type) {
            TileType.MANZU -> "万"
            TileType.PINZU -> "饼"
            TileType.SOUZU -> "索"
            TileType.JIHAI -> when (value) {
                TileValue.EAST -> "东"
                TileValue.SOUTH -> "南"
                TileValue.WEST -> "西"
                TileValue.NORTH -> "北"
                TileValue.WHITE -> "白"
                TileValue.GREEN -> "发"
                TileValue.RED -> "中"
                else -> ""
            }
        }
        val valueStr = if (value.type != TileType.JIHAI) value.value.toString() else ""
        val redMark = if (isRed && value.type != TileType.JIHAI) "赤" else ""
        return "${redMark}${valueStr}${prefix}"
    }
}

// 玩家信息
@Serializable
class PlayerInfo(
    @SerialName("name") val name: String,
    @SerialName("score") val score: Int,
    @SerialName("position") val position: Int, // 0: 东家, 1: 南家, 2: 西家, 3: 北家
    @SerialName("is_dealer") val isDealer: Boolean = false,
    @SerialName("is_riichi") val isRiichi: Boolean = false,
    @SerialName("discards") val discards: List<Tile> = emptyList()
)

// 游戏状态数据
@Serializable
class GameState(
    @SerialName("status") val status: GameStatus = GameStatus.NOT_STARTED,
    @SerialName("round") val round: Int = 0, // 局数
    @SerialName("honba") val honba: Int = 0, // 本场
    @SerialName("kyotaku") val kyotaku: Int = 0, // 供托
    @SerialName("current_player") val currentPlayerIndex: Int = 0,
    @SerialName("players") val players: List<PlayerInfo> = emptyList(),
    @SerialName("hand_tiles") val handTiles: List<Tile> = emptyList(), // 当前玩家手牌
    @SerialName("drawn_tile") val drawnTile: Tile? = null, // 刚摸到的牌
    @SerialName("river") val river: List<List<Tile>> = emptyList(), // 各家打出的牌
    @SerialName("dora_indicators") val doraIndicators: List<Tile> = emptyList(), // 宝牌指示牌
    @SerialName("uradora_indicators") val uradoraIndicators: List<Tile> = emptyList() // 里宝牌指示牌
)