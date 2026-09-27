# Changelog

All notable changes to **Sanity: Psychological Devastation** (`sanitypd`) are listed here.

This is an **alpha** build: mechanics, numbers, config keys and commands may still change between
versions, and entries marked *alpha* may change without notice.

Version format: `<minecraft>-forge<forge>-<mod>` — the current artifact is `sanitypd-mc1.20-1.3.3.jar`
(Minecraft 1.20.1 / Forge 46 / mod 1.3.3). There is no `-alpha` suffix in the file name yet: the alpha
status is stated in the mod description and in this changelog, not in the version string.

> **How to read this file:** every entry is measured against the **previous public release**, never
> against an internal development build. Builds that were only ever used for testing are not listed —
> their content is folded into the release that did ship. So if you are on the last public version,
> the newest entry tells you exactly what changed for you.

---

## 1.3.3 — current

**Status: alpha test build.** Back up your worlds before installing.

If you are on **1.1.1** (or 1.1.0), this is the update to take.

Everything below is measured against 1.1.1, the last public build. The mod gained a new food, a new
inner voice and a new warning window, and the "do not say that name" easter egg was rebuilt so it can
no longer take a server down. Three things that were still broken in 1.1.1 are fixed: the centre
warning line was drawn so faintly it was effectively invisible, the immunity-expiry warning froze
after a single frame instead of playing out, and the inner entities could be duplicated with the
spawn guard.

This was developed across many internal builds, but it ships as one version: there is nothing to
install in between, and nothing from those builds is missing here.

### New since 1.1.1

**Macarons**
* Eight colours — red, orange, yellow, green, light blue, blue, purple and pink — each with its own
  texture, in the mod's own creative tab.
* Eating one restores 3 hunger and 3 saturation, grants Regeneration II for 15 seconds, and slowly
  restores sanity for 25 seconds (1 point per second, shown by the small rising arrow on the sanity
  gauge).
* All eight colours share a single 5 second cooldown, so you cannot eat one colour after another to
  stack the effect. Eating a second macaron before the effect ends replaces the effect instead of
  extending it.
* Macarons also have a second purpose — see the easter egg section below.

**The sanity gauge's rising arrow**
* The small arrow next to the sanity gauge now also points up while a macaron's recovery is running,
  so you can see at a glance that your sanity is climbing rather than draining.

**A fifth inner voice**
* Once a world has reached a certain late-game milestone, a small set of additional lines can join
  the severe tier's draw. They appear in the centre of the screen like the other inner voices, but are
  drawn in dark red, never tremble, fade in and out over about five seconds, and each appearance is
  accompanied by a cave sound (at most once every five minutes). The pool never repeats a line until
  it has been used up.
* Those lines sit deliberately outside the four editable pools: the hint commands never list them and
  cannot add, remove or clear them, and the preview cannot draw them. A custom line that repeats one
  of them is refused silently.
* The milestone is remembered per save and never shared between saves. On a remote server there is no
  local save folder to hold it, so the pool stays locked there.

**A separate warning window**
* At severe madness only the severe pool speaks, and during an immunity only the severe pool speaks;
  the last five seconds of an immunity now use their own six expiry lines instead of the ordinary
  ones.

### Fixed since 1.1.1

* The centre warning line was drawn at roughly 6% opacity, which is indistinguishable from "nothing
  is drawn" on a real screen. It is now at 60% minimum and much more legible, and its shake updates at
  the game tick rate instead of the frame rate, which removes the flicker-fast trembling.
* The immunity-expiry warning appeared for exactly one frame and then froze instead of playing out. It
  is now a single line shown in the centre of the screen for about five seconds.
* Closed a way to duplicate or farm the inner entities — the spawn guard can no longer be used to keep
  spawning them, which also removes a way to destroy a save by flooding it.
* The deep-tier and expiry inner-voice lines now also exist in English. On 1.1.1 an English game still
  showed the Chinese text for those two pools.

### Multiplayer: the easter egg is rebuilt

This was the roughest part of the mod in 1.1.x, and it has been rebuilt around one rule: a command must
never be able to end a world that other people are playing in.

* Speaking the name no longer closes the game unless you are playing alone in a world nobody else can
  join. The host of a shared world, every guest, and anyone on a dedicated server take the damage
  instead, and a server is never closed by it.
* A command block or the server console does nothing at all for that name, because there is nobody
  behind the command to punish. Ordinary text from a command block still works normally, and the word
  detection rule is unchanged (any spacing or punctuation between the letters still counts).
* The two outcomes — closing the game and taking the damage — are now mutually exclusive results of one
  decision, so a single command can never produce both.
* Carrying macarons buys you out of it: if your inventory (the 36 main slots plus the offhand) holds any
  macaron, nothing crashes and nothing kills. Every macaron is taken and you are left at exactly 1
  health point, with a line only you can see.

---

## 1.1.1

**Status: alpha test build — previous public release, superseded by 1.3.3.** Back up your worlds before
installing.

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

**Status: alpha test build — superseded by 1.1.1 (not published separately).** Back up your worlds before
installing.

### Sanity system
* Sanity is now **point-based**: players cap at 100, other mobs cap at their max health;
  older saves migrate automatically.
* **Every living entity** has sanity (the original mod applied it to players only).
* Inner entities are excluded: they have no sanity, are immune to confusion/mania,
  and convert the psychic damage they take into **2.5× true damage**.


**Status: alpha test build — superseded by 1.1.1 (not published separately).** Back up your worlds before installing.

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
