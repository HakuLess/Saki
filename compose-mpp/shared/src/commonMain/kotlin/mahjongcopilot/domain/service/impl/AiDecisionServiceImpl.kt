package mahjongcopilot.domain.service.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mahjongcopilot.data.model.*
import mahjongcopilot.domain.service.AiDecisionService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * AI 决策服务实现
 */
class AiDecisionServiceImpl : AiDecisionService {
    
    private val _decisionHistory = MutableStateFlow<List<AiDecision>>(emptyList())
    private val decisionHistory: Flow<List<AiDecision>> = _decisionHistory.asStateFlow()
    
    private val mutex = Mutex()
    private var isEnabled = false
    private var currentModel: AiModelConfig? = null
    
    override suspend fun getDecision(gameState: GameState): Result<AiDecision> {
        return try {
            mutex.withLock {
                if (!isEnabled) {
                    return Result.failure(Exception("AI decision service is not enabled"))
                }
                
                val decision = generateAiDecision(gameState)
                
                // 添加到决策历史
                val currentHistory = _decisionHistory.value
                _decisionHistory.value = currentHistory + decision
                
                Result.success(decision)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun validateDecision(decision: AiDecision, gameState: GameState): Result<Boolean> {
        return try {
            // 简单的决策验证逻辑
            val isValid = when (decision.action.type) {
                MjaiActionType.DAHAI -> {
                    // 验证打牌是否合法
                    val player = gameState.players.find { it.seat == gameState.currentPlayer }
                    player?.hand?.any { it.type.value == decision.action.pai } == true
                }
                MjaiActionType.CHI, MjaiActionType.PON, MjaiActionType.KAN -> {
                    // 验证吃碰杠是否合法
                    decision.action.consumed?.isNotEmpty() == true
                }
                MjaiActionType.REACH -> {
                    // 验证立直条件
                    val player = gameState.players.find { it.seat == gameState.currentPlayer }
                    player?.hand?.size == 13 && !player.isReach
                }
                MjaiActionType.HORA -> {
                    // 验证和牌条件
                    val player = gameState.players.find { it.seat == gameState.currentPlayer }
                    player?.hand?.size == 14
                }
                else -> true
            }
            
            Result.success(isValid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun loadModel(modelConfig: AiModelConfig): Result<Unit> {
        return try {
            mutex.withLock {
                currentModel = modelConfig
                isEnabled = true
                
                // 模拟模型加载
                delay(1000)
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun unloadModel(): Result<Unit> {
        return try {
            mutex.withLock {
                currentModel = null
                isEnabled = false
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun isModelLoaded(): Boolean {
        return mutex.withLock { isEnabled && currentModel != null }
    }
    
    override suspend fun getModelInfo(): AiModelConfig? {
        return mutex.withLock { currentModel }
    }
    
    override fun observeDecisionHistory(): Flow<List<AiDecision>> {
        return decisionHistory
    }
    
    override suspend fun clearDecisionHistory(): Result<Unit> {
        return try {
            mutex.withLock {
                _decisionHistory.value = emptyList()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 生成 AI 决策
     */
    private suspend fun generateAiDecision(gameState: GameState): AiDecision {
        val startTime = System.currentTimeMillis()
        
        // 模拟 AI 思考时间
        delay(Random.nextLong(100, 500))
        
        val player = gameState.players.find { it.seat == gameState.currentPlayer }
        val action = when {
            player == null -> MjaiAction(MjaiActionType.NONE)
            player.hand.size == 14 -> {
                // 手牌满14张，需要打牌
                val tileToDiscard = selectTileToDiscard(player.hand)
                MjaiAction(
                    type = MjaiActionType.DAHAI,
                    pai = tileToDiscard.type.value,
                    confidence = calculateDiscardConfidence(tileToDiscard, player.hand)
                )
            }
            player.hand.size == 13 -> {
                // 手牌13张，可以立直或打牌
                if (canReach(player.hand) && Random.nextBoolean()) {
                    MjaiAction(
                        type = MjaiActionType.REACH,
                        confidence = 0.8f
                    )
                } else {
                    val tileToDiscard = selectTileToDiscard(player.hand)
                    MjaiAction(
                        type = MjaiActionType.DAHAI,
                        pai = tileToDiscard.type.value,
                        confidence = calculateDiscardConfidence(tileToDiscard, player.hand)
                    )
                }
            }
            else -> {
                // 其他情况，暂时无操作
                MjaiAction(MjaiActionType.NONE)
            }
        }
        
        val processingTime = System.currentTimeMillis() - startTime
        
        return AiDecision(
            action = action,
            confidence = action.confidence,
            reasoning = generateReasoning(action, gameState),
            processingTime = processingTime
        )
    }
    
    /**
     * 选择要打出的牌
     */
    private fun selectTileToDiscard(hand: List<Tile>): Tile {
        // 简单的打牌策略：优先打出孤张
        val isolatedTiles = findIsolatedTiles(hand)
        return if (isolatedTiles.isNotEmpty()) {
            isolatedTiles.random()
        } else {
            hand.random()
        }
    }
    
    /**
     * 找到孤张
     */
    private fun findIsolatedTiles(hand: List<Tile>): List<Tile> {
        val tileCounts = hand.groupBy { it.type }.mapValues { it.value.size }
        
        return hand.filter { tile ->
            val count = tileCounts[tile.type] ?: 0
            count == 1
        }
    }
    
    /**
     * 计算打牌置信度
     */
    private fun calculateDiscardConfidence(tile: Tile, hand: List<Tile>): Float {
        val tileCounts = hand.groupBy { it.type }.mapValues { it.value.size }
        val count = tileCounts[tile.type] ?: 0
        
        return when (count) {
            1 -> 0.9f // 孤张，高置信度打出
            2 -> 0.7f // 对子，中等置信度
            3 -> 0.3f // 刻子，低置信度
            else -> 0.5f
        }
    }
    
    /**
     * 检查是否可以立直
     */
    private fun canReach(hand: List<Tile>): Boolean {
        // 简单的立直判断：手牌13张且听牌
        return hand.size == 13 && isTenpai(hand)
    }
    
    /**
     * 检查是否听牌
     */
    private fun isTenpai(hand: List<Tile>): Boolean {
        // 简化的听牌判断逻辑
        val tileCounts = hand.groupBy { it.type }.mapValues { it.value.size }
        val pairs = tileCounts.values.count { it >= 2 }
        val sets = tileCounts.values.count { it >= 3 }
        
        return pairs >= 1 && sets >= 3
    }
    
    /**
     * 生成决策理由
     */
    private fun generateReasoning(action: MjaiAction, gameState: GameState): String {
        return when (action.type) {
            MjaiActionType.DAHAI -> {
                "打出手牌中的${action.pai}，置信度：${String.format("%.1f", action.confidence * 100)}%"
            }
            MjaiActionType.REACH -> {
                "手牌已听牌，选择立直"
            }
            MjaiActionType.CHI -> {
                "选择吃牌，组成顺子"
            }
            MjaiActionType.PON -> {
                "选择碰牌，组成刻子"
            }
            MjaiActionType.KAN -> {
                "选择杠牌，增加番数"
            }
            MjaiActionType.HORA -> {
                "和牌！"
            }
            else -> {
                "暂无操作"
            }
        }
    }
}
