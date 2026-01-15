# Changelog

## Version 1.0.1
- Fixed poker AI to adapt to player aggression patterns
- Added equity-based decision making for poker AI
- Implemented bet increase functionality in arena
- Added percentage-based custom bet amounts (10%, 30%, 50%, 70%, 100% of player's gem balance)
- Removed redundant CasinoMarketInteractionManager class
- Fixed "Cannot resolve symbol" error by removing unused import
- Improved consistency between custom bet amount menus in arena
- Fixed arena ship regeneration issue after changing bet amounts
- Fixed poker AI allowing fold when already all-in after multiple raises
- Added proper handling for JSON configuration loading for arena flavor texts
- Simplified architecture by following user preference for single-point solutions over distributed systems

## Version 1.0.0
- Initial Release ("The Casino Mod")
- Implemented Texas Hold'em Poker minigame.
- Implemented Spiral Abyss Arena (Spaceship Battle Royale).
- Added VIP Subscription system.