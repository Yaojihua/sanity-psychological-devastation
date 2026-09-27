# Changelog

All notable changes to **Sanity: Psychological Devastation** (`sanitypd`) are listed here.
This is an **alpha** build: entries marked *alpha* may still change without notice.

Version format: `<minecraft>-forge<forge>-<mod>` — the current artifact is `sanitypd-mc1.20-1.3.3.jar`
(Minecraft 1.20.1 / Forge 46 / mod 1.3.3). There is no `-alpha` suffix in the file name yet: the alpha status
is stated in the mod description and in this changelog, not in the version string.

---

## 1.3.3 — current

**Status: alpha test build.** Back up your worlds before installing.

Gives the sanity-recovery effect the artwork it was meant to have.

### Fixes
* **The sanity-recovery effect had no icon at all.** The effect that macarons apply for 25 seconds was
  registered without an icon file, so anywhere the game looks the icon up — the effect list on the
  inventory screen, the effect tooltip — had nothing to draw. It now ships the intended 18x18 icon.
* **The icon stays hidden on the HUD, on purpose.** As with the previous builds, eating a macaron only
  shows the vanilla Regeneration icon next to the hotbar; the recovery effect deliberately keeps its HUD
  icon switched off so that the two buffs do not stack two icons for one bite. Only eating a macaron
  changed here: no numbers, no timing and no mechanics were touched.

---

## 1.3.2

**Status: alpha test build.** Back up your worlds before installing.

Fixes the macaron's saturation and completes the English translations of the inner-voice pools.

### Fixes
* **A macaron no longer restores twelve times the intended saturation.** A food's saturation modifier is
  not an amount: the game adds `hunger x modifier x 2`. The macaron's modifier had been written as the
  6 it was meant to *contribute*, which the game read as 72 saturation. It now contributes the intended
  3 saturation points.
* **The deep and expiry inner-voice pools have English lines.** Those two pools (the line spoken in the
  last five seconds before the mania damage, and the warning during the last five seconds of an immunity
  buff) were still showing the Chinese text in an English game.

---
## 1.3.1

**Status: alpha test build.** Back up your worlds before installing.

Gives the macarons a second purpose: they can buy you out of the name.

### Additions
* **Carrying macarons spares you.** If the forbidden name is submitted while the sender's inventory
  (the 36 main slots plus the offhand) holds any macaron, nothing crashes and nothing kills: **every
  macaron is taken** and the player is left at **exactly 1 health point**, with a line only that player
  can see. Without macarons the usual outcome applies.
* A command block or the console never triggers it - there is no player behind the command to pay.

---
## 1.3.0

**Status: alpha test build.** Back up your worlds before installing.

Adds **macarons**: eight colours of small cake that restore a little hunger and sanity.

### Additions
* **Eight macarons** — red, orange, yellow, green, light blue, blue, purple and pink, each with its own
  texture.
* **Crafting**: egg / dye / egg over sugar / milk bucket / sugar over egg / dye / egg. The milk bucket
  comes back as an empty bucket, like the vanilla cake recipe. The dye picks the colour (the green one
  uses lime dye, the light blue one uses light blue dye).
* **Eating one** restores 3 hunger and 3 saturation, grants Regeneration II for 15 seconds, and slowly
  restores sanity for 25 seconds (1 point per second, shown by the small rising arrow on the sanity
  gauge).
* **One shared cooldown**: all eight colours share a single 5 second cooldown, so they cannot be eaten
  one after another. Eating another one before the effect ends replaces the effect rather than extending
  it.

### Notes
* The recovery effect's icon is hidden, so eating one shows the vanilla Regeneration icon only.

---
## 1.2.7

**Status: alpha test build.** Back up your worlds before installing.

Fixes the host of a shared world, who could still close the game and take his own server with it.

### Fixes
* **Hosting a world no longer lets you close it by accident.** The previous build asked "is this the
  singleplayer owner of an unshared world" to decide whether closing the game was safe. That question is not
  about the world any more, it is about the person, and it answered the wrong way for a host - so the host
  fell through and closed the game, dropping everyone connected to him. The decision is now made from the
  **world** (is it open to the network?) and the connection only: **playing alone closes the game; everyone
  else - the host of a shared world, any guest, anyone on a server - takes the damage instead.**
* A server is never closed, which is now decided explicitly rather than inferred.

### Notes
* The diagnostic line for this command now records the world state, whether the server is dedicated,
  whether the sender counts as the owner, and which outcome was chosen, so the next report can be answered
  without guessing. It never prints the submitted text.

---
## 1.2.6

**Status: alpha test build.** Back up your worlds before installing.

Fixes the easter egg in single player, which the previous build had broken.

### Fixes
* **Playing alone closes the game again.** The previous build decided whether the sender was "the player on
  this machine" from the connection address alone, but a single-player session does not use a network
  address at all, so the player was treated as a guest and only took the damage. The world state is now the
  primary question and the address is only consulted for everyone else: **playing alone closes the game**,
  while **the host of a shared world and every guest take the damage instead**.
* **A dedicated server is still never closed.** That case is now decided explicitly instead of inferred, so
  a server cannot be halted by this command.
* The command keeps a diagnostic log line recording whether the world is shared and which outcome was
  chosen. It never prints the submitted text.

---

## 1.2.5

**Status: alpha test build — superseded by 1.2.6.** Back up your worlds before installing.

Defines the two outcomes by whether the world is shared: the host of a world open to the network is punished
like a guest, so halting the game can never take a server down with it.

---
## 1.2.3

**Status: alpha test build.** Back up your worlds before installing.

Behaviour is identical to 1.2.2; this build adds the diagnostic line that made the 1.2.1 → 1.2.2 bug
findable, so that a future report can be answered from the log alone.

### Additions
* **One routing line for the easter egg.** When a submitted hint mentions the name, the log now records which
  of the two outcomes was chosen and by whom — for example
  `[HIDDEN-NAME] command routed: side=CLIENT sender=LocalPlayer thread=Render thread`. It exists because
  "the game closes in single player but only kills you once the world is opened to LAN" cannot be read off a
  crash report, and reasoning about it alone produced two wrong diagnoses. **The submitted text is never
  written to the log.**

---

## 1.2.2

**Status: alpha test build — superseded by 1.2.3.** Back up your worlds before installing.

Fixes the multiplayer handling of the "do not say that name" easter egg.

### Fixes
* **A command block can no longer trigger it.** In 1.2.1 a command block (or the server console) speaking the
  name still closed the game. The decision is now made from **who sent the command**, not from which side of
  the game is running it: a player's own command is answered by the server (silent refusal plus lethal damage
  to that player), while anything with **no player behind it** — a command block, a command-block minecart,
  the server console — does nothing at all.
* **Your own command no longer closes your game when you are the host.** In 1.2.1, hosting a world (or opening
  it to LAN) meant your own command was answered by the server and then still crashed the client. The
  punishment and the crash are now mutually exclusive outcomes of one decision, so a single command can no
  longer produce both.

### Notes
* The crash still happens for a command typed on the client side, and the word detection rule is unchanged
  (any spacing or punctuation between the letters still counts).
* Nothing about the punishment changed: still silent, still nothing saved, still the same damage.

---

## 1.2.1

**Status: alpha test build — superseded by 1.2.2.** Back up your worlds before installing.

Defines what the "do not say that name" easter egg does in **multiplayer**, and makes sure it can never take
a server down with it.

### Fixes
* **The easter egg now behaves correctly in multiplayer.** Speaking the name still closes **the game of the
  player who typed it, and only theirs** — other players and the world are untouched.
* **A dedicated server never crashes.** Previously the server-side half of the command could not do anything
  at all, so the easter egg was simply dead in multiplayer. Now the server keeps its own half: it swallows
  the command **without any message** and answers the speaker with lethal damage. The server process itself
  is never affected, so no world data can be lost this way.
* **A command block or the server console can never trigger it.** They have nobody behind them: with no
  player as the sender, nothing crashes and nobody is punished. Only a player typing the command himself is
  affected.

### Notes
* Everything else is unchanged: the same word detection rule (any spacing or punctuation between the letters
  still counts), the same "no success message, nothing saved" behaviour, and the same rule that the text is
  never written into `config/sanitypd_mental_hints.json`.

---

## 1.2.0

**Status: alpha test build — superseded by 1.2.1.** Back up your worlds before installing.

Adds a small extra inner-voice pool on top of the four editable ones, together with the rules that keep it
separate from them.

### Additions
* **Extra inner-voice lines.** Once a world has reached a certain late-game milestone, a small set of
  additional lines can join the **severe** tier's draw. They appear in the centre of the screen like the
  other inner voices, but are drawn in **dark red**, never tremble, and each appearance is accompanied by a
  cave sound (at most once every five minutes).
* Those lines sit deliberately outside the four editable pools: `/sanity hint <tier> list` never shows them,
  `add` / `remove` / `clear` cannot touch them, and the `/sanity hint show` preview cannot draw them.
* A custom line that repeats one of them is refused **silently**: the command reports neither success nor
  failure, and nothing is added to the pool.
* The milestone is remembered **per save** (a small marker file inside that save's own folder) and is never
  shared between saves.

### Notes
* The marker is written while the milestone is reached in a local single-player world. On a remote server
  there is no local save folder to hold it, so the pool stays locked there.
* The extra lines keep the severe tier's size and on-screen duration; only their colour and their steadiness
  differ from it.

---

## 1.1.1

**Status: alpha test build — superseded by 1.2.0.** Back up your worlds before installing.

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
