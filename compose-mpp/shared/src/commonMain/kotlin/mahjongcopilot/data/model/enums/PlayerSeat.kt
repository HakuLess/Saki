package mahjongcopilot.data.model.enums

/**
 * 玩家座位枚举
 */
enum class PlayerSeat(val index: Int) {
    EAST(0),
    SOUTH(1),
    WEST(2),
    NORTH(3);
    
    companion object {
        fun fromIndex(index: Int): PlayerSeat? {
            return values().find { it.index == index }
        }
    }
}