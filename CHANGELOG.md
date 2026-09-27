# Changelog

All notable changes to **Sanity: Psychological Devastation** (`sanitypd`) are listed here.
This is an **alpha** build: entries marked *alpha* may still change without notice.

Version format: `<minecraft>-forge<forge>-<mod>` — the current artifact is `sanitypd-mc1.20-1.3.3.jar`
(Minecraft 1.20.1 / Forge 46 / mod 1.3.3). There is no `-alpha` suffix in the file name yet: the alpha status
is stated in the mod description and in this changelog, not in the version string.

---

## 1.3.3 — current

**Status: alpha test build.** Back up your worlds before installing.

This is a **large feature release** built on top of 1.1.1. Work on it happened across many internal
development builds, but it is published as a single version — there is no need to install anything in
between, and nothing from those builds is missing here.

Adds **macarons**, a fifth inner voice, and a rare late-game line pool, and reworks what the
"do not say that name" easter egg does once a world has other players in it — so that it can never take
a server down with it. It also fixes a number of things reported on 1.1.0 and 1.1.1.

### Additions

* **Macarons, eight colours** — red, orange, yellow, green, light blue, blue, purple and pink, each with
  its own texture, in the mod's own creative tab.
  * **Crafting** (shaped, 3x3): egg / dye / (empty) over sugar / milk bucket / sugar over (empty) / dye /
    egg. This yields one macaron and the milk bucket comes back as an empty bucket, exactly like the
    vanilla cake recipe. The dye picks the colour: the green one uses lime dye, the light blue one uses
    light blue dye.
  * **Eating one** restores 3 hunger and 3 saturation, grants **Regeneration II for 15 seconds**, and
    **slowly restores sanity for 25 seconds** (1 point per second, shown by the small rising arrow on the
    sanity gauge).
  * **One shared cooldown**: all eight colours share a single 5 second cooldown, so they cannot be eaten
    one after another to stack the effect. Eating a second macaron before the effect ends **replaces** the
    effect instead of extending it.
  * The sanity-recovery effect keeps its HUD icon switched off on purpose, so a macaron shows the vanilla
    Regeneration icon only and one bite never stacks two icons.
* **Macarons can buy you out of the name.** If the forbidden name is submitted while the sender's
  inventory (the 36 main slots plus the offhand) holds any macaron, nothing crashes and nothing kills:
  **every macaron is taken** and the player is left at **exactly 1 health point**, with a line only that
  player can see. Without macarons the usual outcome applies.
* **A fifth inner voice.** Once a world has reached a certain late-game milestone, a small set of
  additional lines can join the **severe** tier's draw. They appear in the centre of the screen like the
  other inner voices, but are drawn in **dark red**, never tremble, fade in and out over about five
  seconds, and each appearance is accompanied by a cave sound (at most once every five minutes). The pool
  never repeats a line until it has been used up.
  * Those lines sit deliberately outside the four editable pools: `/sanity hint <tier> list` never shows
    them, `add` / `remove` / `clear` cannot touch them, and the `/sanity hint show` preview cannot draw
    them. A custom line that repeats one of them is refused **silently**.
  * The milestone is remembered **per save** (a small marker file inside that save's own folder) and is
    never shared between saves. On a remote server there is no local save folder to hold it, so the pool
    stays locked there.
* **A separate warning window.** At severe madness only the severe pool speaks, and during an immunity
  only the severe pool speaks; the **last five seconds of an immunity** now use their own expiry lines
  instead of the ordinary ones. There are six built-in lines for that window, and the deep tier gained
  three more.
* **One routing line for the easter egg.** When a submitted hint mentions the name, the log records which
  outcome was chosen and by whom — for example
  `[HIDDEN-NAME] command routed: side=CLIENT sender=LocalPlayer thread=Render thread`. It exists because
  "the game closes in single player but only kills you once the world is opened to LAN" cannot be read off
  a crash report. **The submitted text is never written to the log.**

### Changed

* **The easter egg is multiplayer-safe.** Speaking the name no longer closes the game unless you are
  playing alone in a world nobody else can join. **The host of a shared world, every guest, and anyone on
  a dedicated server take the damage instead**, and a server is never closed by it — so a single command
  can no longer take a whole server down and cost everyone their world.
* **A command block or the server console does nothing at all** for that name, because there is nobody
  behind the command to punish. Ordinary text from a command block still works normally, and the word
  detection rule is unchanged (any spacing or punctuation between the letters still counts).
* The two outcomes — closing the game and taking the damage — are now **mutually exclusive results of one
  decision**, so a single command can never produce both.

### Fixes

* **The macaron restored twelve times the intended saturation.** The game computes a food's saturation as
  `hunger x modifier x 2`, and the modifier had been written as the amount it was meant to contribute, so
  a macaron gave 72 saturation instead of 3. It now gives the intended 3. (Spotted through AppleSkin by a
  player — thank you.)
* **The sanity-recovery effect had no icon file at all**, so the effect list and the effect tooltip had
  nothing to draw for it. It now ships the intended 18x18 icon.
* **The deep and expiry inner-voice pools had no English lines** — an English game still showed the
  Chinese text for those two pools. Both are translated now.
* **The immunity-expiry warning was unreachable.** The state machine that decides "has this window been
  announced yet" compared two counters that had already been made equal on the same frame, so the branch
  that picks a line was never entered: the warning never appeared for a whole session. It is now a single
  flag set when a line is picked and cleared only once the window has been closed for longer than the
  warning window itself, so two overlapping immunity windows are still announced only once.
* **A regression that made the warning last exactly one frame.** The "already announced" check had been
  placed before the code that redraws the line and counts its display timer down, so after the first frame
  the timer was never advanced again and the line froze instead of playing out. Drawing and the countdown
  now run together for the full duration (~5 seconds).
* **The centre line was being drawn at roughly 6% opacity**, which is indistinguishable from "nothing is
  drawn" on a real screen; the alpha floor is now 60% and the breathing ripple no longer degenerates on a
  negative timer. The line is much more legible in general, and its shake updates at the game tick rate
  rather than the frame rate, which removes the flicker-fast trembling.
* **Closed a way to duplicate or farm the inner entities** — the spawn guard can no longer be used to keep
  spawning them, which also removes a way to destroy a save by flooding it.
* Several comment blocks in the inner-voice manager described behaviour that no longer matched the code.

---
## 1.1.1

**Status: alpha test build — previous public release, superseded by 1.3.3.** Back up your worlds before installing.

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
