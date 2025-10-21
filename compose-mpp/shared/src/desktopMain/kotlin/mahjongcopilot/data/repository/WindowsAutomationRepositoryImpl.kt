package mahjongcopilot.data.repository

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import mahjongcopilot.data.model.Position
import mahjongcopilot.data.model.Size
import mahjongcopilot.data.model.WindowInfo
import java.util.concurrent.TimeUnit

/**
 * Windows平台特定的自动化仓库实现
 */
class WindowsAutomationRepositoryImpl : AutomationRepository {
    
    companion object {
        // Windows消息常量
        private const val WM_LBUTTONDOWN = 0x0201
        private const val WM_LBUTTONUP = 0x0202
        private const val WM_KEYDOWN = 0x0100
        private const val WM_KEYUP = 0x0101
        private const val PROCESS_QUERY_INFORMATION = 0x0400
        private const val PROCESS_VM_READ = 0x0010
    }

    // 定义Windows API接口
    private interface User32 : StdCallLibrary {
        fun EnumWindows(lpEnumFunc: WNDENUMPROC, lParam: Pointer): Boolean
        fun GetWindowTextW(hwnd: Pointer, lpString: CharArray, nMaxCount: Int): Int
        fun GetClassNameW(hwnd: Pointer, lpClassName: CharArray, nMaxCount: Int): Int
        fun IsWindowVisible(hwnd: Pointer): Boolean
        fun GetWindowThreadProcessId(hwnd: Pointer, lpdwProcessId: IntByReference): Int
        fun SetForegroundWindow(hwnd: Pointer): Boolean
        fun GetWindowRect(hwnd: Pointer, rect: RECT): Boolean
        fun GetClientRect(hwnd: Pointer, rect: RECT): Boolean
        fun ClientToScreen(hwnd: Pointer, point: POINT): Boolean
        fun SendMessage(hwnd: Pointer, msg: Int, wParam: Int, lParam: Int): Int
        fun PostMessage(hwnd: Pointer, msg: Int, wParam: Int, lParam: Int): Boolean
    }
    
    private interface Kernel32 : StdCallLibrary {
        fun OpenProcess(dwDesiredAccess: Int, bInheritHandle: Boolean, dwProcessId: Int): Pointer
        fun CloseHandle(hObject: Pointer): Boolean
        fun GetProcessImageFileNameW(hProcess: Pointer, lpImageFileName: CharArray, nSize: Int): Int
    }
    
    // 回调函数接口
    private interface WNDENUMPROC : StdCallLibrary.StdCallCallback {
        fun callback(hwnd: Pointer, lParam: Pointer): Boolean
    }
    
    // 结构体定义
    private class RECT(byValue: Boolean = false) : Structure(if (byValue) null else Pointer.NULL) {
        @JvmField var left: Int = 0
        @JvmField var top: Int = 0
        @JvmField var right: Int = 0
        @JvmField var bottom: Int = 0
        
        override fun getFieldOrder(): List<String> = listOf("left", "top", "right", "bottom")
    }
    
    private class POINT(byValue: Boolean = false) : Structure(if (byValue) null else Pointer.NULL) {
        @JvmField var x: Int = 0
        @JvmField var y: Int = 0
        
        override fun getFieldOrder(): List<String> = listOf("x", "y")
    }
    

    
    // 加载Windows库
    private val user32 = Native.load("user32", User32::class.java)
    private val kernel32 = Native.load("kernel32", Kernel32::class.java)
    
    override suspend fun findTargetWindow(windowTitle: String, processName: String): WindowInfo? = withContext(Dispatchers.IO) {
        var targetWindow: WindowInfo? = null
        
        try {
            val enumCallback = object : WNDENUMPROC {
                override fun callback(hwnd: Pointer, lParam: Pointer): Boolean {
                    // 检查窗口是否可见
                    if (!user32.IsWindowVisible(hwnd)) {
                        return true
                    }
                    
                    // 获取窗口标题
                    val titleBuffer = CharArray(512)
                    val titleLength = user32.GetWindowTextW(hwnd, titleBuffer, titleBuffer.size)
                    val title = String(titleBuffer, 0, titleLength)
                    
                    // 检查标题是否包含关键词
                    if (windowTitle.isNotEmpty() && !title.contains(windowTitle)) {
                        return true
                    }
                    
                    // 获取进程ID
                    val processIdRef = IntByReference()
                    user32.GetWindowThreadProcessId(hwnd, processIdRef)
                    val processId = processIdRef.value
                    
                    // 获取进程名
                    val processNameFound = getProcessName(processId, kernel32 as Any)
                    
                    // 检查进程名是否包含关键词
                    if (processName.isNotEmpty() && !processNameFound.contains(processName)) {
                        return true
                    }
                    
                    // 获取窗口位置和大小
                    val rect = RECT()
                    user32.GetWindowRect(hwnd, rect)
                    
                    // 创建窗口信息对象
                    targetWindow = WindowInfo(
                        title = title,
                        processName = processNameFound,
                        windowHandle = Pointer.nativeValue(hwnd),
                        position = Position(rect.left, rect.top),
                        size = Size(rect.right - rect.left, rect.bottom - rect.top)
                    )
                    
                    // 找到目标窗口，停止枚举
                    return false
                }
            }
            
            // 枚举所有顶级窗口
            user32.EnumWindows(enumCallback, Pointer.NULL)
            
            targetWindow
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override fun monitorProcess(processName: String): Flow<Boolean> {
        return flow {
            while (true) {
                val isRunning = isProcessRunning(processName)
                emit(isRunning)
                // 每秒检查一次
                TimeUnit.SECONDS.sleep(1)
            }
        }
    }
    
    override suspend fun simulateMouseClick(windowHandle: Long, x: Int, y: Int): Boolean {
        return withContext(Dispatchers.IO) {
            val hwnd = Pointer(windowHandle)
            
            // 设置窗口为前台
            user32.SetForegroundWindow(hwnd)
            
            // 计算点击位置的lParam
            val lParam = (y shl 16) or (x and 0xFFFF)
            
            // 发送鼠标点击消息
            val downResult = user32.PostMessage(hwnd, WM_LBUTTONDOWN, 0, lParam)
            Thread.sleep(50) // 短暂延迟
            val upResult = user32.PostMessage(hwnd, WM_LBUTTONUP, 0, lParam)
            
            downResult && upResult
        }
    }
    
    override suspend fun simulateKeyPress(windowHandle: Long, key: String, delayMs: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val hwnd = Pointer(windowHandle)
            
            // 设置窗口为前台
            user32.SetForegroundWindow(hwnd)
            
            // 获取虚拟键码
            val virtualKeyCode = getVirtualKeyCode(key)
            if (virtualKeyCode == 0) return@withContext false
            
            // 发送按键消息
            val downResult = user32.PostMessage(hwnd, WM_KEYDOWN, virtualKeyCode, 0)
            Thread.sleep(delayMs)
            val upResult = user32.PostMessage(hwnd, WM_KEYUP, virtualKeyCode, 0)
            
            downResult && upResult
        }
    }
    
    override suspend fun captureWindow(windowHandle: Long): ByteArray? {
        // 这个功能需要更复杂的GDI+实现，暂时返回null
        // 将在后续阶段实现
        return null
    }
    
    // 辅助方法
    fun getProcessName(processId: Int): String {
        val kernel32 = Native.load("kernel32", Kernel32::class.java)
        return getProcessName(processId, kernel32)
    }
    
    fun getProcessName(processId: Int, kernel32: Any): String {
        val kernel32Lib = kernel32 as Kernel32
        val processHandle = kernel32Lib.OpenProcess(
            PROCESS_QUERY_INFORMATION or PROCESS_VM_READ,
            false,
            processId
        )
        
        if (processHandle == Pointer.NULL) return ""
        
        try {
            val buffer = CharArray(1024)
            val length = kernel32Lib.GetProcessImageFileNameW(processHandle, buffer, buffer.size)
            if (length > 0) {
                val fullPath = String(buffer, 0, length)
                // 返回文件名部分
                return fullPath.substringAfterLast('\\', "")
            }
        } finally {
            kernel32Lib.CloseHandle(processHandle)
        }
        
        return ""
    }
    
    fun isProcessRunning(processName: String): Boolean {
        val user32 = Native.load("user32", User32::class.java)
        val kernel32 = Native.load("kernel32", Kernel32::class.java)
        
        var isRunning = false
        
        try {
            val enumCallback = object : WNDENUMPROC {
                override fun callback(hwnd: Pointer, lParam: Pointer): Boolean {
                    // 获取进程ID
                    val processIdRef = IntByReference()
                    user32.GetWindowThreadProcessId(hwnd, processIdRef)
                    val processId = processIdRef.value
                    
                    // 获取进程名
                    val currentProcessName = getProcessName(processId, kernel32 as Any)
                    
                    // 检查是否匹配
                    if (currentProcessName.contains(processName)) {
                        isRunning = true
                        return false // 找到匹配的进程，停止枚举
                    }
                    
                    return true // 继续枚举
                }
            }
            
            user32.EnumWindows(enumCallback, Pointer.NULL)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return isRunning
    }
    
    fun getVirtualKeyCode(key: String): Int {
        return when (key.uppercase()) {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> 0x30 + key.toInt()
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z" -> 0x41 + (key[0] - 'A')
            "F1" -> 0x70
            "F2" -> 0x71
            "F3" -> 0x72
            "F4" -> 0x73
            "F5" -> 0x74
            "F6" -> 0x75
            "F7" -> 0x76
            "F8" -> 0x77
            "F9" -> 0x78
            "F10" -> 0x79
            "F11" -> 0x7A
            "F12" -> 0x7B
            "SPACE" -> 0x20
            "ENTER" -> 0x0D
            "ESC" -> 0x1B
            "TAB" -> 0x09
            "SHIFT" -> 0x10
            "CTRL" -> 0x11
            "ALT" -> 0x12
            "LEFT" -> 0x25
            "UP" -> 0x26
            "RIGHT" -> 0x27
            "DOWN" -> 0x28
            else -> 0
        }
    }
}