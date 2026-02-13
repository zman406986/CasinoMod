# Interastral Peace Casino

A Starsector mod adding a casino experience with poker, gacha, and arena betting.

**Version:** 0.1.0  
**Game Version:** Starsector 0.98a-RC7

## Features

- **Texas Hold'em Poker** - No-Limit Hold'em against adaptive AI
- **Tachy-Impact** - Gacha system with pity mechanics
- **Spiral Abyss Arena** - Battle royale betting simulation
- **Stargem Currency** - Exchange credits for casino currency
- **VIP System** - Subscription with daily rewards

## Requirements

- Starsector 0.98a-RC7

## Installation

1. Extract to `starsector/mods/Casino`
2. Enable in game mod menu
3. Safe to add/remove mid-campaign

## Configuration

Edit `data/config/casino_settings.json` to customize:
- VIP costs and rewards
- Poker blinds and stack sizes
- Gacha rates and pity thresholds
- Arena odds and chaos events

Add hull IDs to `data/config/gacha_ships_blacklist.csv` to exclude ships from gacha pool.

## Architecture

```
CasinoModPlugin
├── CasinoMusicPlugin
├── CasinoConfig
├── CasinoVIPManager
├── CasinoDebtScript
├── CasinoGachaManager
├── CasinoMarketInteractionListener
├── PokerGame
├── SpiralAbyssArena
├── GachaAnimation
├── GachaAnimationDialogDelegate
└── CasinoInteraction
    ├── OptionHandler
    ├── PokerHandler
    ├── GachaHandler
    ├── ArenaHandler
    ├── FinHandler
    ├── TopupHandler
    └── HelpHandler
```

## License

See `Licences.txt`.
