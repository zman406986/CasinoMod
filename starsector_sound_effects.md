# Starsector Sound Effects — Complete Modding Reference

> **Scope:** Sound effects (SFX) only. Music is covered in a separate section for contrast, but this guide focuses on non-music audio.
>
> Sources: `SoundPlayerAPI.java`, `SoundAPI.java`, `Sounds.java`, `Misc.java`, `PlaySound.java`, community forum threads, example mods (Nexerelin, Casino), and the SOUND tutorial.

---

## 1. Architecture Overview

### Entry Point

All sound playback goes through the singleton `SoundPlayerAPI` obtained via:

```java
SoundPlayerAPI soundPlayer = Global.getSoundPlayer();
```

### Core Interfaces

| Interface | Package | Purpose |
|-----------|---------|---------|
| `SoundPlayerAPI` | `com.fs.starfarer.api` | Sound system entry point — play, loop, stop |
| `SoundAPI` | `com.fs.starfarer.api` | Handle to a *currently playing* sound instance |
| `Sounds` | `com.fs.starfarer.api.impl.campaign.ids` | Constants for vanilla sound IDs |

### Key Methods (SoundPlayerAPI)

```java
// UI sounds — files must be STEREO
SoundAPI playUISound(String id, float pitch, float volume);

// Positional (3D) sounds — files must be MONO
SoundAPI playSound(String id, float pitch, float volume, Vector2f loc, Vector2f vel);

// Looped UI sound
void playUILoop(String id, float pitch, float volume);

// Looped positional sound — must be called EVERY frame or it fades out
void playLoop(String id, Object playingEntity, float pitch, float volume,
              Vector2f loc, Vector2f vel);
void playLoop(String id, Object playingEntity, float pitch, float volume,
              Vector2f loc, Vector2f vel, float fadeIn, float fadeOut);

// Fade control for next loop
void setNextLoopFadeInAndOut(float nextLoopFadeIn, float nextLoopFadeOut);

// Audio filter
void applyLowPassFilter(float gain, float gainHF);

// Listener position
Vector2f getListenerPos();
void setListenerPosOverrideOneFrame(Vector2f pos);
```

### SoundAPI (playback handle)

```java
SoundAPI sound = Global.getSoundPlayer().playUISound("my_sound", 1f, 1f);
sound.stop();           // stop immediately
sound.setVolume(0.5f);  // change volume during playback
sound.setPitch(1.2f);   // change pitch during playback
sound.setLocation(x, y);// reposition (positional only)
boolean playing = sound.isPlaying();
```

---

## 2. CRITICAL: Mono vs Stereo

| Sound Type | Channel Requirement | Method | Use Case |
|-----------|-------------------|--------|----------|
| **UI / Menu** | **Stereo** | `playUISound()` | Buttons, notifications, interface |
| **Positional / 3D** | **Mono** | `playSound()` | Combat, ambient, spatial events |
| **UI Loop** | **Stereo** | `playUILoop()` | Ambient menus, bar ambience |
| **Positional Loop** | **Mono** | `playLoop()` | Engine hums, ongoing effects |

**Wrong channel = broken playback.** A stereo file used with `playSound()` will not position correctly in 3D space. A mono file used with `playUISound()` may sound thin or phase-cancelled.

> **Forum wisdom:** Stereo sounds are played in the UI layer and won't be placed in OpenAL's stereo field — they sound like they come from "everywhere." Use stereo for rarely-occurring, attention-requiring events.

---

## 3. Sound Configuration: `sounds.json`

### Location

`data/config/sounds.json` in your mod folder.

### Schema — Sound Effects (NOT music)

Each key is a **sound ID** referenced from code. The value can be either a **JSON array** (simple) or a **JSON object** with a `"sounds"` array (equivalent).

Multiple entries in the array = **random selection** each time the sound is played.

```json
{
  "my_click": [
    {"file": "sounds/mymod/click.ogg", "pitch": 1.0, "volume": 0.6}
  ],

  "my_random_impact": [
    {"file": "sounds/mymod/impact_light.ogg", "pitch": 0.9, "volume": 0.5},
    {"file": "sounds/mymod/impact_heavy.ogg", "pitch": 1.1, "volume": 0.8}
  ],

  "my_long_sound": {
    "sounds": [
      {"file": "sounds/mymod/ambient.ogg", "pitch": 1.0, "volume": 0.4}
    ]
  }
}
```

### Per-Entry Fields

| Field | Type | Range | Description |
|-------|------|-------|-------------|
| `file` | string | — | Path relative to mod root (e.g. `sounds/mymod/effect.ogg`) |
| `pitch` | float | 0.5 – 2.0 | 1.0 = normal; <1 = slower/deeper; >1 = faster/higher |
| `volume` | float | 0.0 – 1.5 | 1.0 = standard; can exceed for emphasis |

### Music Section (contrast — NOT SFX)

Music lives under a top-level `"music"` key with different schema:

```json
{
  "music": {
    "my_music_set": [
      {
        "source": "sounds/music",
        "volume": 0.80,
        "files": ["track1.ogg", "track2.ogg"],
        "randomStart": false
      }
    ]
  }
}
```

### Supported Audio Formats

| Format | Use Case | Notes |
|--------|----------|-------|
| `.ogg` | **Recommended** | Smaller files, good quality, preferred by engine |
| `.wav` | Short sounds | Larger, uncompressed |

> **Technical requirement:** Use Ogg Vorbis, 44.1 kHz, 32-bit. For positional sounds, encode as **mono**.

### Self-Interrupt Behavior

- If a sound ID references a **single file** multiple times (same file path), it will **self-interrupt** — the previous instance stops when a new one starts. Useful for rapid-fire weapons where you want a clean tail.
- If a sound ID references **different files**, they will **not** self-interrupt and can overlap.

---

## 4. Playing Sounds in Code

### Basic UI Sound

```java
Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 0.6f);
```

### UI Sound with Pitch Variation (avoid repetitiveness)

```java
float pitch = 0.9f + (float) Math.random() * 0.2f;
Global.getSoundPlayer().playUISound("my_sound", pitch, 0.8f);
```

### Positional (3D) Sound

```java
Vector2f loc = ship.getLocation();
Vector2f vel = ship.getVelocity();
Global.getSoundPlayer().playSound("explosion_flak", 1f, 1f, loc, vel);
```

### Screen-Center Positional (dramatic cutscene effect)

```java
Vector2f listenerPos = Global.getSoundPlayer().getListenerPos();
Global.getSoundPlayer().playSound("explosion", 1f, 1f, listenerPos, new Vector2f());
```

### Looped Sound (must be called every frame)

```java
// In your advance() method:
Global.getSoundPlayer().playLoop("engine_hum", ship, 1f, 1f,
    ship.getLocation(), ship.getVelocity());

// With fade-in/out:
Global.getSoundPlayer().setNextLoopFadeInAndOut(0.05f, 0.5f);
Global.getSoundPlayer().playLoop("transit_loop", fleet, 1f, 1f, loc, vel);
```

### UI Loop

```java
Global.getSoundPlayer().playUILoop("ambient_bar", 1f, 0.5f);
```

### Stop a Sound Programmatically

```java
SoundAPI sound = Global.getSoundPlayer().playUISound("my_sound", 1f, 1f);
sound.stop();
```

### Using `Misc.playSound()` for Damage-Based Sounds

A convenience method in `com.fs.starfarer.api.util.Misc` that auto-selects a sound based on damage thresholds:

```java
Misc.playSound(damageResult, point, vel,
    "hit_shield_light_energy",   // light shields
    "hit_shield_heavy_energy",   // solid shields
    "hit_shield_heavy_energy",   // heavy shields
    "hit_light_energy",          // light hull
    "hit_solid_energy",          // solid hull
    "hit_heavy_energy");         // heavy hull
```

### CombatEngineAPI.applyDamage() Sound Flag

```java
engine.applyDamage(entity, point, damageAmount, DamageType.ENERGY,
    empAmount, bypassShields, dealsSoftFlux, source, true /* playSound */);
```

### Campaign Rule Command (rules.csv)

```
PlaySound "ui_button_pressed"
```

The `PlaySound` rulecmd in `com.fs.starfarer.api.impl.campaign.rulecmd.PlaySound` calls:
```java
Global.getSoundPlayer().playUISound(soundId, 1f, 1f);
```

### In-Panel UI Plugin Usage

```java
@Override
public void actionPerformed(Object actionId, Object source) {
    Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 0.6f);
    handleAction(actionId);
}

// Hover sound in processInput:
if (event.isMouseMoveEvent() && isOverButton(event, btn)) {
    Global.getSoundPlayer().playUISound("ui_button_mouseover", 1f, 0.25f);
}
```

---

## 5. Vanilla Sound IDs

### From `Sounds.java` constants

| Constant | Sound ID String |
|----------|----------------|
| `Sounds.STORY_POINT_SPEND` | `ui_char_spent_story_point` |
| `Sounds.STORY_POINT_SPEND_COMBAT` | `ui_char_spent_story_point_combat` |
| `Sounds.STORY_POINT_SPEND_LEADERSHIP` | `ui_char_spent_story_point_leadership` |
| `Sounds.STORY_POINT_SPEND_TECHNOLOGY` | `ui_char_spent_story_point_technology` |
| `Sounds.STORY_POINT_SPEND_INDUSTRY` | `ui_char_spent_story_point_industry` |
| `Sounds.REP_GAIN` | `ui_rep_raise` |
| `Sounds.REP_LOSS` | `ui_rep_drop` |
| `Sounds.NONE` | `sound_none` |

### Common UI Sound IDs (from community reference)

| Context | Sound ID |
|---------|----------|
| Button click | `ui_button_pressed` |
| Button hover | `ui_button_mouseover` |
| Disabled click | `ui_button_disabled_pressed` |
| Reputation up | `ui_rep_raise` |
| Reputation down | `ui_rep_drop` |
| Level up | `ui_char_level_up` |
| Story point gain | `ui_char_gained_story_point` |
| Hullmod acquired | `ui_acquired_hullmod` |
| Blueprint acquired | `ui_acquired_blueprint` |
| Discovery | `ui_discovered_entity` |
| Raid finished | `ui_raid_finished` |
| Cargo pickup | `ui_cargo_default` |
| Number scroll | `ui_number_scrolling` |
| Typing | `ui_typer_type` |
| Typing buzz | `ui_typer_buzz` |

### Combat Sound IDs (community reference patterns)

| Context | Example IDs |
|---------|-------------|
| Shield hit (light/solid/heavy) | `hit_shield_light_energy`, `hit_shield_heavy_kinetic` |
| Hull hit (light/solid/heavy) | `hit_light_energy`, `hit_solid_kinetic` |
| Explosions | `explosion_flak`, `explosion` |
| Detector ping | `ui_neutrino_detector_ping` |

---

## 6. Sound Effects vs Music — Complete Comparison

| Aspect | Sound Effects (SFX) | Music |
|--------|-------------------|-------|
| **API Entry** | `playUISound()`, `playSound()`, `playLoop()` | `playCustomMusic()`, `playMusic()` |
| **Config location** | Top-level keys in `sounds.json` | Under `"music"` key in `sounds.json` |
| **JSON schema** | `"id": [{"file":"...", "pitch":..., "volume":...}]` | `"music": {"set_id": [{"source":"...", "files":[...], "volume":...}]}` |
| **Audio channels** | UI: stereo, Positional: mono | Always stereo |
| **Spatial audio** | Yes — `playSound()` requires `loc, vel` | No — always non-spatial |
| **Per-instance control** | `SoundAPI` handle — stop, setVolume, setPitch | Fade in/out durations, music set IDs |
| **Looping** | `playLoop()`/`playUILoop()` — must be called every frame | `playCustomMusic(id, fadeOut, fadeIn, set, looping)` — true looping |
| **Concurrency** | Many SFX can play simultaneously | Only one track at a time |
| **Thread safety** | `playUISound()`/`playSound()` — NOT thread-safe | `playCustomMusic()`, `restartCurrentMusic()` — thread-safe |
| **Filter effects** | `applyLowPassFilter()` available for both | Same |

---

## 7. Parameter Guidelines

| Parameter | Range | Notes |
|-----------|-------|-------|
| `pitch` | 0.5 – 2.0 | 1.0 = normal; <1 = downsampled (slower, deeper, longer); >1 = upsampled (faster, higher, shorter) |
| `volume` | 0.0 – 1.5 | 1.0 = standard; can exceed for emphasis |
| `location` | Vector2f | Use `getListenerPos()` for screen-center |
| `velocity` | Vector2f | Used for doppler effect calculations |

### Pitch Variation Best Practice

> Forum recommendation: Use three pitch variants for a given sound:
> - One at 1.0
> - One at 1.03
> - One at 0.97

This prevents the repetitive feel without being obviously different.

---

## 8. Complete Real-World Examples

### Nexerelin `sounds.json` (excerpt)

```json
{
  "nex_sfx_combat": [
    {"file": "sounds/nexerelin/sfx_interface/combat.wav", "pitch": 0.8, "volume": 0.8}
  ],
  "nex_sfx_gb_victory": [
    {"file": "sounds/nexerelin/sfx_interface/groundbattle/victory.ogg", "pitch": 1.1, "volume": 0.22}
  ],
  "nex_alarm": [
    {"file": "sounds/nexerelin/sfx_interface/alarm.wav", "pitch": 1, "volume": 1.0}
  ],
  "nex_alicorn_fire": [
    {"file": "sounds/nexerelin/weapons/plasma_cannon_fire_01.ogg", "pitch": 1, "volume": 3}
  ]
}
```

(Note: volume can exceed 1.0 for powerful weapon sounds.)

### Sound Manager Utility Class

```java
public class MySoundManager {
    public static void playCardFlip() {
        Global.getSoundPlayer().playUISound("my_card_flip", 1f, 0.6f);
    }

    public static void playWin(int rarity) {
        float pitch = 1f + (rarity - 1) * 0.1f;
        float volume = 0.5f + (rarity - 1) * 0.15f;
        Global.getSoundPlayer().playUISound("my_win", pitch, Math.min(volume, 1f));
    }

    public static void playLose() {
        Global.getSoundPlayer().playUISound("my_lose", 0.9f, 0.4f);
    }

    public static void playPositionalExplosion(Vector2f loc, Vector2f vel) {
        Global.getSoundPlayer().playSound("my_explosion", 1f, 1f, loc, vel);
    }
}
```

---

## 9. Quick Reference Cheat Sheet

```java
// 1. Basic UI sound
Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);

// 2. UI sound with random pitch
Global.getSoundPlayer().playUISound("my_sound", 0.9f + Math.random() * 0.2f, 0.8f);

// 3. Positional sound
Vector2f loc = entity.getLocation();
Vector2f vel = entity.getVelocity();
Global.getSoundPlayer().playSound("my_sound", 1f, 1f, loc, vel);

// 4. Screen-center positional (for cutscenes)
Vector2f center = Global.getSoundPlayer().getListenerPos();
Global.getSoundPlayer().playSound("my_dramatic_sound", 1f, 1f, center, new Vector2f());

// 5. Loop (call every frame)
Global.getSoundPlayer().playLoop("my_loop", this, 1f, 1f, loc, vel);

// 6. Stop a sound
SoundAPI handle = Global.getSoundPlayer().playUISound("my_sound", 1f, 1f);
handle.stop();

// 7. Check if playing
if (handle.isPlaying()) { /* ... */ }

// 8. Damage-based sound via Misc
Misc.playSound(damageResult, point, vel,
    "hit_shield_light", "hit_shield_solid", "hit_shield_heavy",
    "hit_hull_light", "hit_hull_solid", "hit_hull_heavy");

// 9. Low-pass filter
Global.getSoundPlayer().applyLowPassFilter(0.3f, 0.5f);
```

---

## 10. Common Pitfalls

| Problem | Cause | Solution |
|---------|-------|----------|
| Sound doesn't play | Missing comma/bracket in `sounds.json` | Validate JSON syntax |
| Silent positional sound | Stereo file used with `playSound()` | Convert to mono 44.1 kHz Ogg |
| UI sound sounds wrong | Mono file used with `playUISound()` | Use stereo file for UI |
| Sound self-interrupts (cuts off) | Single file listed multiple times | Use multiple different files |
| Loop plays once then stops | `playLoop()` not called every frame | Call it in your `advance()` method |
| Game crashes on load | Empty `sounds.json` section | Ensure every entry has a `.ogg` file |
| Music conflicts | Multiple music mods active | Only one music mod at a time |
