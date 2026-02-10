# The Casino Mod

Only the boldest pilots dare to wager their fleets. This mod adds a high-stakes Casino to **all markets** regardless of faction or size.

## Features

### 1. Texas Hold'em Poker (Stargem Stakes)
Play standard No-Limit Texas Hold'em against the House AI using **Stargems**.

**The Currency: Stargems**
- Buy Gem Packages with Credits
- Purchase a **VIP Pass** (100,000 Credits) for 30 days of daily rewards
- Trade your ships directly for Stargems (valued at base hull value ÷ 1000, with 90% sell multiplier)
- Cash out Stargems back to Credits at a 1:1000 ratio

**Poker Details**
- Blinds: 50/100 Stargems
- Advanced AI that adapts to your play style and aggression patterns
- Text-based interface with colored card displays

### 2. Tachy-Impact (Gacha System)
Acquire rare ships through a rotating banner system.

- **Cost**: 160 Stargems per Pull
- **Pool Size**: 32 ships
- **Rotation**: Featured ships change every 14 in-game days

**The Capital (5★) - 0.6% Base Rate**
- Soft pity starts at 73 pulls (rate increases)
- Hard pity guarantees a 5★ at 90 pulls
- 50/50 chance to get the featured Capital

**The Cruiser (4★) - 5.1% Base Rate**
- Hard pity guarantees a 4★ at 10 pulls
- Standard pool contains random Destroyers, Frigates, and Weapons

### 3. Warp Core Arena (Spiral Abyss)
Battle with randomized ships in an elimination tournament.

- **Entry Fee**: 100 Stargems
- **Ships**: 5 ships per battle with random prefixes and affixes
- **Betting**: Bet on ships to survive. Base odds are 1:5.0
  - Positive perks (stronger ships) reduce odds by 25%
  - Negative perks (weaker ships) increase odds by 25%
  - Minimum odds: 1:2.0

**Dynamic Odds System (Mid-Battle Betting)**
- Odds change in real-time based on ship HP and battle round
- Higher HP = Lower odds (safer bet, smaller payout)
- Lower HP = Higher odds (riskier bet, bigger payout)
- HP factor range: 0.5x to 3.0x base odds
- Later bets have diminishing returns (20% reduction per round, minimum 40%)
- Your bet is locked at the odds shown when placed

**Performance Bonuses**
- Survival: +5% per turn survived
- Kills: +10% per kill
- House edge: ~10% (survival reward multiplier: 0.9x)

**Consolation Prizes**
- Defeated ships may earn consolation based on performance
- Consolation factor: 30% of calculated value

**Chaos Events** (10% chance per turn)
- Single Ship Damage: Deals 15% damage to one ship
- Multi Ship Damage: Deals 10% damage to multiple ships

**Strategy Tips**
- Bet early for maximum odds (before HP damage and diminishing returns)
- Look for wounded champions with good perks for high-value mid-battle bets
- Higher HP champions are safer but pay less; low HP = high risk, high reward

## Economic System

### Stargem Economy
- **Acquisition**: Buy gems through packages, convert ships, or earn through VIP daily rewards
- **Spending**: Used for poker stakes, gacha pulls, and arena entry fees
- **Conversion**: Gems can be converted back to credits at a fixed rate (1 Gem = 1000 Credits)

### Ship Trading
- Ships are valued at base hull value ÷ 1000 = Stargems
- Sell multiplier: 90% of calculated value (house edge)
- Smart sorting prioritizes lower-value ships first to preserve your expensive vessels

### VIP System
- **Cost**: 100,000 Credits for a 30-day pass
- **Daily Reward**: 100 Stargems per day
- **Interest Rates**: 2% daily for VIP members, 5% daily for non-VIP (applied when balance is negative)
- **Debt Ceiling**: Base 5,000 Stargems + 10,000 per VIP Pass purchased
- **Max Debt**: Cannot exceed 200% of your credit ceiling

## Configuration

You can customize many aspects of the mod by editing the configuration file:

**Location**: `data/config/casino_settings.json`

This JSON file allows you to adjust:
- Exchange rates and economy settings
- Gacha probabilities and pity thresholds
- Poker blinds and stakes
- Arena odds and ship stats
- VIP rewards and interest rates

Open the file in any text editor to see all available options and their current values.

## How to Play

1. Visit any TriTachyon or Player owned market in the game
2. Select "Visit Casino" from the market options
3. Top-up or buy a VIP pass for daily stargems
4. Choose your game: Poker, Tachy-Impact, or Arena
5. Manage your Stargems through the Financial Services menu


Good luck!