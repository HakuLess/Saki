package mahjongcopilot.domain.service.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mahjongcopilot.data.model.*
import mahjongcopilot.domain.service.AiModelService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AI 模型管理服务实现
 */
class AiModelServiceImpl : AiModelService {
    
    private val _availableModels = MutableStateFlow<List<AiModelConfig>>(emptyList())
    private val availableModels: Flow<List<AiModelConfig>> = _availableModels.asStateFlow()
    
    private val _currentModel = MutableStateFlow<AiModelConfig?>(null)
    private val currentModel: Flow<AiModelConfig?> = _currentModel.asStateFlow()
    
    private val mutex = Mutex()
    private var isInitialized = false
    
    override suspend fun initializeModels(): Result<Unit> {
        return try {
            mutex.withLock {
                if (isInitialized) {
                    return Result.success(Unit)
                }
                
                // 初始化可用的 AI 模型
                val models = listOf(
                    AiModelConfig(
                        type = AiModelType.LOCAL,
                        name = "本地 Mortal 模型",
                        modelPath = "models/mortal_model.bin",
                        isEnabled = true,
                        supportedModes = listOf(GameMode.FOUR_PLAYER)
                    ),
                    AiModelConfig(
                        type = AiModelType.AKAGI_OT,
                        name = "Akagi 在线模型",
                        apiUrl = "https://api.akagi.com/mahjong",
                        isEnabled = true,
                        supportedModes = listOf(GameMode.FOUR_PLAYER)
                    ),
                    AiModelConfig(
                        type = AiModelType.MJAPI,
                        name = "MJAPI 服务",
                        apiUrl = "https://mjapi.com/v1",
                        apiKey = "your_api_key_here",
                        isEnabled = false,
                        supportedModes = listOf(GameMode.FOUR_PLAYER)
                    )
                )
                
                _availableModels.value = models
                isInitialized = true
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getAvailableModels(): List<AiModelConfig> {
        return mutex.withLock { _availableModels.value }
    }
    
    override suspend fun loadModel(modelConfig: AiModelConfig): Result<Unit> {
        return try {
            mutex.withLock {
                when (modelConfig.type) {
                    AiModelType.LOCAL -> {
                        // 模拟加载本地模型
                        delay(2000)
                        _currentModel.value = modelConfig
                        Result.success(Unit)
                    }
                    AiModelType.AKAGI_OT -> {
                        // 模拟连接在线模型
                        delay(1000)
                        _currentModel.value = modelConfig
                        Result.success(Unit)
                    }
                    AiModelType.MJAPI -> {
                        // 模拟连接 MJAPI 服务
                        delay(1500)
                        _currentModel.value = modelConfig
                        Result.success(Unit)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun unloadModel(): Result<Unit> {
        return try {
            mutex.withLock {
                _currentModel.value = null
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getCurrentModel(): AiModelConfig? {
        return mutex.withLock { _currentModel.value }
    }
    
    override suspend fun isModelLoaded(): Boolean {
        return mutex.withLock { _currentModel.value != null }
    }
    
    override suspend fun updateModelConfig(modelConfig: AiModelConfig): Result<Unit> {
        return try {
            mutex.withLock {
                val currentModels = _availableModels.value.toMutableList()
                val index = currentModels.indexOfFirst { it.name == modelConfig.name }
                
                if (index >= 0) {
                    currentModels[index] = modelConfig
                    _availableModels.value = currentModels
                    
                    // 如果当前模型被更新，也更新当前模型
                    if (_currentModel.value?.name == modelConfig.name) {
                        _currentModel.value = modelConfig
                    }
                }
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun testModelConnection(modelConfig: AiModelConfig): Result<Boolean> {
        return try {
            when (modelConfig.type) {
                AiModelType.LOCAL -> {
                    // 测试本地模型文件是否存在
                    delay(500)
                    Result.success(true) // 模拟测试成功
                }
                AiModelType.AKAGI_OT -> {
                    // 测试在线模型连接
                    delay(1000)
                    Result.success(true) // 模拟测试成功
                }
                AiModelType.MJAPI -> {
                    // 测试 MJAPI 服务连接
                    delay(800)
                    Result.success(true) // 模拟测试成功
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun observeAvailableModels(): Flow<List<AiModelConfig>> {
        return availableModels
    }
    
    override fun observeCurrentModel(): Flow<AiModelConfig?> {
        return currentModel
    }
}
