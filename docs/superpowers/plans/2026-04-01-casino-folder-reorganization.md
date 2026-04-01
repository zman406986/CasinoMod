# Casino Folder Reorganization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize game-specific files from casino root into separate subfolders (arena/, blackjack/, gacha/, poker2/) and update all imports.

**Architecture:** Move files to game-specific packages, update package declarations, update imports in all dependent files.

**Tech Stack:** Java package refactoring, git mv for file moves.

---

### Task 1: Create new folder structure

**Files:**
- Create: `src/data/scripts/casino/arena/`
- Create: `src/data/scripts/casino/blackjack/`
- Create: `src/data/scripts/casino/gacha/`
- Create: `src/data/scripts/casino/poker2/`

- [ ] **Step 1: Create folders**

```bash
mkdir src/data/scripts/casino/arena
mkdir src/data/scripts/casino/blackjack
mkdir src/data/scripts/casino/gacha
mkdir src/data/scripts/casino/poker2
```

- [ ] **Step 2: Verify folders exist**

Run: `ls src/data/scripts/casino/`
Expected: arena, blackjack, cards, gacha, interaction, poker2, poker5, shared folders visible

---

### Task 2: Move Arena files

**Files:**
- Move: `ArenaDialogDelegate.java` → `arena/`
- Move: `ArenaPanelUI.java` → `arena/`
- Move: `SpiralAbyssArena.java` → `arena/`
- Modify: `arena/*.java` - package declarations
- Modify: `interaction/ArenaHandler.java` - imports

- [ ] **Step 1: Move arena files**

```bash
git mv src/data/scripts/casino/ArenaDialogDelegate.java src/data/scripts/casino/arena/
git mv src/data/scripts/casino/ArenaPanelUI.java src/data/scripts/casino/arena/
git mv src/data/scripts/casino/SpiralAbyssArena.java src/data/scripts/casino/arena/
```

- [ ] **Step 2: Update ArenaDialogDelegate.java package**

Change line 1:
```java
package data.scripts.casino.arena;
```

Add imports for cross-package dependencies:
```java
import data.scripts.casino.shared.BaseGameDelegate;
import data.scripts.casino.Strings;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
```

- [ ] **Step 3: Update ArenaPanelUI.java package**

Change line 1:
```java
package data.scripts.casino.arena;
```

Add imports:
```java
import data.scripts.casino.shared.BaseCardGamePanelUI;
import data.scripts.casino.cards.*;
import data.scripts.casino.Strings;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.gacha.CasinoGachaManager;
```

Change inner class reference: `SpiralAbyssArena.SpiralGladiator` stays as-is (same package).

- [ ] **Step 4: Update SpiralAbyssArena.java package**

Change line 1:
```java
package data.scripts.casino.arena;
```

Add imports:
```java
import data.scripts.casino.cards.*;
import data.scripts.casino.Strings;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.gacha.CasinoGachaManager;
import data.scripts.casino.shared.CasinoFinancials;
```

- [ ] **Step 5: Update ArenaHandler.java imports**

Change imports from:
```java
import data.scripts.casino.ArenaDialogDelegate;
import data.scripts.casino.ArenaPanelUI;
import data.scripts.casino.SpiralAbyssArena;
import data.scripts.casino.CasinoGachaManager;
```

To:
```java
import data.scripts.casino.arena.ArenaDialogDelegate;
import data.scripts.casino.arena.ArenaPanelUI;
import data.scripts.casino.arena.SpiralAbyssArena;
import data.scripts.casino.gacha.CasinoGachaManager;
```

---

### Task 3: Move Blackjack files

**Files:**
- Move: `BlackjackDialogDelegate.java` → `blackjack/`
- Move: `BlackjackGame.java` → `blackjack/`
- Move: `BlackjackPanelUI.java` → `blackjack/`
- Modify: `blackjack/*.java` - package declarations
- Modify: `interaction/BlackjackHandler.java` - imports

- [ ] **Step 1: Move blackjack files**

```bash
git mv src/data/scripts/casino/BlackjackDialogDelegate.java src/data/scripts/casino/blackjack/
git mv src/data/scripts/casino/BlackjackGame.java src/data/scripts/casino/blackjack/
git mv src/data/scripts/casino/BlackjackPanelUI.java src/data/scripts/casino/blackjack/
```

- [ ] **Step 2: Update BlackjackDialogDelegate.java package**

Change line 1:
```java
package data.scripts.casino.blackjack;
```

Add imports:
```java
import data.scripts.casino.shared.BaseGameDelegate;
import data.scripts.casino.Strings;
```

- [ ] **Step 3: Update BlackjackGame.java package**

Change line 1:
```java
package data.scripts.casino.blackjack;
```

Add imports:
```java
import data.scripts.casino.cards.*;
```

- [ ] **Step 4: Update BlackjackPanelUI.java package**

Change line 1:
```java
package data.scripts.casino.blackjack;
```

Add imports:
```java
import data.scripts.casino.shared.BaseCardGamePanelUI;
import data.scripts.casino.cards.*;
import data.scripts.casino.Strings;
```

- [ ] **Step 5: Update BlackjackHandler.java imports**

Change imports from:
```java
import data.scripts.casino.BlackjackDialogDelegate;
import data.scripts.casino.BlackjackGame;
import data.scripts.casino.BlackjackGame.Action;
import data.scripts.casino.BlackjackGame.GameState;
```

To:
```java
import data.scripts.casino.blackjack.BlackjackDialogDelegate;
import data.scripts.casino.blackjack.BlackjackGame;
import data.scripts.casino.blackjack.BlackjackGame.Action;
import data.scripts.casino.blackjack.BlackjackGame.GameState;
```

---

### Task 4: Move Gacha files

**Files:**
- Move: `CasinoGachaManager.java` → `gacha/`
- Move: `GachaAnimation.java` → `gacha/`
- Move: `GachaAnimationDialogDelegate.java` → `gacha/`
- Modify: `gacha/*.java` - package declarations
- Modify: `interaction/GachaHandler.java` - imports
- Modify: `shared/GachaUI.java` - imports (if it references GachaAnimation)

- [ ] **Step 1: Move gacha files**

```bash
git mv src/data/scripts/casino/CasinoGachaManager.java src/data/scripts/casino/gacha/
git mv src/data/scripts/casino/GachaAnimation.java src/data/scripts/casino/gacha/
git mv src/data/scripts/casino/GachaAnimationDialogDelegate.java src/data/scripts/casino/gacha/
```

- [ ] **Step 2: Update CasinoGachaManager.java package**

Change line 1:
```java
package data.scripts.casino.gacha;
```

Add imports:
```java
import data.scripts.casino.Strings;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.shared.CasinoFinancials;
```

- [ ] **Step 3: Update GachaAnimation.java package**

Change line 1:
```java
package data.scripts.casino.gacha;
```

Add imports (check actual file for dependencies):
```java
import data.scripts.casino.cards.*;
import data.scripts.casino.Strings;
```

- [ ] **Step 4: Update GachaAnimationDialogDelegate.java package**

Change line 1:
```java
package data.scripts.casino.gacha;
```

Add imports:
```java
import data.scripts.casino.shared.BaseGameDelegate;
import data.scripts.casino.Strings;
```

- [ ] **Step 5: Update GachaHandler.java imports**

Change imports from:
```java
import data.scripts.casino.CasinoGachaManager;
import data.scripts.casino.GachaAnimation;
import data.scripts.casino.GachaAnimationDialogDelegate;
```

To:
```java
import data.scripts.casino.gacha.CasinoGachaManager;
import data.scripts.casino.gacha.GachaAnimation;
import data.scripts.casino.gacha.GachaAnimationDialogDelegate;
```

- [ ] **Step 6: Update shared/GachaUI.java imports**

If it imports `GachaAnimation`, change to:
```java
import data.scripts.casino.gacha.GachaAnimation;
```

---

### Task 5: Move Poker (2-player) files

**Files:**
- Move: `PokerDialogDelegate.java` → `poker2/`
- Move: `PokerGame.java` → `poker2/`
- Move: `PokerOpponentAI.java` → `poker2/`
- Move: `PokerPanelUI.java` → `poker2/`
- Modify: `poker2/*.java` - package declarations
- Modify: `interaction/PokerHandler.java` - imports

- [ ] **Step 1: Move poker files**

```bash
git mv src/data/scripts/casino/PokerDialogDelegate.java src/data/scripts/casino/poker2/
git mv src/data/scripts/casino/PokerGame.java src/data/scripts/casino/poker2/
git mv src/data/scripts/casino/PokerOpponentAI.java src/data/scripts/casino/poker2/
git mv src/data/scripts/casino/PokerPanelUI.java src/data/scripts/casino/poker2/
```

- [ ] **Step 2: Update PokerDialogDelegate.java package**

Change line 1:
```java
package data.scripts.casino.poker2;
```

Add imports:
```java
import data.scripts.casino.shared.BaseGameDelegate;
import data.scripts.casino.Strings;
import data.scripts.casino.interaction.PokerHandler;
```

- [ ] **Step 3: Update PokerGame.java package**

Change line 1:
```java
package data.scripts.casino.poker2;
```

Add imports:
```java
import data.scripts.casino.cards.*;
```

- [ ] **Step 4: Update PokerOpponentAI.java package**

Change line 1:
```java
package data.scripts.casino.poker2;
```

Add imports (check actual file for dependencies):
```java
import data.scripts.casino.cards.*;
```

- [ ] **Step 5: Update PokerPanelUI.java package**

Change line 1:
```java
package data.scripts.casino.poker2;
```

Add imports:
```java
import data.scripts.casino.shared.BaseCardGamePanelUI;
import data.scripts.casino.cards.*;
import data.scripts.casino.Strings;
```

- [ ] **Step 6: Update PokerHandler.java imports**

Change imports from:
```java
import data.scripts.casino.PokerDialogDelegate;
import data.scripts.casino.PokerGame;
import data.scripts.casino.PokerOpponentAI;
```

To:
```java
import data.scripts.casino.poker2.PokerDialogDelegate;
import data.scripts.casino.poker2.PokerGame;
import data.scripts.casino.poker2.PokerOpponentAI;
```

---

### Task 6: Update cross-package references in shared/

**Files:**
- Check: `shared/GachaUI.java` for GachaAnimation import
- Check: `shared/CardRenderingUtils.java` for any game-specific imports
- Check: `shared/BaseCardGamePanelUI.java` for any game-specific imports

- [ ] **Step 1: Check and update shared/GachaUI.java**

Read file and update any imports of moved classes.

- [ ] **Step 2: Check other shared files**

Verify no game-specific imports exist in shared files.

---

### Task 7: Verify and commit

**Files:**
- All modified files

- [ ] **Step 1: Verify folder structure**

Run: `ls -R src/data/scripts/casino/`
Expected: arena/, blackjack/, cards/, gacha/, interaction/, poker2/, poker5/, shared/ plus root files (CasinoConfig, CasinoDebtScript, CasinoVIPManager, Strings)

- [ ] **Step 2: Verify package declarations**

Grep for old package declarations:
```bash
grep -r "package data.scripts.casino;" src/data/scripts/casino/arena/
grep -r "package data.scripts.casino;" src/data/scripts/casino/blackjack/
grep -r "package data.scripts.casino;" src/data/scripts/casino/gacha/
grep -r "package data.scripts.casino;" src/data/scripts/casino/poker2/
```
Expected: No matches (all should have subpackage)

- [ ] **Step 3: Verify imports in handlers**

Grep for old import patterns:
```bash
grep "import data.scripts.casino.Arena" src/data/scripts/casino/interaction/
grep "import data.scripts.casino.Blackjack" src/data/scripts/casino/interaction/
grep "import data.scripts.casino.Gacha" src/data/scripts/casino/interaction/
grep "import data.scripts.casino.Poker" src/data/scripts/casino/interaction/
```
Expected: No matches (all should import from subpackages)

- [ ] **Step 4: Commit all changes**

```bash
git add -A
git commit -m "refactor: organize game files into separate packages

- Move arena files to arena/ package
- Move blackjack files to blackjack/ package  
- Move gacha files to gacha/ package
- Move poker (2-player) files to poker2/ package
- Update all imports accordingly"
```

---

### Task 8: Final verification

- [ ] **Step 1: Run git status**

Run: `git status`
Expected: Clean working tree

- [ ] **Step 2: Verify file count**

Run: `find src/data/scripts/casino -name "*.java" | wc -l`
Expected: Same count as before (no files lost)