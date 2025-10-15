package mahjongcopilot.test

import kotlinx.coroutines.*
import mahjongcopilot.platform.MitmNetworkInterceptor
import mahjongcopilot.data.model.LiqiMessage
import mahjongcopilot.data.model.NetworkSettings
import kotlin.system.measureTimeMillis

/**
 * 测试MITM网络拦截器功能
 */
fun main() = runBlocking {
    println("开始测试MITM网络拦截器...")
    
    val interceptor = MitmNetworkInterceptor()
    
    // 启动拦截器
    val startResult = interceptor.startInterception()
    if (startResult.isSuccess) {
        println("MITM拦截器启动成功")
    } else {
        println("MITM拦截器启动失败: ${startResult.exceptionOrNull()?.message}")
        return@runBlocking
    }
    
    // 监听消息
    val job = launch {
        interceptor.capturedMessages.collect { message ->
            println("收到消息: ${message.method} (${message.type})")
            
            // 如果是游戏相关消息，打印更多信息
            if (message.method.name.contains("Game") || message.method.name.contains("Action")) {
                println("  消息ID: ${message.id}")
                println("  数据长度: ${message.data.toString().length}")
            }
        }
    }
    
    // 等待用户输入
    println("按Enter键停止测试...")
    readLine()
    
    // 停止拦截器
    job.cancel()
    val stopResult = interceptor.stopInterception()
    if (stopResult.isSuccess) {
        println("MITM拦截器已停止")
    } else {
        println("停止MITM拦截器失败: ${stopResult.exceptionOrNull()?.message}")
    }
}