package mahjongcopilot.domain.service.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mahjongcopilot.data.model.*
import mahjongcopilot.domain.repository.NetworkInterceptorRepository
import mahjongcopilot.domain.service.NetworkManagerService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 网络管理服务实现
 */
class NetworkManagerServiceImpl(
    private val networkInterceptor: NetworkInterceptorRepository
) : NetworkManagerService {
    
    private val _networkStatus = MutableStateFlow(NetworkStatus.DISCONNECTED)
    private val networkStatus: Flow<NetworkStatus> = _networkStatus.asStateFlow()
    
    private val mutex = Mutex()
    private var isConnected = false
    private var statistics = NetworkStatistics()
    
    override suspend fun startNetworkInterception(): Result<Unit> {
        return try {
            mutex.withLock {
                if (isConnected) {
                    return Result.success(Unit)
                }
                
                _networkStatus.value = NetworkStatus.CONNECTING
                
                val result = networkInterceptor.startInterception(
                    NetworkSettings()
                )
                
                if (result.isSuccess) {
                    isConnected = true
                    _networkStatus.value = NetworkStatus.CONNECTED
                    statistics = statistics.copy(connectionUptime = System.currentTimeMillis())
                } else {
                    _networkStatus.value = NetworkStatus.ERROR
                }
                
                result
            }
        } catch (e: Exception) {
            _networkStatus.value = NetworkStatus.ERROR
            Result.failure(e)
        }
    }
    
    override suspend fun stopNetworkInterception(): Result<Unit> {
        return try {
            mutex.withLock {
                if (!isConnected) {
                    return Result.success(Unit)
                }
                
                val result = networkInterceptor.stopInterception()
                
                if (result.isSuccess) {
                    isConnected = false
                    _networkStatus.value = NetworkStatus.DISCONNECTED
                } else {
                    _networkStatus.value = NetworkStatus.ERROR
                }
                
                result
            }
        } catch (e: Exception) {
            _networkStatus.value = NetworkStatus.ERROR
            Result.failure(e)
        }
    }
    
    override suspend fun isNetworkConnected(): Boolean {
        return mutex.withLock { isConnected }
    }
    
    override suspend fun getNetworkStatistics(): NetworkStatistics {
        return mutex.withLock { 
            statistics.copy(
                connectionUptime = if (isConnected) {
                    System.currentTimeMillis() - statistics.connectionUptime
                } else {
                    0L
                }
            )
        }
    }
    
    override fun observeNetworkStatus(): Flow<NetworkStatus> {
        return networkStatus
    }
}