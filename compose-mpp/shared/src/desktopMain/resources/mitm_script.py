#!/usr/bin/env python3
"""
MITM脚本，用于拦截和处理雀魂麻将的网络通信
参考MahjongCopilot的实现方式，增强协议解析功能
"""

import json
import os
import sys
import base64
import struct
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

# 消息类型枚举
class MsgType:
    NOTIFY = 1  # 服务器到客户端通知
    REQ = 2     # 客户端到服务器请求
    RES = 3     # 服务器到客户端响应

def is_majsoul_url(url: str) -> bool:
    """检查URL是否属于雀魂相关域名"""
    return any(domain in url for domain in MAJSOUL_DOMAINS)

class MajsoulInterceptor:
    """雀魂网络拦截器，增强版"""
    
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
            
            # 尝试解析二进制消息
            try:
                parsed_message = self.parse_liqi_message(message.content)
                if parsed_message:
                    # 将解析后的消息写入文件
                    self.write_message_to_file(parsed_message)
                    
                    # 打印消息摘要
                    print(f"WebSocket message: flow_id={flow.id}, direction={'client_to_server' if message.from_client else 'server_to_client'}")
                    print(f"  Method: {parsed_message.get('method', 'Unknown')}")
                    print(f"  Type: {parsed_message.get('type', 'Unknown')}")
                    print(f"  ID: {parsed_message.get('id', 'Unknown')}")
                    sys.stdout.flush()
                    return
            except Exception as e:
                print(f"Error parsing binary message: {e}")
            
            # 如果二进制解析失败，尝试作为文本处理
            try:
                content_str = message.content.decode('utf-8', errors='ignore')
                # 创建消息对象
                message_data = {
                    "flow_id": flow.id,
                    "timestamp": message.timestamp,
                    "content": content_str,
                    "direction": "client_to_server" if message.from_client else "server_to_client"
                }
                
                # 将消息写入文件
                self.write_message_to_file(message_data)
                
                # 打印消息摘要
                print(f"WebSocket message: flow_id={flow.id}, direction={message_data['direction']}")
                sys.stdout.flush()
            except Exception as e:
                print(f"Error processing message: {e}")
    
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
    
    def parse_liqi_message(self, content):
        """解析雀魂二进制消息，参考MahjongCopilot的liqi.py实现"""
        if not content or len(content) < 2:
            return None
            
        try:
            buf = content
            msg_type = buf[0]  # 通信报文类型
            
            result = {}
            
            if msg_type == MsgType.NOTIFY:
                # 通知消息
                msg_block = self.from_protobuf(buf[1:])
                if len(msg_block) >= 2 and msg_block[0]['type'] == 'string':
                    method_name = msg_block[0]['data'].decode()
                    result['method'] = method_name
                    result['type'] = 'NOTIFY'
                    result['id'] = -1
                    
                    # 尝试解析protobuf数据
                    try:
                        protobuf_data = msg_block[1]['data']
                        result['data'] = base64.b64encode(protobuf_data).decode('utf-8')
                    except:
                        result['data'] = ""
                        
            elif msg_type == MsgType.REQ or msg_type == MsgType.RES:
                # 请求或响应消息
                if len(buf) < 3:
                    return None
                    
                msg_id = struct.unpack('<H', buf[1:3])[0]  # 小端序解析报文编号
                msg_block = self.from_protobuf(buf[3:])
                
                if len(msg_block) >= 2 and msg_block[0]['type'] == 'string':
                    method_name = msg_block[0]['data'].decode()
                    result['method'] = method_name
                    result['type'] = 'REQ' if msg_type == MsgType.REQ else 'RES'
                    result['id'] = msg_id
                    
                    # 尝试解析protobuf数据
                    try:
                        protobuf_data = msg_block[1]['data']
                        result['data'] = base64.b64encode(protobuf_data).decode('utf-8')
                    except:
                        result['data'] = ""
            else:
                return None
                
            # 添加原始数据
            result['raw_data'] = base64.b64encode(content).decode('utf-8')
            return result
            
        except Exception as e:
            print(f"Error parsing liqi message: {e}")
            return None
    
    def from_protobuf(self, buf):
        """解析protobuf结构，参考MahjongCopilot的liqi.py实现"""
        p = 0
        result = []
        
        while p < len(buf):
            block_type = (buf[p] & 7)
            block_id = buf[p] >> 3
            p += 1
            
            if block_type == 0:
                # varint
                data, p = self.parse_varint(buf, p)
                block_type = 'varint'
            elif block_type == 2:
                # string
                s_len, p = self.parse_varint(buf, p)
                data = buf[p:p+s_len]
                p += s_len
                block_type = 'string'
            else:
                break
                
            result.append({'id': block_id, 'type': block_type, 'data': data})
            
        return result
    
    def parse_varint(self, buf, p):
        """解析varint，参考MahjongCopilot的liqi.py实现"""
        data = 0
        base = 0
        
        while p < len(buf):
            data += (buf[p] & 127) << base
            base += 7
            p += 1
            if buf[p-1] >> 7 == 0:
                break
                
        return (data, p)
    
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