# MahjongCopilot 项目

## 项目地址
https://github.com/latorc/MahjongCopilot

## 项目逻辑

### 项目概述
MahjongCopilot是一个麻将AI助手，基于mjai (Mortal模型)实现的机器人。它能对雀魂游戏对局的每一步进行实时指导，支持三人和四人麻将模式。

### 核心功能
- 对局每一步AI指导，可在游戏中覆盖显示
- 自动打牌，自动加入游戏
- 多语言支持
- 支持本地Mortal模型和在线模型

### 项目结构
项目主要包含以下目录：
- `gui`: tkinter GUI相关类
- `game`: 雀魂游戏相关类
- `bot`: AI模型和机器人实现
- `common`: 共同使用的支持代码
- `libriichi` & `libriichi3p`: 编译完成的libriichi库文件

### 技术实现
- 基于Mortal模型和MJAI协议
- 设计和功能实现参考了Akagi项目
- 使用Python开发，推荐Python 3.11版本
- 使用Playwright + Chromium进行浏览器自动化

### 使用方法
1. 克隆仓库
2. 安装Python虚拟环境
3. 安装requirements.txt中的依赖
4. 安装Playwright + Chromium
5. 运行main.py

### 模型配置
支持多种模型来源，本地模型(Local)基于Akagi兼容的Mortal模型。

### 许可证
项目使用GNU GPL v3许可协议。