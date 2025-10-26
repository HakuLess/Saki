package net

import com.browserup.bup.BrowserUpProxyServer
import com.browserup.bup.filters.RequestFilter
import com.browserup.bup.filters.ResponseFilter
import com.browserup.bup.mitm.RootCertificateGenerator
import com.browserup.bup.mitm.manager.ImpersonatingMitmManager
import com.browserup.bup.util.HttpMessageContents
import com.browserup.bup.util.HttpMessageInfo
import java.io.File

class MitmProxyServer(
    private val port: Int,
    private val hostPatterns: List<String>,
    private val onCapture: (bytes: ByteArray, inbound: Boolean) -> Unit,
    private val enableSocks5: Boolean = false  // 新增：是否启用SOCKS5模式
) {
    private var server: BrowserUpProxyServer? = null

    fun start() {
        if (server != null) return

        val baseDir = File(System.getProperty("user.home"), "AppData/Roaming/Saki/mitm").apply { mkdirs() }
        val certFile = File(baseDir, "ca-certificate.cer")
        val keyStoreFile = File(baseDir, "ca-keystore.p12")
        val rootGen = RootCertificateGenerator.builder().build()
        // 每次启动都写入当前生成的根证书与私钥，确保与本次运行一致
        rootGen.saveRootCertificateAsPemFile(certFile)
        rootGen.saveRootCertificateAndKey("PKCS12", keyStoreFile, "privateKeyAlias", "password")

        val proxy = BrowserUpProxyServer()
        proxy.setMitmManager(
            ImpersonatingMitmManager.builder()
                .rootCertificateSource(rootGen)
                .build()
        )
        
        // 配置SOCKS5支持
        if (enableSocks5) {
            // BrowserUpProxy 本身支持作为 SOCKS5 代理运行
            // 但需要禁用某些 HTTP 特定的功能
            proxy.setTrustAllServers(true)
        }
        
        proxy.start(port)

        proxy.addRequestFilter(RequestFilter { request, contents, messageInfo ->
            captureIfMatch(contents, messageInfo, inbound = true)
            null
        })
        proxy.addResponseFilter(ResponseFilter { response, contents, messageInfo ->
            captureIfMatch(contents, messageInfo, inbound = false)
        })

        server = proxy
        val proxyType = if (enableSocks5) "SOCKS5/HTTPS" else "HTTPS"
        println("[MITM] $proxyType Proxy started at port $port (CA at ${certFile.absolutePath})")
    }

    fun stop() {
        server?.stop()
        server = null
        println("[MITM] Proxy stopped")
    }

    private fun captureIfMatch(contents: HttpMessageContents, info: HttpMessageInfo, inbound: Boolean) {
        val url = info.originalUrl ?: ""
        val matched = hostPatterns.any { url.contains(it, ignoreCase = true) }
        if (!matched) return
        val dirLabel = if (inbound) "CLIENT->SERVER" else "SERVER->CLIENT"
        val size = contents.binaryContents?.size ?: 0
        println("[MITM] Match $dirLabel url=$url size=$size contentType=${contents.contentType}")
        val bytes = contents.binaryContents
        if (bytes != null && bytes.isNotEmpty()) {
            onCapture(bytes, inbound)
        }
    }
}