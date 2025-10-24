#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Mahjong Copilot Core Module
Responsible for Mahjong Soul data interception, analysis, and providing AI assistance
"""

import json
import sys
import os
import logging
import asyncio
import websockets
import argparse
import functools
from typing import Dict, Any, List, Optional

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('mahjong_analyzer.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


class MahjongCopilot:
    """Mahjong Copilot - Core analyzer for Mahjong Soul game data"""
    
    def __init__(self):
        self.game_state = None
        self.player_id = None
        self.analysis_results = {}
        logger.info("MahjongCopilot initialized")
    
    def update_game_state(self, game_state: Dict[str, Any]):
        """Update game state and perform analysis"""
        logger.info(f"Game data intercepted: {json.dumps(game_state, ensure_ascii=False)}")
        self.game_state = game_state
        
        # Perform game analysis
        self._analyze_game()
        
        return self.analysis_results
    
    def _analyze_game(self):
        """Analyze current game state"""
        # Reset analysis results
        self.analysis_results = {
            'tiles': {},
            'discards': {},
            'recommendations': [],
            'warnings': [],
            'score': 0,
            'game_stage': 'unknown'
        }
        
        if not self.game_state:
            logger.warning("No game state to analyze")
            return
        
        # Extract player hand tiles
        self._extract_player_tiles()
        
        # Extract discarded tiles
        self._extract_discards()
        
        # Analyze game stage
        self._analyze_game_stage()
        
        # Generate AI recommendations
        self._generate_recommendations()
        
    def _extract_player_tiles(self):
        """Extract player hand tiles"""
        try:
            if 'player_hand' in self.game_state:
                self.analysis_results['tiles'] = self.game_state['player_hand']
                logger.info(f"Player hand extracted: {self.game_state['player_hand']}")
        except Exception as e:
            logger.error(f"Error extracting player tiles: {e}")
    
    def _extract_discards(self):
        """Extract discarded tiles"""
        try:
            if 'discards' in self.game_state:
                self.analysis_results['discards'] = self.game_state['discards']
                logger.info(f"Discards extracted: {self.game_state['discards']}")
        except Exception as e:
            logger.error(f"Error extracting discards: {e}")
    
    def _analyze_game_stage(self):
        """Analyze game stage"""
        try:
            if 'round' in self.game_state:
                round_num = self.game_state['round']
                if round_num <= 3:
                    self.analysis_results['game_stage'] = 'early'
                elif round_num <= 8:
                    self.analysis_results['game_stage'] = 'middle'
                else:
                    self.analysis_results['game_stage'] = 'late'
                logger.info(f"Game stage determined: {self.analysis_results['game_stage']}")
        except Exception as e:
            logger.error(f"Error analyzing game stage: {e}")
    
    def _generate_recommendations(self):
        """Generate AI recommendations"""
        # Simplified recommendation logic, actual implementation would include
        # more complex Mahjong Soul rules and strategies
        recommendations = []
        warnings = []
        
        try:
            # Provide recommendations based on game stage
            stage = self.analysis_results['game_stage']
            
            if stage == 'early':
                recommendations.append("Early stage: Keep tile combinations, avoid chii/pon calls")
            elif stage == 'middle':
                recommendations.append("Middle stage: Adjust strategy based on game flow")
            else:
                recommendations.append("Late stage: Focus on defense, avoid giving up tiles")
            
            # Check for dangerous tiles
            if 'danger_tiles' in self.game_state:
                danger_tiles = self.game_state['danger_tiles']
                for tile in danger_tiles:
                    warnings.append(f"WARNING: Dangerous tile detected: {tile}")
                    logger.warning(f"Dangerous tile detected: {tile}")
            
            self.analysis_results['recommendations'] = recommendations
            self.analysis_results['warnings'] = warnings
            
            logger.info(f"Generated {len(recommendations)} recommendations and {len(warnings)} warnings")
        except Exception as e:
            logger.error(f"Error generating recommendations: {e}")
    
    def get_analysis_result(self) -> Dict[str, Any]:
        """Get analysis results"""
        return self.analysis_results


class GameDataServer:
    """Game data server for receiving Mahjong Soul game data"""
    
    def __init__(self, copilot: MahjongCopilot):
        self.copilot = copilot
        self.clients = set()
        self.is_running = False
        logger.info("GameDataServer initialized")
    
    async def handle_client(self, websocket, path):
        """Handle game data client connections"""
        logger.info(f"New game data client connected! WebSocket: {websocket}, Path: {path}")
        
        # Add client to set
        self.clients.add(websocket)
        logger.info(f"Game data client added. Total clients: {len(self.clients)}")
        
        try:
            logger.info("Starting to receive game data...")
            async for message in websocket:
                logger.info(f"Game data received: {message[:100]}..." if len(message) > 100 else f"Game data received: {message}")
                try:
                    # Try parsing JSON message
                    data = json.loads(message)
                    
                    # Process all message types from game
                    logger.info(f"Processing message of type: {data.get('type', 'unknown')}")
                    
                    # Handle different message types
                    if 'type' in data:
                        if data['type'] == 'game_state':
                            # Update game state and get analysis results
                            logger.info("Processing game state data...")
                            analysis_result = self.copilot.update_game_state(data.get('data', {}))
                            
                            # Send analysis results back
                            await self._send_analysis_result(websocket, analysis_result)
                        elif data['type'] == 'game_start':
                            logger.info("Game started!")
                            # Notify copilot about game start
                            self.copilot.update_game_state({'game_start': True})
                        elif data['type'] == 'game_end':
                            logger.info("Game ended!")
                            # Notify copilot about game end
                            self.copilot.update_game_state({'game_end': True})
                        else:
                            logger.debug(f"Received message type: {data['type']} with data: {data.get('data')}")
                    
                except json.JSONDecodeError:
                    logger.warning(f"Received non-JSON message: {message[:100]}..." if len(message) > 100 else f"Received non-JSON message: {message}")
                except Exception as e:
                    logger.error(f"Error processing game message: {e}")
                    import traceback
                    logger.error(f"Stack trace: {traceback.format_exc()}")
                    
        except Exception as e:
            logger.error(f"Error in game client handler: {e}")
            import traceback
            logger.error(f"Stack trace: {traceback.format_exc()}")
        finally:
            # Remove client from set
            self.clients.remove(websocket)
            logger.info(f"Game data client disconnected. Total clients: {len(self.clients)}")
    
    async def _send_analysis_result(self, websocket, result: Dict[str, Any]):
        """Send analysis results"""
        try:
            response = {
                'type': 'analysis_result',
                'data': result
            }
            await websocket.send(json.dumps(response))
            logger.info("Analysis results sent successfully")
        except Exception as e:
            logger.error(f"Error sending analysis result: {e}")
    
    async def start(self, host: str = 'localhost', port: int = 8888):
        """Start game data server"""
        logger.info(f"Starting game data server on {host}:{port}")
        
        # Use the same handler adaptation as BridgeServer
        async def handler(*args, **kwargs):
            logger.info(f"Game data server handler called with args: {args}, kwargs: {kwargs}")
            
            # Handle different cases for websocket library versions
            if len(args) >= 1:
                websocket = args[0]
                path = args[1] if len(args) >= 2 else '/'  
                logger.info(f"Calling handle_client with websocket: {websocket}, path: {path}")
                return await self.handle_client(websocket, path)
            
            logger.error(f"Not enough arguments for game data server handler: {len(args)} args provided")
            return None
        
        self.is_running = True
        logger.info("Game data server is ready to receive connections")
        
        async with websockets.serve(handler, host, port, ping_interval=None):
            logger.info("Game data server started successfully")
            await asyncio.Future()  # Run until cancelled
    
    def is_server_running(self):
        """Check if game data server is running"""
        return self.is_running


class BridgeServer:
    """Kotlin-Python Bridge Server"""
    
    def __init__(self, copilot: MahjongCopilot):
        self.copilot = copilot
        self.clients = set()
        logger.info("BridgeServer initialized")
    
    async def handle_client(self, websocket, path):
        """Handle client connections"""
        logger.info(f"New client connection received! WebSocket: {websocket}, Path: {path}")
        
        # Add client to set
        self.clients.add(websocket)
        logger.info(f"Client added to clients set. Total clients: {len(self.clients)}")
        
        try:
            logger.info("Starting to listen for messages from client...")
            async for message in websocket:
                logger.info(f"Bridge received message: {message[:100]}..." if len(message) > 100 else f"Bridge received message: {message}")
                
                try:
                    # Parse message
                    data = json.loads(message)
                    logger.info(f"Successfully parsed JSON message: {data}")
                    
                    # Process different message types
                    if data.get('type') == 'game_state':
                        logger.info("Processing game_state message")
                        # Analyze game state
                        analysis_result = self.copilot.update_game_state(data.get('data', {}))
                        
                        # Send analysis results
                        response = {
                            'type': 'analysis_result',
                            'data': analysis_result
                        }
                        await websocket.send(json.dumps(response))
                        logger.info("Sent analysis_result response")
                    
                    elif data.get('type') == 'get_analysis':
                        logger.info("Processing get_analysis message")
                        # Send current analysis results
                        response = {
                            'type': 'analysis_result',
                            'data': self.copilot.get_analysis_result()
                        }
                        await websocket.send(json.dumps(response))
                        logger.info("Sent analysis_result response")
                    
                    elif data.get('type') == 'connect':
                        logger.info(f"Received connect message from client: {data.get('data')}")
                        # Send connection acknowledgment
                        response = {
                            'type': 'connect_ack',
                            'data': 'connected_successfully'
                        }
                        await websocket.send(json.dumps(response))
                        logger.info("Sent connect_ack response")
                    
                    elif data.get('type') == 'fetch_data':
                        logger.info("Data fetching request received")
                        # This is where MahjongCopilot handles data fetching
                        response = {
                            'type': 'data_fetched',
                            'data': {
                                'status': 'fetching',
                                'message': 'MahjongCopilot is fetching game data...'
                            }
                        }
                        await websocket.send(json.dumps(response))
                        logger.info("Data fetching started")
                    
                    else:
                        logger.warning(f"Unknown message type: {data.get('type')}")
                        
                except json.JSONDecodeError:
                    logger.warning(f"Received non-JSON message: {message[:100]}..." if len(message) > 100 else f"Received non-JSON message: {message}")
                except Exception as e:
                    logger.error(f"Error processing bridge message: {e}")
                    import traceback
                    logger.error(f"Stack trace: {traceback.format_exc()}")
                    
        except Exception as e:
            logger.error(f"Error in bridge client handler: {e}")
            import traceback
            logger.error(f"Stack trace: {traceback.format_exc()}")
        finally:
            # Remove client from set
            self.clients.remove(websocket)
            logger.info(f"Client disconnected and removed from clients set. Total clients: {len(self.clients)}")
    
    async def start(self, host: str = 'localhost', port: int = 8765):
        """Start bridge server"""
        logger.info(f"Starting bridge server on {host}:{port}")
        
        # Fixed handler function to adapt to different websockets library versions
        async def handler(*args, **kwargs):
            logger.info(f"Handler called with args: {args}, kwargs: {kwargs}")
            
            # Handle different cases:
            # 1. Some versions may pass only one argument (websocket)
            # 2. Some versions may pass two arguments (websocket and path)
            if len(args) >= 1:
                websocket = args[0]
                # Use second argument if available, otherwise provide default path
                path = args[1] if len(args) >= 2 else '/'  
                logger.info(f"Calling handle_client with websocket: {websocket}, path: {path}")
                return await self.handle_client(websocket, path)
            
            logger.error(f"Not enough arguments for handler: {len(args)} args provided")
            return None
        
        async with websockets.serve(handler, host, port, ping_interval=None):
            logger.info("Bridge server started successfully")
            await asyncio.Future()  # 运行直到取消


async def main():
    """Main function"""
    # Parse command line arguments
    parser = argparse.ArgumentParser(description='Mahjong Copilot')
    parser.add_argument('--bridge-port', type=int, default=8765, help='Bridge server port')
    parser.add_argument('--game-port', type=int, default=8888, help='Game data server port')
    parser.add_argument('--debug', action='store_true', help='Enable debug logging')
    args = parser.parse_args()
    
    # Set logging level to DEBUG if debug mode is enabled
    if args.debug:
        logger.setLevel(logging.DEBUG)
    
    # Create MahjongCopilot instance
    copilot = MahjongCopilot()
    logger.info("Mahjong Copilot service started")
    logger.info("Data interception mode: Python-based MahjongCopilot")
    logger.info("UI panel disabled, only log output will be shown")
    logger.info(f"Starting game data server on port {args.game_port}")
    
    # Start bridge server for Kotlin communication
    bridge_server = BridgeServer(copilot)
    
    # Start game data server to receive game data from Kotlin
    game_server = GameDataServer(copilot)
    
    # Run both servers in parallel
    await asyncio.gather(
        bridge_server.start(port=args.bridge_port),
        game_server.start(port=args.game_port)
    )


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logger.info("Shutting down...")
    except Exception as e:
        logger.error(f"Fatal error: {e}")
        sys.exit(1)