# Sanity: Psychological Devastation

> ## ⚠️ ALPHA TEST BUILD
> This is an **alpha test version**. Mechanics, numbers, config keys and commands may still change between
> builds, and things may be unbalanced or broken. Back up your worlds before installing it.
> This build is the **first part test build**: the thought chain (part one) and the memory-fragment tapes
> are in; the boss stages and the rest of the story are not.
> Feedback and bug reports are very welcome — see [Reporting bugs](#reporting-bugs).

**A sanity system for Minecraft — new mechanics, items, mobs and commands built on top of the original Sanity mod.**

Sanity is a point-based mental state. Every living entity has it. Low sanity corrupts what you see,
what you hear, and eventually what comes for you.

---

## Overview

This is a standalone Forge mod (`modid: sanitypd`). Its mechanics are derived from the open-source project
**Sanity: Descent Into Madness** by [croissantnova](https://github.com/croissantnova/SanityDescentIntoMadness) (MIT),
and it builds on that foundation with new mechanics, items, mobs and commands. The original author's copyright
and license notices are preserved in full — see [LICENSE](LICENSE) and
[NOTICE](src/main/resources/META-INF/NOTICE-sanitypd.txt).

## Requirements

| | Version |
|---|---|
| Minecraft | 1.20 / 1.20.1 |
| Forge | 46+ |
| **GeckoLib** | **4.2** on Forge 46 · **4.8.4** on Forge 47.1+ (required — see the note below) |
| Jade | optional — adds a sanity line to its tooltip |

> **The GeckoLib version has to match your Forge version.** GeckoLib 4.2.1–4.8.4 declares `loaderVersion="[47,)"`,
> so it cannot load on Forge 46. With a wrong pairing the game reports a *missing `geckolib` dependency* —
> that message is misleading; the real cause is the Forge version. On Forge 46 use GeckoLib **4.2**;
> on Forge 47.1+ use **4.8.4**.

## Installation

1. Install Minecraft Forge for 1.20.1.
2. Put **GeckoLib** and **this mod** into your `mods/` folder.
3. Launch the game. Nothing else is needed; the config file is generated on first run.

## Features

### Sanity
* Point-based sanity: players cap at **100**, other mobs cap at their max health.
* **Every living entity has sanity**, not just the player.
* Passive influences (light level, darkness, weather, water, hunger, nearby monsters, pets, music, blocks underfoot)
  and active sources (sleeping, breeding, trading, fishing, shearing, advancements, eating, breaking farmland and more).
* Sanity is drained by **psychic damage**, which ignores armour by design and is reduced only by the
  *Psychic Protection* enchantment or by blocking with a shield.

### Status effects
| Effect | What it does |
|---|---|
| **Confusion** | the low-sanity state |
| **Mania** | rapid sanity loss; can be lethal on its own |
| **Mania Immunity** | blocks the mania true damage; its last 5 seconds show a separate inner-voice warning |
| **Inner Immunity** | inner entities will not attack you, and the crawler will not detonate; its last 5 seconds show the same warning pool |
| **Psychic Drain** | ongoing psychic damage over time |

### Enchantments
| Enchantment | Effect |
|---|---|
| **Psychic Protection** | 1 point per level against psychic damage, on all four armour pieces, max level IV in survival, does not conflict with other enchantments |
| **Psychic Deprivation** | weapon enchantment — deals psychic damage on hit |
| **Psychic Drain** | applies the psychic drain effect |

### Items
* **Shadow Sword / Shadow Axe** — the mod's weapons. Charge one to repair its durability at the cost of sanity.
  With a shield in the off hand, the shield takes priority over charging.
* **Shadow refinement** — put a shadow weapon and *Inner Clumps* into an anvil to add refinement levels
  (1 clump = +1 level = +1 psychic damage = 1 XP level, cap 100, no XP penalty, enchantments and durability preserved).
  Hold **Shift** to consume every clump in the anvil at once.
* **Inner Shard / Inner Clump / Inner Core** — crafting materials dropped by inner entities.
* **Stabilizer α / β / γ** — restore sanity and grant a short protection: β blocks the mania true damage,
  γ makes inner entities ignore you (a cooldown applies to each).
* **Garland** — a wearable flower accessory.
* **Spawn eggs** for all three inner entities, grouped with the rest of the mod's items in a dedicated
  creative tab.

### Inner entities (mobs)
Three hostile entities that exist where sanity is low. They drop XP and inner materials, and killing them
restores sanity. **They have no sanity at all**, and confusion and mania do nothing to them.

**⚠️ Psychic damage does not behave on them the way it behaves on you** — there are two different conversions:

| Target | What psychic damage does |
|---|---|
| **players / ordinary mobs** | drains sanity; **the part past zero sanity becomes 1:1 true damage** |
| **inner entities** | **no sanity to drain** ⇒ the whole amount is multiplied by their own resistance factor **and by 2.5**, then applied as true damage |

Example: the **Screaming Crawler** has 20 psychic resistance ⇒ a 0.8 factor ⇒ **100 psychic damage becomes
200 true damage**. At 100 points it would be fully immune (nothing to convert).

| Mob | Trait |
|---|---|
| **Rotting Stalker** | psychic resistance 10 · a tall, silent pursuer |
| **Sneaking Terror** | psychic resistance 15 |
| **Screaming Crawler** | psychic resistance 20 · lights a fuse, swells and explodes; its blast deals psychic damage instead of ordinary explosion damage |

The **Screaming Crawler** actively hunts players whose sanity drops below **20%**. It paths around obstacles
(A\* pathfinding), steps and jumps up ledges, and explodes at close range. Its eyes glow in the dark.

**Two sanity gates apply to it**, so a recovering player is not punished by a mob that is still walking
toward them:

| Your sanity | What the crawler may do |
|---|---|
| **75% and above** | nothing — it cannot track you at all: it refuses you as a target and drops you immediately if it was already chasing |
| **50% – 75%** | it may chase and detonate, but the blast **breaks no blocks** (the damage is unchanged) |
| **below 50%** | normal behaviour: it chases, detonates and the blast destroys terrain as a creeper's would |

**Why the crawler is dangerous:** its explosion deals a large amount of **psychic damage**. That is *not*
"just sanity" — psychic damage drains sanity, and **any damage that goes past zero sanity is converted 1:1
into true damage** (true damage ignores armour, enchantments, shields and status effects). A blast you cannot
"pay for" with sanity therefore hits your health for the remainder — from full sanity it is survivable, at low
sanity it simply kills you.

**The crawler does not hurt other inner entities.** Its explosion resolution skips everything carrying the
`sanitypd:inner_entities` tag, so a skipped inner entity takes neither psychic damage nor overflow true damage —
two inner entities standing together will not kill each other.

Inner entities are still present on the server when you cannot see them, and they still count toward your
passive sanity drain. Sane players cannot see them — unless one has targeted them.


### The thought chain (part one)

* The **Psychic Controller** opens your own Thought Chain: **eighteen slots in two rows of nine** - a
  **Worldview** row and a **Methodology** row.
* **Thoughts** are the items that go in them, in six counting types (Composure, Madness, Servitude,
  Chaos Restraint, Endurance, Hallucination). This part ships sixteen of them.
* A thought deepens as more thoughts of its type sit in the chain; its tooltip shows the current effect
  and, with SHIFT held, the full tier table.
* **Mindsets** switch on once the chain holds enough of a type, and the Mindset Trends panel shows which
  are active.

### The lost memory fragments

* **Four fragments** sit in the creative tab added by this mod. Holding right click charges one with the
  bow-pull pose: the HUD hides, while **your hand and the item you are holding stay visible**.
* A charge sound starts after one second; the instant it ends the screen goes black and a **tape** plays,
  reading the script line by line over a noise band, each line announced by an eject click, ending in a
  white-out.
* The **first viewing of a fragment cannot be skipped**. After that, any key leaves it. Playing a tape
  pauses the game in single player; on a server you are protected from damage the way creative mode
  protects you.
* Every fragment ships a **Chinese and an English script**, and the client language decides which plays.

* **Inspiration:** the tape presentation takes its visual language from the death screen of **Poppy Playtime**. No assets from that game are used - the effect is built entirely from this mod's own code and textures.
## Roadmap

This mod is **under active development** — the current build is the framework, not the finished game.

* **Equipment system** — gear that strengthens or restricts your mind: thinking-enhancement items and
  self-imposed restrictions that change how sanity works for you.
* **A final encounter** — the endgame the whole sanity system is being built toward.
* More inner entities, items and sanity sources as the systems above land.

The alpha label reflects this: the framework is playable, the content on top of it is still coming.
## Commands

All commands need operator permission. `/sanity` is the root.

| Command | Purpose |
|---|---|
| `/sanity get [target]` | read sanity |
| `/sanity set <target> <value>` | set sanity |
| `/sanity add <target> <amount>` | add or subtract sanity |
| `/sanity state <target> <state>` | force a state |
| `/sanity psychic <target> <amount>` | deal psychic damage |
| `/sanity truedamage <target> <amount>` | deal true damage |
| `/sanity resist set\|add\|subtract\|clear\|get` | per-entity psychic resistance |
| `/sanity innerspawn on\|off\|status` | stop only inner entities from spawning |
| `/sanity nospawn on\|off\|status` | stop natural mob spawning |
| `/sanity hint <tier> add\|remove\|list\|clear\|show` | manage the inner-monologue hint pools (mild / severe / deep / **expiry**).<br>**How the tiers are used**: at 25% sanity or below only the *severe* pool is drawn (the deep tier is no longer part of the regular draw); the *deep* tier speaks exactly one randomly picked line during the last 5 seconds before the mania damage starts; while **mania immunity** is held only the *severe* pool is drawn, and during that buff's last 5 seconds the **expiry** pool is shown instead (3 built-in lines, editable through the `expiry` tier) |
| `/sanity config reload` | reload the configuration |

### Multiplayer note: submitting a hint that mentions the one word

Pasting a custom hint that mentions a particular word (any punctuation or spacing between the letters still
counts) is refused, and what happens to you depends on which side of the game you are on:

* **Your own game closes**, in single player and in multiplayer alike. This always affects only the person
  who typed it.
* **A dedicated server never crashes.** It keeps a half of its own: the command is swallowed **without any
  message** and the player who typed it takes lethal damage on the spot. The server process itself is never
  touched, so no world data can be lost this way.
* **A command block or the server console can never trigger it.** With no player behind the command nothing
  happens at all — no crash and no damage. Only a player typing it himself is affected.

## Companion tool: SanityPD Probe (思维探针 / "Mind Probe")

This project also ships an **optional, separate** diagnostic mod, `sanityprobe`. It is **not needed to play**
and changes nothing about the game. It exists so that behaviour only observable on a real client can actually
be verified — a dedicated server cannot prove anything about rendering, HUD or animation.

**Installing it:** drop `sanityprobe-mc1.20-2.18.0.jar` into `mods/` next to this mod. No setup is required.
Remove the jar when you are done — nothing depends on it.

**Where the information goes:**

| Where | What you get |
|---|---|
| `<instance>/logs/sanityprobe.log` | one line per event, tagged, e.g. `[SHIELD-26-C]`, `[CHASE-27]`, `[HUD-27]` |
| `logs/latest.log` | the same lines mirrored as `[PROBE] ...` |
| On-screen overlay (top-left) | the current readings; **F4** hides or shows it (rebindable in Controls) |

**Commands** (client-side, no operator permission needed):

| Command | Effect |
|---|---|
| `/sanityprobe on` | enable logging |
| `/sanityprobe off` | disable logging (the overlay stays visible) |
| `/sanityprobe mark <text>` | append a `MARK` line, to timestamp the log around something you just did |
| `/sanityprobe` | print the mod's usage line |

**What it observes** — one group per mechanism:

* walk / float — the crawler's walk-animation decision and ground contact
* sound — every sound the mod plays, its distance, and whether its resource resolves
* stabilizer — item cooldowns and status-effect start/end
* sneak HUD — the sneak sanity readout and its translation keys
* shield — whether an off-hand shield takes the right-click instead of the weapon charging
* easter egg — whether the "do not say that name" easter egg fires as intended
* enchant / refine — psychic-protection numbers and the anvil shadow-refinement result
* splash — the title-screen splash text pool
* AI / chase — the crawler's target, running goals, A\* path and movement commands
* HUD layout — the overlay's own line count, so overlapping text is immediately visible

**Sending feedback:** attach `logs/sanityprobe.log`. It is written so that this single file is enough to see
what happened; the overlay is only a convenience.

## Configuration

A fully commented config file is generated at `config/sanitypd/default.toml`: passive and active sanity sources,
item and block lists, multiplayer and client options (sanity indicator, inner monologue, blood-tendril overlay,
post-processing, sound).

## Reporting bugs

Please include your Minecraft, Forge and GeckoLib versions, the mod version, and the relevant part of
`logs/latest.log` or the crash report, and open an issue on the project page:
<https://github.com/Yaojihua/sanity-psychological-devastation>

## Credits

* Original project: **Sanity: Descent Into Madness** by **croissantnova** — MIT. Mechanics are derived from it;
  the original license and copyright notices are preserved in full.
* Original art / audio collaboration: **toujourspareil**, **Zapsplat**. Sound effects inherited from the
  original project keep their own terms; the Zapsplat standard license is linked in
  `NOTICE-sanitypd.txt` inside the jar.
* This version: **Yaojihua**.
* **AI-assisted development:** parts of this mod were developed with **DeepSeek Harness (dsh)**.
  All design decisions, in-game testing and final review were done by the author.

**Sound effects added by this project.** Every file, its author and its license are listed in
[`ASSETS-CREDITS.md`](ASSETS-CREDITS.md). Two entries there matter to anyone redistributing this mod:

* **Attribution required — CC-BY.** The horror sting 「恐怖」 is by **Taira Komori**, licensed **CC-BY**, via
  Freesound (source <http://tairakomori.jpn.org/freesounden.html>, license text
  <https://creativecommons.org/licenses/by/4.0/>). It was **modified** for this mod — trimmed and converted
  to OGG — and that has to be said alongside the credit.
* **Courtesy credit — CC0.** The tape sounds — 「Charged laser」 by **LegoLunatic**, 「盒式磁带机弹出」 by
  **jpkweli** and 「空白磁带嘶嘶噪声」 by **骨质人** — are released under **CC0**, which requires no
  attribution and allows commercial use. They are credited here because it is the right thing to do, not
  because the license asks for it.

**This mod is an unofficial derivative of the original project, and is not affiliated with, endorsed by or
supported by its author.** Bug reports about this mod belong on this project's issue tracker.

## License

The **code** of this mod is MIT — see [LICENSE](LICENSE), which also carries the original author's copyright
line. Redistribution must keep the original author's attribution.

**All other assets — textures, models, animations and the story text — are all rights reserved** unless a
file says otherwise. A permissive code license does not cover them, and it does not cover the sound effects
either: those keep the licenses listed in [`ASSETS-CREDITS.md`](ASSETS-CREDITS.md).
