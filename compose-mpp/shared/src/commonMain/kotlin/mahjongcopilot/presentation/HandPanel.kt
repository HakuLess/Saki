package mahjongcopilot.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mahjongcopilot.data.model.*

/**
 * 手牌展示面板
 */
@Composable
fun HandPanel(gameState: GameState?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "手牌",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (gameState != null) {
                // 获取当前玩家的手牌
                val currentPlayer = gameState.players.find { it.seat == gameState.currentPlayer }
                
                if (currentPlayer != null) {
                    // 显示玩家信息
                    Text(
                        text = "当前玩家: ${currentPlayer.name} (${currentPlayer.seat.name})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    // 显示手牌
                    if (currentPlayer.hand.isNotEmpty()) {
                        Text(
                            text = "手牌 (${currentPlayer.hand.size}张):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // 按类型排序手牌
                        val sortedHand = currentPlayer.hand.sortedBy { 
                            when (it.type) {
                                // 万子
                                TileType.MAN_1, TileType.MAN_2, TileType.MAN_3, TileType.MAN_4, TileType.MAN_5,
                                TileType.MAN_6, TileType.MAN_7, TileType.MAN_8, TileType.MAN_9 -> 0
                                // 筒子
                                TileType.PIN_1, TileType.PIN_2, TileType.PIN_3, TileType.PIN_4, TileType.PIN_5,
                                TileType.PIN_6, TileType.PIN_7, TileType.PIN_8, TileType.PIN_9 -> 10
                                // 索子
                                TileType.SOU_1, TileType.SOU_2, TileType.SOU_3, TileType.SOU_4, TileType.SOU_5,
                                TileType.SOU_6, TileType.SOU_7, TileType.SOU_8, TileType.SOU_9 -> 20
                                // 风牌
                                TileType.EAST, TileType.SOUTH, TileType.WEST, TileType.NORTH -> 30
                                // 三元牌
                                TileType.WHITE, TileType.GREEN, TileType.RED -> 40
                                // 红宝牌
                                TileType.MAN_5_RED, TileType.PIN_5_RED, TileType.SOU_5_RED -> 50
                                else -> 100
                            }
                        }
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(sortedHand) { tile ->
                                TileDisplay(tile = tile)
                            }
                        }
                    } else {
                        Text(
                            text = "无手牌",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // 显示面子（吃、碰、杠）
                    if (currentPlayer.melds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "面子:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        currentPlayer.melds.forEach { meld ->
                            MeldDisplay(meld = meld)
                        }
                    }
                } else {
                    Text(
                        text = "找不到当前玩家",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "暂无游戏状态",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 单张麻将牌显示组件
 */
@Composable
fun TileDisplay(tile: Tile) {
    val backgroundColor = when (tile.type) {
        // 万子 - 浅红色
        TileType.MAN_1, TileType.MAN_2, TileType.MAN_3, TileType.MAN_4, TileType.MAN_5,
        TileType.MAN_6, TileType.MAN_7, TileType.MAN_8, TileType.MAN_9 -> Color(0xFFFFE0E0)
        // 筒子 - 浅蓝色
        TileType.PIN_1, TileType.PIN_2, TileType.PIN_3, TileType.PIN_4, TileType.PIN_5,
        TileType.PIN_6, TileType.PIN_7, TileType.PIN_8, TileType.PIN_9 -> Color(0xFFE0F0FF)
        // 索子 - 浅绿色
        TileType.SOU_1, TileType.SOU_2, TileType.SOU_3, TileType.SOU_4, TileType.SOU_5,
        TileType.SOU_6, TileType.SOU_7, TileType.SOU_8, TileType.SOU_9 -> Color(0xFFE0FFE0)
        // 风牌 - 浅黄色
        TileType.EAST, TileType.SOUTH, TileType.WEST, TileType.NORTH -> Color(0xFFFFF8E0)
        // 三元牌 - 浅紫色
        TileType.WHITE, TileType.GREEN, TileType.RED -> Color(0xFFF0E0FF)
        // 红宝牌 - 红色
        TileType.MAN_5_RED, TileType.PIN_5_RED, TileType.SOU_5_RED -> Color(0xFFFFD0D0)
        else -> Color.LightGray
    }
    
    val borderColor = if (tile.isRed) Color.Red else MaterialTheme.colorScheme.outline
    
    Card(
        modifier = Modifier
            .width(50.dp)
            .height(70.dp)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tile.displayName,
                fontSize = 10.sp,
                fontWeight = if (tile.isRed) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = if (tile.isRed) Color.Red else Color.Black
            )
        }
    }
}

/**
 * 面子（吃、碰、杠）显示组件
 */
@Composable
fun MeldDisplay(meld: Meld) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${meld.type.name}: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(50.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(meld.tiles) { tile ->
                TileDisplay(tile = tile)
            }
        }
    }
}