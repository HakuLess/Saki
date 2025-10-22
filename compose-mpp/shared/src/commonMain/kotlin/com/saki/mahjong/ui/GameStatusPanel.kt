package com.saki.mahjong.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saki.mahjong.data.GameState
import com.saki.mahjong.data.Tile

/**
 * 游戏状态面板组件
 * 展示游戏状态和手牌信息
 */
@Composable
fun GameStatusPanel(gameState: GameState) {
    Column(modifier = Modifier.padding(16.dp)) {
        // 游戏基本信息
        GameInfoHeader(gameState)
        
        // 玩家信息
        PlayersInfoRow(gameState)
        
        // 手牌展示
        HandTilesDisplay(gameState.handTiles, gameState.drawnTile)
        
        // 宝牌信息
        DoraDisplay(gameState.doraIndicators)
    }
}

/**
 * 游戏基本信息头部
 */
@Composable
fun GameInfoHeader(gameState: GameState) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        border = BorderStroke(1.dp, Color.Gray)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "对局状态: ${gameState.status}", fontSize = 16.sp)
            Text(text = "${gameState.round}局 ${gameState.honba}本场 ${gameState.kyotaku}供托", fontSize = 16.sp)
            Text(text = "当前回合: ${gameState.currentPlayerIndex + 1}位玩家", fontSize = 16.sp)
        }
    }
}

/**
 * 玩家信息行
 */
@Composable
fun PlayersInfoRow(gameState: GameState) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        border = BorderStroke(1.dp, Color.Gray)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "玩家信息", fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                gameState.players.forEach { player ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = player.name,
                            fontSize = 14.sp,
                            color = if (player.isDealer) Color.Red else Color.Black
                        )
                        Text(text = "${player.score}点", fontSize = 14.sp)
                        if (player.isRiichi) {
                            Text(text = "立直", fontSize = 12.sp, color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 手牌展示组件
 */
@Composable
fun HandTilesDisplay(handTiles: List<Tile>, drawnTile: Tile?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        border = BorderStroke(1.dp, Color.Gray)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "手牌", fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            
            // 排序手牌
            val sortedTiles = handTiles.sortedWith(compareBy({
                when (it.value.type) {
                    com.saki.mahjong.data.TileType.MANZU -> 0
                    com.saki.mahjong.data.TileType.PINZU -> 1
                    com.saki.mahjong.data.TileType.SOUZU -> 2
                    com.saki.mahjong.data.TileType.JIHAI -> 3
                }
            }, { it.value.value }))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                sortedTiles.forEach { tile ->
                    TileDisplay(tile = tile)
                }
                
                // 刚摸到的牌
                if (drawnTile != null) {
                    Text(text = "+", fontSize = 24.sp, modifier = Modifier.padding(4.dp))
                    TileDisplay(tile = drawnTile, isDrawn = true)
                }
            }
        }
    }
}

/**
 * 单张牌展示
 */
@Composable
fun TileDisplay(tile: Tile, isDrawn: Boolean = false) {
    Card(
        modifier = Modifier
            .size(48.dp)
            .padding(4.dp),
        backgroundColor = if (isDrawn) Color.Yellow else Color.White,
        border = BorderStroke(1.dp, Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = tile.toString(),
                fontSize = 12.sp,
                color = if (tile.isRed) Color.Red else Color.Black
            )
        }
    }
}

/**
 * 宝牌展示
 */
@Composable
fun DoraDisplay(doraIndicators: List<Tile>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Color.Gray)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "宝牌指示", fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                doraIndicators.forEach { tile ->
                    Card(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(4.dp),
                        backgroundColor = Color.Cyan,
                        border = BorderStroke(1.dp, Color.Black)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = tile.toString(),
                                fontSize = 12.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}