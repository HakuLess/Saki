# mitmproxy 配置指南

## 简介

mitmproxy是一个强大的中间人代理工具，用于拦截和分析网络流量。在雀魂游戏拦截器中，我们使用mitmproxy来捕获雀魂游戏的网络通信。

## 安装方法

### Windows 安装

#### 方法1: 使用pip安装
```bash
pip install mitmproxy
```

#### 方法2: 使用conda安装
```bash
conda install -c conda-forge mitmproxy
```

#### 方法3: 下载预编译版本
访问 [mitmproxy.org](https://mitmproxy.org/) 下载Windows版本

### 验证安装
```bash
mitmdump --version
```

## 证书安装

### 为什么需要安装证书？

雀魂游戏使用HTTPS加密通信，mitmproxy需要安装自己的根证书才能解密HTTPS流量。

### 安装步骤

1. **启动mitmproxy生成证书**
   ```bash
   mitmdump
   ```

2. **在浏览器中安装证书**
   - 打开浏览器，访问 `http://mitm.it`
   - 选择对应的操作系统图标
   - 下载证书文件

3. **Windows证书安装**
   - 双击下载的证书文件
   - 选择"将所有的证书都放入下列存储"
   - 点击"浏览"，选择"受信任的根证书颁发机构"
   - 完成导入

4. **验证证书安装**
   - 重启浏览器
   - 访问 `https://example.com`，应该能看到mitmproxy的拦截页面

## 代理配置

### 系统级代理配置

1. **Windows设置**
   - 打开"设置" → "网络和Internet" → "代理"
   - 启用"使用代理服务器"
   - 地址：`127.0.0.1`
   - 端口：`8080`
   - 保存设置

2. **命令行配置（临时）**
   ```bash
   # 设置代理
   set HTTP_PROXY=http://127.0.0.1:8080
   set HTTPS_PROXY=http://127.0.0.1:8080
   ```

### 应用级代理配置

某些应用可能需要单独配置代理：

```bash
# 命令行应用代理
curl --proxy http://127.0.0.1:8080 https://example.com

# Python请求代理
import requests
proxies = {
    'http': 'http://127.0.0.1:8080',
    'https': 'http://127.0.0.1:8080'
}
response = requests.get('https://example.com', proxies=proxies, verify=False)
```

## 雀魂游戏特殊配置

### 处理WebSocket连接

雀魂游戏使用WebSocket进行实时通信，mitmscript.py已经包含了WebSocket拦截功能。

### 处理二进制数据

雀魂游戏使用Protobuf格式传输数据，脚本会自动进行XOR解密和解析。

## 故障排除

### 常见问题

**Q: mitmproxy无法启动**
A: 检查端口8080是否被占用，可以尝试其他端口：
```bash
mitmdump -p 8081
```

**Q: HTTPS网站显示证书错误**
A: 重新安装mitmproxy证书，确保证书在"受信任的根证书颁发机构"中

**Q: 某些应用不通过代理**
A: 这些应用可能使用硬编码的代理设置或直接连接，需要检查应用的具体配置

**Q: 网络速度变慢**
A: 这是正常现象，因为所有流量都经过mitmproxy处理

### 调试技巧

1. **查看原始流量**
   ```bash
   mitmdump -v
   ```

2. **保存流量到文件**
   ```bash
   mitmdump -w traffic.mitm
   ```

3. **重放流量**
   ```bash
   mitmdump -r traffic.mitm
   ```

## 安全注意事项

1. **不要在生产环境使用** - mitmproxy会解密所有HTTPS流量，存在安全风险

2. **及时关闭代理** - 使用完成后记得关闭系统代理设置

3. **保护证书安全** - mitmproxy的根证书可以用于中间人攻击，请妥善保管

## 高级用法

### 自定义脚本

除了默认的mitm_script.py，你还可以编写自定义脚本：

```python
# custom_script.py
def request(flow):
    # 处理请求
    pass

def response(flow):
    # 处理响应
    pass

def websocket_message(flow):
    # 处理WebSocket消息
    pass
```

运行自定义脚本：
```bash
mitmdump -s custom_script.py
```

### 过滤特定流量

```bash
# 只拦截雀魂相关流量
mitmdump -s mitm_script.py --filter "host maj-soul.com"
```

## 相关资源

- [mitmproxy官方文档](https://docs.mitmproxy.org/)
- [mitmproxy教程](https://mitmproxy.org/doc/tutorials/)
- [证书安装指南](https://docs.mitmproxy.org/stable/concepts-certificates/)