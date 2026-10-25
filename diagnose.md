# Performance Diagnosis: 10 FPS on Main Menu & Combat

## Symptom
Adding this mod causes FPS to drop from ~60 to ~10 on **main menu** and **combat**. Removing the mod restores 60 FPS immediately. Both game states are affected.

---

## What I Investigated

### 1. Per-frame Java code paths (advance/renderBelow)

**Files examined:**
- `CasinoModPlugin.java` — only `onApplicationLoad()` + `onGameLoad()`, no per-frame code
- `CasinoVIPManager.java` — `EveryFrameScript` with `advance()`, throttled to 1/sec
- `CasinoDebtScript.java` — `EveryFrameScript` with `advance()`, runs every frame
- `CasinoInteraction.java` — `advance()` is empty
- `ArenaDialogDelegate.java` — `advance()` calls `arenaPanel.advance()` if `!finished`
- `BlackjackDialogDelegate.java`, `PokerDialogDelegate.java`, etc. — empty or delegate to panel
- `BaseGameDelegate.java` — `advance()` is empty
- `ArenaPanelUI.java` — `advance()` + `renderBelow()` (animation timers, sprite rendering)
- `GachaAnimation.java` — `advance()` + `renderBelow()` (reel animations, particles)
- `BlackjackPanelUI.java`, `PokerPanelUI.java`, `PokerPanelUI5.java` — card animation `advance()` + `renderBelow()`
- `ShipPickerPanelUI.java` — `renderBelow()`

**Ruling:**
- `CasinoVIPManager` and `CasinoDebtScript` are `EveryFrameScript` instances added via `addTransientScript()` in `onGameLoad()`. They run in the **campaign layer only** — NOT on main menu (no game loaded) and NOT in combat (separate engine layer). They cannot be the cause of main menu / combat FPS drops.
- All panel UI classes (`ArenaPanelUI`, `GachaAnimation`, poker/blackjack panels) only have their `advance()` and `renderBelow()` called when their visual dialog/panel is **active**. They are properly cleaned up when the dialog is dismissed. They do not run on main menu or combat.

### 2. Static initializers / class loading

**Files examined:**
- `CasinoConfig.java` — `static {}` populates `ARENA_BASE_STATS` (4 enum entries)
- `CardSprites.java` — 64 `static final SpriteAPI` fields, each calling `settings.getSprite("card", "...")`
- `CardRenderingUtils.java` — `static final SpriteAPI POKER_TABLE = settings.getSprite("poker", "table")`
- `BaseCardGamePanelUI.java` — `static final SettingsAPI settings = Global.getSettings()`
- `CasinoSpriteCache.java` — `static final SettingsAPI settings = Global.getSettings()` + `HashMap`
- `Strings.java` — `static { load(); }` loads `strings.json` via `Global.getSettings().loadJSON()`

**Ruling:**
- `CardSprites` has 64 `static final SpriteAPI` calls. When this class loads, it triggers 64 texture lookups (≈10.5 MB VRAM total). However, `CardSprites` is only loaded when a card-game panel is first used (during casino interaction), NOT at game startup.
- `CardRenderingUtils` has 1 static sprite lookup (`POKER_TABLE`). Same loading-on-demand behavior.
- `CasinoConfig` static initializer is triggered by `CasinoModPlugin.onApplicationLoad()` but is trivial (4 enum entries in a HashMap).
- `CasinoSpriteCache` and `BaseCardGamePanelUI` have static `SettingsAPI` references that just call `Global.getSettings()` — a singleton accessor.
- `Strings` has `static { load(); }` — loads strings.json on class initialization. However, `Strings` is only loaded on first access by per-frame scripts or interaction handlers. On main menu and combat, `Strings` is **never loaded** — no code path reaches it. **Ruled out.**
- **Conclusion**: No static initializer runs heavy work at startup except `CasinoConfig.loadSettings()`.

### 3. Mod info flags

**File: `mod_info.json`**
- `"utility":"true"` — Note: this is a **JSON string**, not a boolean. The correct format is `"utility": true` (no quotes around true).
- If the game uses `optBoolean("utility")` internally, a string `"true"` would return the default (`false`), meaning the mod is NOT recognized as a utility mod.
- This could affect data merge order or plugin lifecycle, but both utility and non-utility mods have their `onApplicationLoad()` called at startup regardless. The difference is subtle (e.g., utility mod data is merged first).
- **Ruling:** Worth fixing (`"utility":true` without quotes) but unlikely to cause 10 FPS by itself.

**File: `interastral_peace_casino.version`**
- Declares `"starsectorVersion":"0.98a-RC8"` but `mod_info.json` says `"gameVersion": "0.98a-RC7"`. The player runs RC7. Mismatch is minor (version file is for update checking, not mod validation).
- **Ruling:** Not related to FPS.

### 4. Settings (data/config/settings.json)

**File: `data/config/settings.json`**
- Defines **75 sprite paths** nested under `"graphics"` → `"card"` (74 entries) / `"poker"` (1 entry)
- This file gets **merged** into the game's global settings system at startup
- These entries are accessible via `settings.getSprite("card", "...")` etc.

**Why this is WEAKER than originally suspected:**
- 75 sprite paths is a modest number. Many mods register 100+ sprites without FPS issues.
- The nested structure (`"card"` as a sub-object) is handled natively by Starsector's settings merger.
- `getSprite()` is a one-time lookup that creates a cached SpriteAPI object — not a per-frame operation.
- If the game does iterate over all graphics entries per frame (e.g., for cache management or atlas processing), 75 entries would add negligible overhead — nowhere near the ~67ms per frame needed to drop 60→10 FPS.

**Why it's still a suspect (the only one that touches both main menu and combat):**
- It's the only mod artifact that affects BOTH rendering contexts.
- If the settings merger does something unexpected with nested objects (e.g., deep-walks leaf nodes for path validation every render pass), 75 extra leaf nodes could add overhead.
- **Verdict downgraded: WEAK SUSPECT.** Poor explanation for the severity of the FPS drop.

### 5. Graphics assets

**Files examined:** All PNG files in `graphics/`
- 56 card face images: 169×244 px each (very small)
- 15 card back images: 169×244 px each
- 1 poker table: 878×415 px
- 4 suit icons: 15×15 px
- Total VRAM ≈ 11 MB

**Ruling:** Texture sizes are very small. Not a VRAM or cache-pressure issue.

### 6. Sound assets

**Files examined:** All OGG files in `sounds/`
- `casino_theme.ogg`: 1.4 MB
- SFX files: 5–31 KB each

**Ruling:** Normal sizes. Sound is not preloaded at startup. Music registration (`sounds.json`) merely indexes the track for later use via `playCustomMusic()`. No per-frame impact.

### 7. Other data files (CSV, JSON)

- `data/config/casino_settings.json` — 102 lines, game balance config, read once at startup via `Global.getSettings().loadJSON()`. Parses ~80 key-value pairs. **Ruled out** (~20ms startup cost).
- `data/config/gacha_ships_blacklist.csv` — 6 ship IDs, read once at startup via `getMergedSpreadsheetDataForMod()`. **Ruled out**.
- `data/config/sounds.json` — 14 lines, music registration. **Ruled out**.
- `data/campaign/rules.csv` — 33 lines, campaign dialog rules with specific triggers (`PopulateOptions`, `DialogOptionSelected`, `BeginFleetEncounter`, `OpenCommLink`). These are **event-driven, not per-frame**. Only evaluated when campaign events fire. **Ruled out for main menu/combat.**
- `data/console/commands.csv` — 3 entries, console commands. **Ruled out.**
- `data/config/version/version_files.csv` — 2 lines, version checker reference. **Ruled out.**

### 8. OpenGL state management

`CardRenderingUtils.java` and panel UI classes use `GL11.glBegin/glEnd`, `GL11.glEnable/glDisable(GL_TEXTURE_2D)`, and other immediate-mode OpenGL calls. These only execute when a casino panel is rendering, not on main menu / combat. However, if a panel fails to restore OpenGL state before being dismissed, it could corrupt subsequent rendering.

**Ruling:** Can't be the initial cause (user sees 10 FPS before any casino interaction), but could exacerbate issues if panels were opened first.

### 9. JVM / GC interaction

The user runs Java 17 with:
- `-XX:+UseShenandoahGC -XX:ShenandoahGCMode=iu` (aggressive GC)
- `-Xmx4396m -Xms4396m` (4.4 GB heap)
- `-XX:+AlwaysPreTouch` (pre-commit all heap pages)
- `-XX:ShenandoahGuaranteedGCInterval=0` (no minimum GC interval)
- `-XX:+PrintCodeCache` (prints code cache state on changes)
- `-noverify` + `-XX:-BytecodeVerificationLocal -XX:-BytecodeVerificationRemote`

**Analysis:**
- The JVM config is extremely aggressive. `ShenandoahGCMode=iu` + `compact` heuristics + `GuaranteedGCInterval=0` means the GC runs as aggressively as needed to meet `MaxGCPauseMillis=10`.
- Class loading for the 177-class JAR (435 KB) triggers JIT compilation. With `-XX:+PrintCodeCache`, each compilation event prints output (one-time at startup, not per-frame).
- The mod adds no per-frame allocation on main menu or combat, so GC should not be triggered continuously.
- 11 MB of textures on a 4.4 GB heap = 0.25% pressure. Negligible.
- **Ruling:** Unlikely cause, but the extreme GC config means even a tiny allocation pattern could trigger GC cycles. However, no such allocation pattern exists from this mod on main menu/combat.

### 10. JAR class count and class loading chain

**Files examined:** `jars/Casino.jar` — 435 KB, 177 class files.

**Class loading analysis:**
- At startup (`onApplicationLoad`), only `CasinoModPlugin` (loaded by game) and `CasinoConfig` (called explicitly) are loaded.
- `CasinoConfig` static init populates `ARENA_BASE_STATS` using `HullSize` enum (already loaded by base game). `loadSettings()` loads a JSON file and CSV. No chain-loading of other mod classes.
- `Strings.java` has `static { load(); }` but is NOT loaded at startup — no code path reaches it until a casino interaction or per-frame script fires.
- `CasinoSpriteCache` has static `SettingsAPI` access — singleton accessor, only loaded when arena/gacha/ship-picker panels are used.
- All 177 classes are on the classpath but only ~5 are ever loaded during main menu / combat.
- **Ruling:** No class loading cascade. JIT compilation of 177 classes happens lazily on demand, not at startup.

### 11. rulecmd package namespace

**File:** `Casino_StartCasinoInteraction.java` lives in `com.fs.starfarer.api.impl.campaign.rulecmd` — the game's own package namespace.

**Analysis:** This is standard practice in Starsector for rule command classes. The `-noverify` flag and `-XX:-BytecodeVerificationLocal -XX:-BytecodeVerificationRemote` disable bytecode verification. No performance impact from namespace placement.

**Ruling:** Standard pattern. Ruled out.

---

## Summary of Findings

| Suspect | Main Menu? | Combat? | Verdict |
|---|---|---|---|
| `EveryFrameScript` (VIP, Debt) | No (not loaded) | No (paused in combat) | **Ruled out** |
| Panel UI advance/renderBelow | No (not active) | No (not active) | **Ruled out** |
| Static initializers (incl. Strings) | No (not triggered) | No (not triggered) | **Ruled out** |
| Data files (CSV/JSON) | One-time at startup | One-time at startup | **Ruled out** |
| Texture VRAM (11 MB) | Small | Small | **Ruled out** |
| Sound preloading | Minimal | Minimal | **Ruled out** |
| OpenGL corruption | Only after panel use | Only after panel use | **Ruled out** |
| JVM/GC aggression | Not amplified by mod | Not amplified by mod | **Ruled out** |
| JAR class count (177) | Not eagerly loaded | Not eagerly loaded | **Ruled out** |
| **settings.json (75 sprite paths)** | **Merged at startup** | **Settings shared** | **WEAK SUSPECT** |
| `"utility":"true"` (string vs bool) | Minor flag issue | Same | **Ruled out** |

**Key observation: NO commonly known code path in this mod runs per-frame on main menu or combat.** The 10 FPS drop has no explanation within the mod's codebase that I can find through static analysis alone.

## What's Still Unexplained

After thorough investigation, here is what remains open:

1. **The settings.json theory is weak** — 75 nested sprite paths should not add ~67ms per frame. Many mods with more sprites don't cause this issue. The diagnose.md previously called this "PRIMARY SUSPECT" but that verdict is likely incorrect.

2. **No per-frame code path exists** — The mod has zero per-frame hooks on main menu or combat. No `EveryFrameCombatPlugin`, no `renderInUICoords`, no `renderInWorldCoords`, no global listeners.

3. **Class loading is lazy** — Only ~5 of 177 classes are loaded at startup on main menu/combat.

## Recommended Diagnostic Approach

**Systematic binary strip-down test is the only reliable way to isolate the cause.**

Since the mod has no evident per-frame code, the FPS drop must be caused by:
- Something in the mod's **startup processing** that leaves a persistent bad state
- An **interaction** between the mod and another mod the player has
- A **Starsector engine bug** triggered by something specific in this mod

Create minimal test mods that incrementally add components:

**Test A — Empty mod:**
- Simplest possible `CasinoModPlugin` with empty `onApplicationLoad()`
- No JAR with extra classes (just the plugin)
- No settings.json, sounds.json, rules.csv, etc.
- *If this still drops FPS → issue is the plugin class itself or the mod being present*

**Test B — Add settings.json:**
- Empty plugin + the 75-path settings.json
- *If FPS drops → settings.json is confirmed*

**Test C — Add CasinoConfig.loadSettings():**
- Empty plugin + settings.json + CasinoConfig class with full loadSettings()
- *If FPS drops → casino_settings.json loading or CSV loading is the cause*

**Test D — Add sounds.json:**
- Empty plugin + sounds.json with casino_theme registration
- *If FPS drops → music registration is the cause*

**Test E — Add rules.csv:**
- Empty plugin + the campaign rules
- *If FPS drops → rule registration is the cause*

**Test F — Full mod:**
- The complete mod as released
- *If only Test F drops FPS → the specific combination triggers the issue*

Have the player test each variant in order until the FPS drops.
