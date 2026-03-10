# Interastral Peace Casino

A Starsector mod adding a casino experience with poker, gacha, and arena betting.

**Version:** 1.1.0  
**Game Version:** Starsector 0.98a-RC7

## Features

### Texas Hold'em Poker
Full No-Limit Texas Hold'em against an adaptive AI opponent:
- Monte Carlo-based AI decision making (2000 simulation samples)
- Multiple AI personalities (Tight, Aggressive, Calculated)
- Proportional blind sizing based on stack sizes
- Full side pot support for all-in situations
- Suspend/resume functionality - save and continue games later
- Overdraft support for VIP players
- Visual UI panel with card display
- Anti-exploitation measures (raise spiral prevention, pot commitment detection)

### Tachy-Impact Gacha System
Ship gacha pulling with pity mechanics:
- 160 Stargems per pull
- 5-star pity guaranteed at 90 pulls (0.6% base rate)
- 4-star pity guaranteed at 10 pulls (5.1% base rate)
- Soft pity system starting at pull 73 (increasing rates)
- 50/50 featured ship mechanic
- Ship pool rotation every 14 days (32 ships per pool)
- Convert unwanted ships back to Stargems
- Visual spinning animation with star-shaped cards
- CSV-based ship blacklist support

### Spiral Abyss Arena
Battle royale betting simulation:
- Bet on 5 competing ships with randomized prefixes/affixes
- Monte Carlo-based odds calculation (500 simulations)
- Dynamic odds that update based on HP and round progression
- Mid-round betting with diminishing returns penalty
- Position-based consolation rewards for defeated ships
- Chaos events (random damage to ships)
- Kill bonuses and survival bonuses
- 10% house edge on all bets
- Visual UI with ship sprites and live battle log
- Suspend/resume functionality

### VIP System
30-day VIP subscription with exclusive benefits:
- Daily reward: 100 Stargems
- Overdraft access (spend beyond your balance)
- Reduced interest rate: 0.5% daily (vs 1% for non-VIP)
- Increased credit ceiling: +10,000 per VIP purchase
- Monthly or daily notification modes
- Cost: 30,000 credits

### Debt & Credit System
Comprehensive credit and debt management:
- Base credit ceiling: 5,000 Stargems
- Ceiling increases with player level (+1,000 per level)
- Daily interest accrual on negative balances
- Maximum debt = 2x credit ceiling
- Corporate Reconciliation Team (debt collector fleets)
- Fleet spawns when debt exceeds ceiling
- 90% threshold warning system

### Financial Services
- Sell ships for Stargems (90% of base value)
- Stargem packages (60 to 6,480 gems)
- Satirical "Cash Out" flow with CAPTCHA verification

## Requirements

- Starsector 0.98a-RC7

## Installation

1. Extract to `starsector/mods/Casino`
2. Enable in the game's mod menu
3. Safe to add/remove mid-campaign

## Configuration

Edit `data/config/casino_settings.json` to customize:

### VIP Settings
- `vipDailyReward`: Daily gems for VIP (default: 100)
- `vipPassDays`: VIP duration in days (default: 30)
- `vipDailyInterestRate`: VIP interest rate (default: 0.005 = 0.5%)
- `normalDailyInterestRate`: Non-VIP interest rate (default: 0.01 = 1%)
- `vipPassCost`: Credits for VIP pass (default: 30000)

### Poker Settings
- `pokerSmallBlind`: Small blind amount (default: 50)
- `pokerBigBlind`: Big blind amount (default: 100)
- `pokerDefaultOpponentStack`: AI starting stack (default: 10000)
- `pokerMonteCarloSamples`: AI simulation samples (default: 2000)

### Gacha Settings
- `gachaCost`: Gems per pull (default: 160)
- `gachaPoolSize`: Ships in pool (default: 32)
- `pityHard5`: Hard pity for 5-star (default: 90)
- `pitySoftStart5`: Soft pity start (default: 73)
- `pityHard4`: Hard pity for 4-star (default: 10)
- `prob5Star`: 5-star base rate (default: 0.006)
- `prob4Star`: 4-star base rate (default: 0.051)

### Arena Settings
- `arenaShipCount`: Ships per match (default: 5)
- `arenaBaseOdds`: Base payout odds (default: 5.0)
- `arenaMinOdds`: Minimum odds (default: 1.01)
- `arenaHouseEdge`: House edge percentage (default: 0.10)
- `arenaEntryFee`: Default bet amount (default: 100)
- `arenaSimulationCount`: Monte Carlo simulations (default: 500)
- `arenaMaxBetPerChampion`: Max bet per ship (default: 10000)

Add hull IDs to `data/config/gacha_ships_blacklist.csv` to exclude ships from the gacha pool.

## Architecture

```
CasinoModPlugin
├── CasinoMusicPlugin
├── CasinoConfig              # Configuration loader
├── CasinoVIPManager          # VIP/balance/credit management
├── CasinoDebtScript          # Debt collector fleet spawning
├── CasinoGachaManager        # Gacha pool and pity mechanics
├── PokerGame                 # Poker logic and AI
├── PokerPanelUI              # Poker visual UI
├── PokerDialogDelegate       # Poker dialog handler
├── SpiralAbyssArena          # Arena simulation
├── ArenaPanelUI              # Arena visual UI
├── ArenaDialogDelegate       # Arena dialog handler
├── GachaAnimation            # Gacha animation
├── GachaAnimationDialogDelegate
└── CasinoInteraction         # Main dialog router
    ├── OptionHandler         # Handler interface
    ├── PokerHandler          # Poker menu handler
    ├── GachaHandler          # Gacha menu handler
    ├── ArenaHandler          # Arena menu handler
    ├── FinHandler            # Financial services
    ├── TopupHandler          # Stargem purchase
    └── HelpHandler           # Help screens
```

## Save Data

All persistent data is stored in player memory using the following keys:
- `$ipc_stargems` - Player Stargem balance
- `$ipc_vip_start_time` - VIP subscription start timestamp
- `$ipc_cumulative_vip_purchases` - Total VIP passes purchased
- `$ipc_suspended_game_type` - Suspended game state
- `$ipc_poker_*` - Various poker state keys
- `$ipc_arena_*` - Various arena state keys
- `$ipc_debt_collector_*` - Debt collector state

## License

See `Licences.txt`.
