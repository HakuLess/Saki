# Saki项目MITM集成说明

本项目已成功集成了MahjongCopilot的mitmproxy功能，用于拦截和处理雀魂麻将的网络通信。

## 功能概述

1. **网络拦截**: 使用mitmproxy拦截雀魂游戏的WebSocket和HTTP通信
2. **协议解析**: 参考MahjongCopilot的liqi.py实现，解析雀魂二进制协议
3. **消息处理**: 将拦截的消息转换为LiqiMessage对象，供应用其他部分使用

## 文件结构

```
Saki/compose-mpp/shared/src/
├── desktopMain/
│   ├── kotlin/mahjongcopilot/platform/
│   │   ├── MitmNetworkInterceptor.kt      # MITM网络拦截器实现
│   │   └── WindowsNetworkManagerServiceImpl.kt  # Windows网络管理服务
│   └── resources/
│       └── mitm_script.py                  # MITM代理脚本
└── commonMain/kotlin/mahjongcopilot/
    ├── data/model/
    │   ├── LiqiMessage.kt                  # 消息模型
    │   ├── LiqiMethod.kt                   # 方法枚举
    │   └── LiqiMessageType.kt              # 消息类型枚举
    └── test/
        └── TestMitmInterceptor.kt          # 测试程序
```

## 使用方法

1. **启动MITM拦截器**:
   ```kotlin
   val interceptor = MitmNetworkInterceptor()
   val success = interceptor.start()
   ```

2. **监听消息**:
   ```kotlin
   interceptor.messageFlow.collect { message ->
       println("收到消息: ${message.method} (${message.type})")
   }
   ```

3. **停止拦截器**:
   ```kotlin
   interceptor.stop()
   ```

## 配置说明

### 代理设置
- 默认使用HTTP代理模式
- 代理端口: 8080
- 支持SOCKS5代理模式

### 域名过滤
- 只拦截雀魂相关域名的通信
- 包括: maj-soul.com, majsoul.com, mahjongsoul.com, yo-star.com

### 消息解析
- 自动识别二进制协议和文本协议
- 解析雀魂的protobuf消息
- 将消息转换为LiqiMessage对象

## 测试方法

运行测试程序:
```bash
./gradlew :shared:desktopMain:runTestMitmInterceptor
```

## 注意事项

1. **权限要求**: 需要管理员权限启动mitmproxy
2. **防火墙**: 确保防火墙允许代理端口通信
3. **证书**: 首次使用需要安装mitmproxy证书
4. **游戏设置**: 需要配置游戏使用代理

## 故障排除

1. **无法启动**:
   - 检查Python环境是否安装mitmproxy
   - 确认端口8080未被占用
   - 检查是否有管理员权限

2. **无法拦截消息**:
   - 确认游戏已配置使用代理
   - 检查域名是否在过滤列表中
   - 查看mitm_script.py的日志输出

3. **消息解析错误**:
   - 检查协议版本是否匹配
   - 查看错误日志
   - 尝试更新协议解析代码

## 开发说明

如需扩展功能，可以修改以下文件:

1. **mitm_script.py**: 添加新的消息处理逻辑
2. **MitmNetworkInterceptor.kt**: 添加新的消息解析方法
3. **LiqiMessage.kt**: 扩展消息模型
4. **LiqiMethod.kt**: 添加新的方法枚举

## 参考资料

- [MahjongCopilot项目](https://github.com/Avenshy/MahjongCopilot)
- [mitmproxy文档](https://docs.mitmproxy.org/)
- [雀魂协议分析](https://github.com/MajsoulAddon/majsoul_protocol)