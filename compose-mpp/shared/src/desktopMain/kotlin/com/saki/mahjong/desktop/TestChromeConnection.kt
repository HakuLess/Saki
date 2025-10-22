package com.saki.mahjong.desktop

import com.microsoft.playwright.Playwright
import java.net.HttpURLConnection
import java.net.URL

object TestChromeConnection {
    fun testConnection() {
        try {
            // 测试是否可以连接到Chrome的调试端口
            val url = URL("http://localhost:9222/json/version")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            println("Chrome DevTools Protocol test - Response Code: $responseCode")
            
            if (responseCode == 200) {
                println("Successfully connected to Chrome DevTools Protocol!")
                // 读取响应内容
                val response = connection.inputStream.bufferedReader().readText()
                println("Response: $response")
            } else {
                println("Failed to connect to Chrome DevTools Protocol")
            }
            
            connection.disconnect()
        } catch (e: Exception) {
            println("Error testing Chrome connection: ${e.message}")
            e.printStackTrace()
        }
        
        // 测试Playwright是否能正常工作
        try {
            println("Testing Playwright...")
            val playwright = Playwright.create()
            println("Playwright created successfully")
            playwright.close()
        } catch (e: Exception) {
            println("Error testing Playwright: ${e.message}")
            e.printStackTrace()
        }
    }
}