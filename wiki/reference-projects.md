# 参考项目详细信息

## 1. MahjongCopilot项目
- **项目类型**：雀魂AI助手
- **核心功能**：
  - 对局指导
  - 自动打牌
  - 多语言支持
  - 本地/在线模型支持
  - 游戏中覆盖显示(HUD)功能
- **技术栈**：
  - 编程语言：Python 3.11
  - 自动化工具：Playwright
  - 浏览器：Chromium
  - GUI框架：tkinter
  - 模型：基于Mortal模型
- **项目结构**：
  - gui：tkinter GUI相关类
  - game：雀魂游戏相关类
  - bot：AI模型和机器人实现
  - common：共同使用的支持代码
  - libriichi & libriichi3p：编译完成的库文件
- **主程序入口**：main.py
- **项目链接**：https://github.com/latorc/MahjongCopilot

## 2. Akagi项目
- **项目类型**：麻将AI模型项目
- **核心功能**：
  - 提供麻将AI决策模型
  - 兼容Mortal模型
  - 基于MJAI协议实现
- **技术特点**：
  - 被MahjongCopilot项目作为设计和功能实现的基础
  - 提供与雀魂游戏交互的能力
  - 支持获取和使用麻将AI模型
- **项目链接**：https://github.com/shinkuan/Akagi

## 项目关联
这两个项目存在明确的技术关联：MahjongCopilot是基于Akagi的设计和实现开发的雀魂AI助手应用，两者都基于Mortal模型并支持MJAI协议，共同构成了雀魂游戏AI辅助系统的完整解决方案。