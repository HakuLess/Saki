import json
import base64
import struct
from mitmproxy import http
from mitmproxy import websocket

# 基于MahjongCopilot实现的mitmproxy脚本，用于拦截雀魂的WebSocket消息

# 雀魂相关域名
MAJSOUL_DOMAINS = [
    "maj-soul.com",
    "majsoul.com", 
    "mahjongsoul.com",
    "yo-star.com"
]

# 消息类型
class MsgType:
    NOTIFY = 1  # 服务器到客户端通知
    REQ = 2     # 客户端到服务器请求
    RES = 3     # 服务器到客户端响应

# XOR解密密钥
KEYS = [0x84, 0x5e, 0x4e, 0x42, 0x39, 0xa2, 0x1f, 0x60, 0x1c]

def decode(data: bytes) -> bytes:
    """解密数据"""
    data = bytearray(data)
    for i in range(len(data)):
        u = (23 ^ len(data)) + 5 * i + KEYS[i % len(KEYS)] & 255
        data[i] ^= u
    return bytes(data)

def websocket_start(flow: http.HTTPFlow):
    """WebSocket连接开始"""
    if any(domain in flow.request.pretty_host for domain in MAJSOUL_DOMAINS):
        print(f"WebSocket连接开始: {flow.request.pretty_host}")
        # 记录连接开始
        message_data = {
            "type": "websocket_start",
            "host": flow.request.pretty_host,
            "timestamp": flow.timestamp_start
        }
        save_message(message_data)

def websocket_message(flow: http.HTTPFlow):
    """WebSocket消息处理"""
    if any(domain in flow.request.pretty_host for domain in MAJSOUL_DOMAINS):
        msg = flow.websocket.messages[-1]
        from_client = msg.from_client
        
        try:
            # 解析消息类型
            msg_type = msg.content[0]
            
            if msg_type == MsgType.NOTIFY:
                # 通知消息
                parse_notify_message(msg.content, from_client, flow.request.pretty_host)
            elif msg_type == MsgType.REQ:
                # 请求消息
                parse_req_message(msg.content, from_client, flow.request.pretty_host)
            elif msg_type == MsgType.RES:
                # 响应消息
                parse_res_message(msg.content, from_client, flow.request.pretty_host)
            else:
                print(f"未知消息类型: {msg_type}")
                
        except Exception as e:
            print(f"解析WebSocket消息错误: {e}")

def websocket_end(flow: http.HTTPFlow):
    """WebSocket连接结束"""
    if any(domain in flow.request.pretty_host for domain in MAJSOUL_DOMAINS):
        print(f"WebSocket连接结束: {flow.request.pretty_host}")
        message_data = {
            "type": "websocket_end",
            "host": flow.request.pretty_host,
            "timestamp": flow.timestamp_end
        }
        save_message(message_data)

def response(flow):
    # 检查是否是雀魂相关的域名
    if any(domain in flow.request.pretty_host for domain in [
        "maj-soul.com",
        "majsoul.com", 
        "mahjongsoul.com",
        "yo-star.com"
    ]):
        # 打印请求信息
        print(f"Intercepted request to: {flow.request.pretty_host}")
        print(f"Request path: {flow.request.path}")
        
        # 如果有响应内容，保存它
        if flow.response and flow.response.content:
            # 将响应内容保存到文件中
            try:
                import os
                # 创建一个简单的消息对象
                message_data = {
                    "host": flow.request.pretty_host,
                    "path": flow.request.path,
                    "content": flow.response.content.decode('utf-8', errors='ignore')
                }
                
                # 使用绝对路径保存文件
                file_path = os.path.join("D:\\WorkSpace\\Saki\\compose-mpp", "mitm_messages.json")
                # 将消息追加到文件中
                with open(file_path, "a", encoding="utf-8") as f:
                    f.write(json.dumps(message_data, ensure_ascii=False) + "\n")
                    
            except Exception as e:
                print(f"Error processing response: {e}")

def request(flow):
    # 检查是否是雀魂相关的域名
    if any(domain in flow.request.pretty_host for domain in [
        "maj-soul.com",
        "majsoul.com", 
        "mahjongsoul.com",
        "yo-star.com"
    ]):
        # 打印请求信息
        print(f"Intercepted request to: {flow.request.pretty_host}")
        print(f"Request path: {flow.request.path}")
        print(f"Request method: {flow.request.method}")
        
        # 如果有请求内容，保存它
        if flow.request.content:
            try:
                import os
                # 创建一个简单的消息对象
                message_data = {
                    "host": flow.request.pretty_host,
                    "path": flow.request.path,
                    "method": flow.request.method,
                    "content": flow.request.content.decode('utf-8', errors='ignore')
                }
                
                # 使用绝对路径保存文件
                file_path = os.path.join("D:\\WorkSpace\\Saki\\compose-mpp", "mitm_messages.json")
                # 将消息追加到文件中
                with open(file_path, "a", encoding="utf-8") as f:
                    f.write(json.dumps(message_data, ensure_ascii=False) + "\n")
                    
            except Exception as e:
                print(f"Error processing request: {e}")

def parse_notify_message(data: bytes, from_client: bool, host: str):
    """解析通知消息"""
    try:
        # 跳过消息类型字节
        protobuf_data = data[1:]
        
        # 解析Protobuf结构
        msg_block = from_protobuf(protobuf_data)
        
        if len(msg_block) >= 2:
            method_name = msg_block[0]['data'].decode('utf-8')
            
            message_data = {
                "type": "notify",
                "from_client": from_client,
                "host": host,
                "method": method_name,
                "data": base64.b64encode(msg_block[1]['data']).decode('utf-8'),
                "timestamp": get_timestamp()
            }
            
            # 特殊处理ActionPrototype消息
            if method_name == ".lq.ActionPrototype":
                parse_action_prototype(msg_block[1]['data'], message_data)
            
            save_message(message_data)
            print(f"拦截到通知消息: {method_name}")
            
    except Exception as e:
        print(f"解析通知消息错误: {e}")

def parse_req_message(data: bytes, from_client: bool, host: str):
    """解析请求消息"""
    try:
        # 提取消息ID和Protobuf数据
        msg_id = struct.unpack('<H', data[1:3])[0]
        protobuf_data = data[3:]
        
        # 解析Protobuf结构
        msg_block = from_protobuf(protobuf_data)
        
        if len(msg_block) >= 2:
            method_name = msg_block[0]['data'].decode('utf-8')
            
            message_data = {
                "type": "req",
                "id": msg_id,
                "from_client": from_client,
                "host": host,
                "method": method_name,
                "data": base64.b64encode(msg_block[1]['data']).decode('utf-8'),
                "timestamp": get_timestamp()
            }
            
            save_message(message_data)
            print(f"拦截到请求消息: {method_name} (ID: {msg_id})")
            
    except Exception as e:
        print(f"解析请求消息错误: {e}")

def parse_res_message(data: bytes, from_client: bool, host: str):
    """解析响应消息"""
    try:
        # 提取消息ID和Protobuf数据
        msg_id = struct.unpack('<H', data[1:3])[0]
        protobuf_data = data[3:]
        
        # 解析Protobuf结构
        msg_block = from_protobuf(protobuf_data)
        
        message_data = {
            "type": "res",
            "id": msg_id,
            "from_client": from_client,
            "host": host,
            "method": "",  # 响应消息没有方法名
            "data": base64.b64encode(msg_block[1]['data']).decode('utf-8') if len(msg_block) >= 2 else "",
            "timestamp": get_timestamp()
        }
        
        save_message(message_data)
        print(f"拦截到响应消息 (ID: {msg_id})")
        
    except Exception as e:
        print(f"解析响应消息错误: {e}")

def parse_action_prototype(data: bytes, base_message: dict):
    """解析ActionPrototype消息"""
    try:
        # 解密数据
        decoded_data = decode(data)
        
        # 解析Protobuf结构
        action_block = from_protobuf(decoded_data)
        
        if len(action_block) >= 2:
            action_name = action_block[0]['data'].decode('utf-8')
            action_data = base64.b64encode(action_block[1]['data']).decode('utf-8')
            
            base_message.update({
                "action_name": action_name,
                "action_data": action_data
            })
            
            print(f"解析Action: {action_name}")
            
    except Exception as e:
        print(f"解析ActionPrototype错误: {e}")

def from_protobuf(buf: bytes) -> list:
    """解析Protobuf数据结构"""
    p = 0
    result = []
    while p < len(buf):
        block_begin = p
        block_type = (buf[p] & 7)
        block_id = buf[p] >> 3
        p += 1
        
        if block_type == 0:
            # varint
            block_type_str = 'varint'
            data, p = parse_varint(buf, p)
        elif block_type == 2:
            # string
            block_type_str = 'string'
            s_len, p = parse_varint(buf, p)
            data = buf[p:p+s_len]
            p += s_len
        else:
            raise ValueError(f"Unknown type: {block_type}")
            
        result.append({
            'id': block_id, 
            'type': block_type_str,
            'data': data, 
            'begin': block_begin
        })
        
    return result

def parse_varint(buf: bytes, p: int) -> tuple:
    """解析varint"""
    data = 0
    base = 0
    while p < len(buf):
        data += (buf[p] & 127) << base
        base += 7
        p += 1
        if buf[p-1] >> 7 == 0:
            break
    return data, p

def save_message(message_data: dict):
    """保存消息到文件"""
    try:
        import os
        # 使用绝对路径保存文件
        file_path = os.path.join("D:\\WorkSpace\\Saki\\compose-mpp", "mitm_messages.json")
        with open(file_path, "a", encoding="utf-8") as f:
            f.write(json.dumps(message_data, ensure_ascii=False) + "\n")
    except Exception as e:
        print(f"保存消息错误: {e}")

def get_timestamp() -> float:
    """获取当前时间戳"""
    import time
    return time.time()

# 启动信息
print("MITM WebSocket拦截脚本加载成功")