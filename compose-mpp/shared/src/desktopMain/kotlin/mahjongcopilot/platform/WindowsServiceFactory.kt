package mahjongcopilot.platform

import mahjongcopilot.data.repository.GameStateRepositoryImpl
import mahjongcopilot.data.repository.ProtocolParserRepositoryImpl
import mahjongcopilot.domain.service.impl.*
import mahjongcopilot.domain.service.*

/**
 * Windows平台服务工厂
 * 用于创建Windows平台特定的服务实例
 */
object WindowsServiceFactory {
    
    /**
     * 创建Windows平台的网络管理服务
     */
    fun createNetworkManagerService(): NetworkManagerService {
        val networkInterceptor = MitmNetworkInterceptor()
        return NetworkManagerServiceImpl(networkInterceptor)
    }
    
    /**
     * 创建Windows平台的游戏管理服务
     */
    fun createGameManagerService(): GameManagerService {
        val networkInterceptor = MitmNetworkInterceptor()
        val protocolParser = ProtocolParserRepositoryImpl()
        val gameStateRepository = GameStateRepositoryImpl()
        return GameManagerServiceImpl(networkInterceptor, protocolParser, gameStateRepository)
    }
    
    /**
     * 创建Windows平台的自动化服务
     */
    fun createAutomationService(): AutomationService {
        return WindowsAutomationService()
    }
    
    /**
     * 创建Windows平台的AI决策服务
     */
    fun createAiDecisionService(): AiDecisionService {
        return AiDecisionServiceImpl()
    }
    
    /**
     * 创建Windows平台的AI模型服务
     */
    fun createAiModelService(): AiModelService {
        return AiModelServiceImpl()
    }
}