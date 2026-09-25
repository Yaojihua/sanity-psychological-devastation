# Changelog

All notable changes to **Sanity: Psychological Devastation** (`sanitypd`) are listed here.
This is an **alpha** build: entries marked *alpha* may still change without notice.

Version format: `<minecraft>-forge<forge>-<mod>` — the current artifact is `sanitypd-mc1.20-1.1.1.jar`
(Minecraft 1.20.1 / Forge 46 / mod 1.1.1). There is no `-alpha` suffix in the file name yet: the alpha status
is stated in the mod description and in this changelog, not in the version string.

---

## 1.1.1 — current

**Status: alpha test build.** Back up your worlds before installing.

Fixes for the inner-voice warnings. Both bugs lived in the same state machine, so **if you are on 1.1.0
please update**: on that build the "immunity is about to expire" warning could not appear at all (or
appeared for a single frame and then froze).

### Fixes
* **The immunity-expiry warning was unreachable.** The state machine that decides "has this window been
  announced yet" compared two counters that had already been made equal on the same frame, so the branch
  that picks a line was never entered: the warning never appeared for a whole session. It is now a single
  flag set when a line is picked and cleared only once the window has been closed for longer than the
  warning window itself, so two overlapping immunity windows are still announced only once.
* **Fixed a regression that made the warning last exactly one frame.** The "already announced" check had
  been placed before the code that redraws the line and counts its display timer down, so after the first
  frame the timer was never advanced again and the line froze instead of playing out. Drawing and the
  countdown now run together for the full duration (~5 seconds).
* **The centre line was being drawn at roughly 6% opacity** (1.1.0), which is indistinguishable from
  "nothing is drawn" on a real screen; the alpha floor is now 60% and the breathing ripple no longer
  degenerates on a negative timer. The centre line is also much more legible in general.
* The centre-line shake updates at the game tick rate rather than the frame rate, which removes the
  flicker-fast trembling.

---

## 1.1.0

**Status: alpha test build — superseded by 1.1.1.** Back up your worlds before installing.

### Sanity system
* Sanity is now **point-based**: players cap at 100, other mobs cap at their max health;
  older saves migrate automatically.
* **Every living entity** has sanity (the original mod applied it to players only).
* Inner entities are excluded: they have no sanity, are immune to confusion/mania,
  and convert the psychic damage they take into **2.5× true damage**.

### New damage types
* `sanitypd:psychic` — drained sanity; ignores armour, reduced only by *Psychic Protection* or a blocking shield.
* `sanitypd:true_damage` / `sanitypd:true_damage_attributed` — true damage (attributed and sourceless variants).
* `sanitypd:mania` — mania drain.

### New enchantments
* **Psychic Protection** — 1 point per level against psychic damage, four armour pieces, max level IV in survival,
  does not conflict with other enchantments. Psychic damage is no longer reduced by vanilla Protection etc.
* **Psychic Deprivation** — weapon enchantment, psychic damage on hit.
* **Psychic Drain** — applies the psychic drain effect.

### New items
* **Shadow Sword / Shadow Axe** — charge to repair durability at the cost of sanity; an off-hand shield takes priority.
* **Shadow refinement** — anvil: shadow weapon + Inner Clumps → +1 level each
  (+1 psychic damage, 1 XP level, cap 100, no XP penalty, enchantments and durability preserved).
  Hold Shift to consume every clump at once.
* **Inner Shard / Inner Clump / Inner Core** — inner-entity drops and crafting materials.
* **Stabilizer α / β / γ** — sanity restore with a cooldown.
* **Garland** — wearable flower accessory.
* **Spawn eggs** for all three inner entities, in the mod's own creative tab.

### Inner entities
* **Rotting Stalker** (psychic resistance 10), **Sneaking Terror** (15), **Screaming Crawler** (20).
* The Screaming Crawler now **hunts players below 20% sanity**, routes around obstacles with A\* pathfinding,
  steps up and jumps over ledges, and its eyes glow in the dark.
* **Two sanity gates on the crawler**: at **75% sanity or above it cannot track you at all** (it refuses you as
  a target and instantly drops a chase in progress), and between **50% and 75% its blast destroys no blocks**
  (the psychic damage is unchanged). Below 50% it behaves as a creeper would.
* The crawler's explosion damages sanity instead of health and no longer hurts other inner entities.
* Inner entities still count toward passive sanity drain while invisible, and can retaliate when attacked.

### Additions and fixes
* New commands: `/sanity resist`, `/sanity innerspawn`, `/sanity hint <tier> add|remove|list|clear|show`
  (tiers: `mild`, `severe`, `deep`, **`expiry`**),
  plus reworked `/sanity get|set|add|state|psychic|truedamage|config reload`.
* **How the inner-monologue tiers are used**: at 25% sanity or below only the *severe* pool is drawn
  (the deep tier is no longer part of the regular draw); the *deep* tier speaks exactly **one randomly picked
  line** during the last 5 seconds before the mania damage starts; while **mania immunity** is held only the
  *severe* pool is drawn, and during that buff's last 5 seconds the **expiry** pool is shown instead
  (3 built-in lines, editable with `/sanity hint expiry …`).
* The **expiry warning pool now also covers *Inner Immunity*** (the Gamma stabilizer): whichever immunity
  ends first is announced, and when the two immunity windows overlap the warning is shown **only once**.
  ⚠️ **In this build the warning could not actually be displayed — see the 1.1.1 entry above.**
* ~~**Inner-voice visibility fix**~~ / ~~**expiry-warning fix**~~ — both were attempted in this build but the
  result was still wrong (the centre line stayed at ~6% opacity). The working fixes are in **1.1.1**.
* The centre-line shake now updates at the game tick rate rather than the frame rate.
  ⚠️ **This build still drew the line at ~6% opacity, so it was not visible — fixed in 1.1.1.**
* The **title-screen splash pool** gains a few extra lines (vanilla lines all kept).
* 8 upstream defects fixed: pet death penalty never applied, block cooldowns saved to the wrong table,
  chunk "player placed block" tracking losing entries, inner-entity capability packet resent every tick,
  refresh timer keyed by instance (memory leak), spawn-height sentinel value, garland timer shared globally,
  reversed argument order in the English command messages.
* **Save-safety fix**: the "block mob spawning" test switch no longer removes the Ender Dragon, the Wither,
  the Warden or the Elder Guardian. They share the monster spawn category with ordinary mobs, and removing a
  one-per-world boss made the world impossible to finish.
* Jade integration (optional): shows the sanity value under the health bar.

---

## 1.20-forge46-1.0.0 — initial standalone release

* First standalone build under the `sanitypd` mod id, based on **Sanity: Descent Into Madness** by croissantnova (MIT).
* Simplified Chinese localisation of the mod and its config comments.
* Config options for light, darkness, dirt paths and carpets, being stuck in blocks, trampling farmland,
  potting flowers, changing dimensions, lightning strikes, breaking blocks, and the new HUD indicator.

---

## Credits

* Original project: **Sanity: Descent Into Madness** by **croissantnova** (MIT) — mechanics derived from it.
* Original art / audio collaboration: **toujourspareil**, **Zapsplat**.
* This version: **Yaojihua** — with **AI-assisted development (DeepSeek Harness, "dsh")**.
