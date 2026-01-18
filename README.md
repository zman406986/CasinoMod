# The Casino Mod

Only the boldest pilots dare to wager their fleets. This mod adds a high-stakes Casino to **Tri-Tachyon** and **Player-owned** markets.

## Features

### 1. Texas Hold'em Poker (Stargem Stakes)
Play standard No-Limit Texas Hold'em against House AI using **Stargems**.
- **The Currency**: **Stargems**.
    - **Top-Up**: Buy Gem Packages (1980/3280/6480 gems) or **VIP Pass** (9999 Credits) which gives 100 Gems daily for 30 days.
    - **Trade Ships**: Convert ships directly to Stargems (Valued at 1 Gem = 100 Credits of Hull Value). Includes smart sorting.
- **Stakes**: Blinds are 100/200 Stargems.
- **Advanced AI**: The poker AI adapts to player aggression patterns, tracking consecutive raises and adjusting its strategy accordingly. It uses equity-based calculations to make more realistic decisions against bluff-heavy players.
- **Cash Out**: Convert Stargems back to Credits at a 1:100 ratio (1 Gem = 100 Credits).

### 2. Tachy-Impact
Acquire rare ships through a rotating banner system.
- **Cost**: **160 Stargems** per Pull.
- **Rotation**: The featured Capital and Cruisers change every **14 in-game days**.
- **The Capital (5★) - 0.6% Rate**
  - **Soft Pity**: Starts at 74 pulls.
  - **Hard Pity**: Guaranteed at **90 pulls**.
  - **50/50 Rule**: 50% chance to get the Featured Capital.
- **The Cruiser (4★) - 5.1% Rate**
  - **Hard Pity**: Guaranteed at **10 pulls**.
  - **Standard Pool**: Contains random Destroyers, Frigates, and Weapons.

### 3. Warp Core Arena (Spiral Abyss)
Battle with randomized ships in an elimination tournament.
- **Entry Fee**: 100 Stargems.
- **Betting System**: Bet on one or more ships to survive. Each ship has base odds (e.g., 1:2.0 means 2x return).
- **Performance Bonuses**: Survival and kills add proportional modifiers to your multiplier.
- **Champion Switching**: Switch champions during battle (costs 50% of current bet as fee, halves multiplier).
- **Chaos Events**: Solar Flare (reduces agility), Hull Breach (deals damage), Power Surge (doubles damage).
- **Ship Modifications**: Ships receive random prefixes and affixes that modify stats and affect odds.
- **Bet Management**: Players can bet on multiple ships and add bets during battle (later bets have reduced effectiveness).

## Internal Architecture

### Core Components

#### CasinoModPlugin
The main entry point of the mod that integrates with the game's lifecycle. It registers the casino interaction listener to markets that meet the size requirements. The plugin handles initialization and manages the connection between the game and the casino features. Redundant CasinoMarketInteractionManager class has been removed for cleaner architecture and simpler implementation as per user preference for single-point solutions over distributed systems.

#### CasinoInteraction
The primary UI controller that manages the interaction dialog with the player. It implements the state machine pattern to handle different modes of interaction:
- **Menu State**: Displays the main casino options
- **Poker State**: Manages the poker game interface
- **Gacha State**: Handles the banner pull interface
- **Arena State**: Controls the arena battle interface

The class manages the text panel content, option selection, and transitions between different game states.

#### CasinoConfig
Centralizes all configurable values for the casino mod, allowing for easy balancing without code changes. It loads settings from `data/config/casino_settings.json` and exposes them as static fields used throughout the mod. This includes economic parameters, gacha probabilities, poker rules, arena settings, and more.

#### CasinoVIPManager
Handles VIP subscription logic including:
- Managing VIP pass purchases
- Distributing daily rewards to VIP members
- Calculating debt interest
- Tracking VIP status and expiration dates

#### CasinoGachaManager
Manages the gacha system's persistent state and mechanics:
- **Persistence**: Stores data in `Global.getSector().getPersistentData()` under the key `"CasinoGachaData"`
- **Banner Rotation**: Checks timestamps to rotate featured ships every 14 in-game days
- **Probability Curves**: Implements soft pity (starting at 74 pulls) and hard pity (guaranteed at 90 pulls) for 5-star items
- **Pull Mechanics**: Manages the 50/50 guarantee system for featured capitals

#### PokerHandler
Implements the Texas Hold'em poker game logic:
- **Card Management**: Handles deck creation, shuffling, and dealing
- **Hand Evaluation**: Uses a 9-tier ranking system (High Card to Straight Flush) to evaluate hands
- **Game Flow**: Manages betting rounds (pre-flop, flop, turn, river)
- **Advanced AI**: Implements decision-making algorithms for the house AI based on hand strength, pot odds, and player aggression tracking. The AI now adapts to repeated bluffing/raising patterns from the player and adjusts its calling/raising frequency accordingly based on equity calculations.

#### SpiralAbyssArena
Controls arena battle system:
- **Team Generation**: Creates randomized teams with prefixed ships
- **Battle Simulation**: Runs turn-based combat between ships
- **Reward Calculation**: Determines payouts based on survival, kills, and ship odds
- **Modifier System**: Applies random prefixes and affixes to ships with stat modifications
- **Betting**: Players can bet on multiple ships, add bets during battle, and switch champions with penalties

### Economic System

The mod implements a dual-currency system with conversion mechanisms:

#### Stargem Economy
- **Acquisition**: Players can buy gems through packages or convert ships to gems
- **Spending**: Used for poker stakes, gacha pulls, and arena entry fees
- **Conversion**: Gems can be converted back to credits at a fixed rate

#### Ship Trading Mechanism
- **Valuation**: Ships are valued at 1 Gem per 10 Credits of hull value
- **Smart Sorting**: The system prioritizes lower-value ships first to preserve expensive vessels

### Market Integration

#### CasinoMarketInteractionListener
Monitors market interactions and adds casino options to qualifying markets:
- **Size Requirements**: Player markets need minimum size of 3, general markets need size 4
- **Faction Restrictions**: Only applies to specific factions (currently Tri-Tachyon)
- **Dynamic Options**: Adds casino-related options to the market interaction dialog

#### UI Panels
The CasinoUIPanels class manages the rendering of various interface elements:
- **Financial Panel**: Shows gem packages and ship trading options
- **Poker Interface**: Visual representation of cards, chips, and game state
- **Gacha Animation**: Banner pull animations and results display
- **Arena Display**: Battle visualization and team management

### Configuration Structure

All parameters are externalized to `data/config/casino_settings.json`:

```json
{
  "economy": {
    "stargemExchangeRate": 10.0,
    "shipTradeRate": 10.0,
    "vipPassCost": 9999,
    "vipPassDays": 30
  },
  "gacha": {
    "pullCost": 160,
    "prob5Star": 0.006,
    "prob4Star": 0.051,
    "pityHard5": 90,
    "pityHard4": 10,
    "pitySoftStart5": 74
  },
  "poker": {
    "smallBlind": 100,
    "bigBlind": 200,
    "raiseAmounts": [200, 400, 1000, 5000],
    "aiBluffChance": 0.1
  },
  "arena": {
    "entryFee": 100,
    "survivalRewardMult": 1.5,
    "shipCount": 8,
    "chaosEventChance": 0.15,
    "baseStats": {
      "frigate": { "hp": 56, "power": 25, "agility": 0.60 },
      "destroyer": { "hp": 84, "power": 29, "agility": 0.40 },
      "cruiser": { "hp": 112, "power": 33, "agility": 0.20 },
      "capital": { "hp": 140, "power": 40, "agility": 0.00 }
    }
  },
  "vipSystem": {
    "interestRate": 0.05,
    "debtHunterThreshold": -10000
  },
  "marketInteraction": {
    "minSizeForPlayerCasino": 3,
    "minSizeForGeneralCasino": 4
  }
}
```

This structure allows for easy balancing and customization without recompiling the mod.

### Persistence & State Management

The mod uses the game's built-in persistence system to maintain state between game sessions:
- **Gacha Data**: Tracks pity counters and banner rotation state
- **VIP Status**: Maintains VIP subscription information
- **Player Statistics**: Records win/loss records and achievements
- **Debt Tracking**: Manages player debt and interest calculations

The persistent data is stored in `Global.getSector().getPersistentData()` using unique keys to avoid conflicts with other mods.