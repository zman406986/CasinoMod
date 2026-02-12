# The Casino Mod

**Version:** 1.0.0  
**Game Version:** Starsector 0.98a-RC7

> *You might win, but the Interastral Peace Casino never loses.*

---

## Overview

The Casino Mod brings the high-stakes thrill of the Interastral Peace Corporation (IPC) to the Persean Sector. This mod adds a fully-featured casino experience with three distinct games, a unique currency system, VIP subscriptions, and even debt collection mechanics.

**Safe to add or remove mid-campaign!** The casino uses a separate currency (Stargems) that doesn't affect your save file integrity.

---

## Features

### Three Ways to Test Your Luck

| Game | Description | Cost |
|------|-------------|------|
| **Texas Hold'em Poker** | Classic No-Limit Hold'em against an adaptive AI opponent | Blinds: 50/100 Stargems |
| **Tachy-Impact** | Gacha system with rotating featured ships and pity mechanics | 160 Stargems per pull |
| **Spiral Abyss Arena** | Battle royale betting simulation with chaos events | Variable bet amounts |

### Currency System: Stargems

- **Exchange Rate:** 1 Stargem = 1,000 Credits
- **Top-up:** Purchase gem packages with credits
- **Ship Trading:** Convert ships directly to Stargems (base hull value ÷ 1000 × 0.9)
- **Cash Out:** Convert Stargems back to credits (ISP Tech Support experience included!)

### VIP Subscription

- **Cost:** 100,000 Credits for 30 days
- **Daily Reward:** 100 Stargems per day
- **Reduced Interest:** 0.5% daily debt interest (vs 1% for non-VIP)
- **Overdraft Access:** VIP-only credit facility for negative balances
- **Credit Ceiling Increase:** Each VIP purchase permanently raises your credit limit

### Debt & Collections

- **Credit Ceiling:** Based on player level and VIP purchases
- **Daily Interest:** Applied to negative balances
- **Corporate Reconciliation Team:** Debt collectors spawn for delinquent accounts
- **Maximum Debt:** 2× your credit ceiling

---

## Requirements

- **Starsector:** 0.98a-RC7
- **LunaLib:** v2.0.5 or higher (required for custom UI panels)

---

## Installation

1. Download the mod archive
2. Extract to your Starsector `mods` folder
3. Enable the mod in the game's mod menu
4. Start a new game or load an existing save

---

## Game Mechanics

### Texas Hold'em Poker

A fully-featured heads-up No-Limit Texas Hold'em implementation against an AI opponent.

**Gameplay:**
- 2 hole cards dealt to each player
- Betting rounds: Pre-flop → Flop (3 cards) → Turn (4th card) → River (5th card)
- Standard poker hand rankings apply

**AI Behavior:**
- **Adaptive Personality:** AI adjusts between TIGHT, AGGRESSIVE, and CALCULATED based on your playstyle
- **Player Tracking:** Monitors your VPIP (Voluntarily Put $ In Pot), PFR (Pre-Flop Raise), and aggression factor
- **Monte Carlo Simulation:** Uses 2000+ simulations for equity calculation
- **Hand Reading:** Estimates your hand range based on betting patterns
- **Anti-Exploitation:** Resists common AI abuse strategies like constant all-ins

**Blinds:**
- Calculated proportionally based on average stack size
- Small game (≤5000): BB = stack/80
- Medium game (≤20000): BB = stack/120
- Large game: BB = stack/200

---

### Tachy-Impact (Gacha)

A gacha system inspired by popular gacha games, featuring rotating banners and pity mechanics.

**Drop Rates:**
| Rarity | Type | Base Rate |
|--------|------|-----------|
| 5★ | Capital Ship | 0.6% |
| 4★ | Cruiser | 5.1% |
| 3★ | Destroyer/Frigate | 94.3% |

**Pity System:**
- **5★ Soft Pity:** Starts at pull 73, rate increases by 6% per pull
- **5★ Hard Pity:** Guaranteed at pull 90
- **4★ Hard Pity:** Guaranteed at pull 10
- **50/50 Rule:** 50% chance to get featured ship; losing triggers guaranteed featured on next 5★/4★

**Banner Rotation:**
- Rotates every 14 in-game days
- Features 1 capital ship and 3 cruisers
- Featured ships are promoted in Arena battles

**Ship Pool:**
- Configurable pool size (default: 32 ships)
- Excludes: Omega, Remnant, Derelict, boss ships, and special faction hulls
- Uses base hulls only (no skins/variants)

---

### Spiral Abyss Arena

A battle royale betting simulation where ships fight to the last one standing.

**How It Works:**
1. 5 ships are generated with random prefixes and affixes
2. Place bets on one or more ships before or during battle
3. Watch the automated combat unfold
4. Collect winnings based on your bet and the ship's odds

**Ship Stats:**
| Hull Size | HP | Power | Agility |
|-----------|-----|-------|---------|
| Frigate | 80 | 25 | 35% |
| Destroyer | 120 | 35 | 25% |
| Cruiser | 180 | 50 | 15% |
| Capital | 250 | 70 | 10% |

**Prefixes & Affixes:**
- **Positive:** Durable, Mighty, Swift, Fierce (buff stats)
- **Negative:** Brittle, Feeble, Sluggish, Timid (debuff stats)
- Affect HP, Power, Agility, and Bravery

**Odds Calculation:**
- Base odds: 1:5.0 (20% win chance)
- Monte Carlo simulation determines actual win probability
- House edge: ~10% built into all payouts
- Dynamic odds: Higher HP = lower payout, Lower HP = higher payout
- Mid-round betting: Penalties apply to prevent information exploitation

**Chaos Events:**
- **Single Ship Damage:** Maintenance accidents, asteroid impacts (15% HP)
- **Multi Ship Damage:** Collisions, chain reactions (10% HP to multiple ships)

**Rewards:**
- Win: Bet × Odds
- Survival Bonus: +5% per turn survived
- Kill Bonus: +10% per kill
- Defeated Consolation: 30% of calculated value

---

## Financial Services

### Stargem Top-up Packages

| Gems | Cost (Credits) |
|------|----------------|
| 60 | 15,000 |
| 300 | 60,000 |
| 980 | 170,000 |
| 1,980 | 350,000 |
| 3,280 | 580,000 |
| 6,480 | 1,150,000 |

### Ship Trading

Sell your ships for Stargems:
- **Formula:** Base Value ÷ 1000 × 0.9
- Ships are permanently transferred (cannot buy back)

### Cash Out

Convert Stargems back to credits through our "streamlined" customer support system:
- Experience authentic ISP tech support hold times
- Navigate automated phone menus
- Complete security verification (CAPTCHA)
- *Note: Cashout success not guaranteed*

---

## Configuration

All settings can be modified in `data/config/casino_settings.json`:

### VIP Settings
```json
"vipDailyReward": 100,
"vipPassDays": 30,
"vipDailyInterestRate": 0.005,
"normalDailyInterestRate": 0.01,
"baseDebtCeiling": 5000,
"ceilingIncreasePerVIP": 10000,
"vipPassCost": 30000
```

### Poker Settings
```json
"pokerSmallBlind": 50,
"pokerBigBlind": 100,
"pokerDefaultOpponentStack": 10000,
"pokerStackSizes": [1000, 2500, 5000, 10000],
"pokerMonteCarloSamples": 2000
```

### Gacha Settings
```json
"gachaCost": 160,
"gachaPoolSize": 32,
"pityHard5": 90,
"pitySoftStart5": 73,
"pityHard4": 10,
"gachaRotationDays": 14,
"prob5Star": 0.006,
"prob4Star": 0.051
```

### Arena Settings
```json
"arenaShipCount": 5,
"arenaBaseOdds": 5.0,
"arenaMinOdds": 1.01,
"arenaHouseEdge": 0.10,
"arenaChaosEventChance": 0.1,
"arenaSimulationCount": 500
```

### Ship Blacklist

Add hull IDs to `data/config/gacha_ships_blacklist.csv` to exclude them from the gacha pool. This file supports mod merging, so other mods can add their own exclusions.

---

## Technical Details

### Save Compatibility

- **Safe to add/remove mid-campaign**
- All data stored in player memory with `$ipc_` prefix
- No persistent data that would corrupt saves

### Memory Keys

| Key | Purpose |
|-----|---------|
| `$ipc_stargems` | Current balance |
| `$ipc_vip_start_time` | VIP subscription start |
| `$ipc_vip_duration` | VIP duration in days |
| `$ipc_cumulative_vip_purchases` | Total VIP passes bought |
| `$ipc_cumulative_topup_amount` | Total gems added |

### Architecture

```
CasinoModPlugin (Entry Point)
├── CasinoVIPManager (Balance, VIP, Daily Rewards)
├── CasinoDebtScript (Debt Collectors)
├── CasinoGachaManager (Gacha Pool & Pity)
└── CasinoInteraction (Main Dialog)
    ├── PokerHandler
    ├── GachaHandler
    ├── ArenaHandler
    ├── FinHandler
    ├── TopupHandler
    └── HelpHandler
```

### AI Implementation

The poker AI uses:
- Monte Carlo simulation for equity calculation
- Bayesian hand range estimation
- Player style classification (VPIP/PFR/AF)
- Personality adaptation (Tight/Aggressive/Calculated)
- Anti-gullibility mechanisms

---

## Accessing the Casino

Visit any market in the Sector and look for the **"Visit Private Lounge"** option. The casino is available at:
- Player-owned colonies (market size 4+)
- Tri-Tachyon markets (market size 3+)

---

## Disclaimer

*The IPC is an entertainment venue provided by Tri-Tachyon Strategic Investment Department. By participating, patrons acknowledge that:*

- *All games are designed for entertainment purposes only and are not designed to generate profitable returns.*
- *To ensure operational sustainability, the IPC may modify all terms and conditions at any time without prior notice.*
- *Negative balances may be subject to daily interest accrual and compulsory debt collection protocols.*

---

## Credits

- **Development:** Vibe-coded with AI assistance
- **Inspiration:** Genshin Impact gacha mechanics, Honkai: Star Rail
- **Framework:** Starsector Modding API, LunaLib

---

## License

See `Licences.txt` for details.
