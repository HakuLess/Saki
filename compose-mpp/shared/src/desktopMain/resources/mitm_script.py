#!/usr/bin/env python3
"""
MITM脚本，用于拦截和处理雀魂麻将的网络通信
参考MahjongCopilot的实现方式
"""

import json
import os
import sys
import asyncio
from mitmproxy import http

# 雀魂相关域名
MAJSOUL_DOMAINS = [
    "maj-soul.com",
    "majsoul.com", 
    "mahjongsoul.com",
    "yo-star.com"
]

# 消息输出文件
MESSAGE_OUTPUT_FILE = "mitm_messages.json"

def is_majsoul_url(url: str) -> bool:
    """检查URL是否属于雀魂相关域名"""
    return any(domain in url for domain in MAJSOUL_DOMAINS)

class MajsoulInterceptor:
    """雀魂网络拦截器"""
    
    def __init__(self):
        self.message_counter = 0
        self.output_file = MESSAGE_OUTPUT_FILE
        
    def websocket_start(self, flow):
        """WebSocket连接开始"""
        if is_majsoul_url(flow.handshake_flow.request.pretty_url):
            print(f"WebSocket connection started: {flow.handshake_flow.request.pretty_url}")
            sys.stdout.flush()
    
    def websocket_message(self, flow):
        """处理WebSocket消息"""
        if is_majsoul_url(flow.handshake_flow.request.pretty_url):
            # 获取消息内容
            message = flow.messages[-1]
            
            # 创建消息对象
            message_data = {
                "flow_id": flow.id,
                "timestamp": message.timestamp,
                "content": message.content.decode('utf-8', errors='ignore') if isinstance(message.content, bytes) else str(message.content),
                "direction": "client_to_server" if message.from_client else "server_to_client"
            }
            
            # 将消息写入文件
            self.write_message_to_file(message_data)
            
            # 打印消息摘要
            print(f"WebSocket message: flow_id={flow.id}, direction={message_data['direction']}")
            sys.stdout.flush()
    
    def websocket_end(self, flow):
        """WebSocket连接结束"""
        if is_majsoul_url(flow.handshake_flow.request.pretty_url):
            print(f"WebSocket connection ended: {flow.handshake_flow.request.pretty_url}")
            sys.stdout.flush()
    
    def request(self, flow: http.HTTPFlow):
        """处理HTTP请求"""
        # 拦截雀魂的特定请求
        if is_majsoul_url(flow.request.pretty_url):
            print(f"HTTP Request: {flow.request.method} {flow.request.pretty_url}")
            sys.stdout.flush()
            
    def response(self, flow: http.HTTPFlow):
        """处理HTTP响应"""
        if is_majsoul_url(flow.request.pretty_url):
            status_code = getattr(flow.response, 'status_code', 'Unknown')
            print(f"HTTP Response: {flow.request.pretty_url} - {status_code}")
            sys.stdout.flush()
            
            # 特别处理阿里云日志请求
            if "majsoul-hk-client.cn-hongkong.log.aliyuncs.com" in flow.request.pretty_url:
                try:
                    # 解析查询参数
                    import urllib.parse
                    parsed_url = urllib.parse.urlparse(flow.request.pretty_url)
                    query_params = urllib.parse.parse_qs(parsed_url.query)
                    
                    if "content" in query_params:
                        content = query_params["content"][0]
                        content_data = json.loads(content)
                        if content_data.get("type") == "re_err":
                            print(f"Majsoul Aliyun Error (killed): {query_params}")
                            flow.kill()
                        else:
                            print(f"Majsoul Aliyun Log: {query_params}")
                except Exception as e:
                    pass
    
    def write_message_to_file(self, message_data):
        """将消息写入文件"""
        try:
            # 以追加模式写入文件
            with open(self.output_file, "a", encoding="utf-8") as f:
                f.write(json.dumps(message_data) + "\n")
        except Exception as e:
            print(f"Error writing message to file: {e}")
            sys.stdout.flush()

# 创建拦截器实例
addons = [
    MajsoulInterceptor()
]