# Sanity: Psychological Devastation

> ## ⚠️ ALPHA TEST BUILD
> This is an **alpha test version**. Mechanics, numbers, config keys and commands may still change between
> builds, and things may be unbalanced or broken. Back up your worlds before installing it.
> This build is the **second part test build**: the rest of the thought chain and the rest of the
> memory-fragment tapes are in; the boss stages and the ending are not.
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
| JEI | optional — gives the transcription table its own recipe page |

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
* Sanity is drained by **psychic damage**, which is resolved on its own sanity track: it drains sanity, and
  only the part that runs past zero sanity is converted 1:1 into true damage. Because it never goes through
  the ordinary armour calculation, **armour, vanilla resistance and vanilla enchantments cannot reduce it**;
  what does reduce it is **blocking with a shield**, the mod's own **psychic resistance** (from stabilizers
  and thoughts) and the *Psychic Protection* enchantment.

### Status effects
| Effect | What it does |
|---|---|
| **Confusion** | the low-sanity state |
| **Mania** | rapid sanity loss; can be lethal on its own |
| **Mania Immunity** | blocks the mania true damage; its last 5 seconds show a separate inner-voice warning |
| **Inner Immunity** | inner entities will not attack you, and the crawler will not detonate; its last 5 seconds show the same warning pool |
| **Psychic Drain** | ongoing psychic damage over time |
| **Sanity Recovery** | restores 1 sanity per second for its 25 seconds; a macaron grants it |

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
* **Mood Stabilizer α / β / γ / δ** — each changes your sanity and grants a short protection: α restores 35 sanity
  instantly (15 s cooldown), β blocks the health loss from a late-stage mania breakdown for 180 s (300 s
  cooldown), γ makes inner entities ignore you for 60 s (120 s cooldown), and **δ takes 40 sanity away
  instantly** (5 s cooldown) — the one for reaching low sanity on purpose.
* **Garland** — a wearable flower accessory.
* **Macarons** — eight colours; eating one grants **Sanity Recovery** and vanilla Regeneration.
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


### The thought chain

* The **Psychic Controller** opens your own Thought Chain: **eighteen slots in two rows of nine** - a
  **Worldview** row and a **Methodology** row. A thought of the **Hallucination** category may sit in either
  row.
* **Thoughts** are the items that go in them. Each belongs to one of **six counting types** (Composure,
  Madness, Servitude, Chaos Restraint, Endurance, Hallucination), and a few belong to two types and count
  toward both. **Twenty-four thoughts** ship today.
* A thought deepens as more thoughts of its type sit in the chain; its tooltip shows the current effect
  and, with SHIFT held, the full tier table.
* **Mindsets** switch on once the chain holds enough of a type, and the Mindset Trends panel shows which
  are active.

### Thought containers and the transcription table

* A **Thought Container** is a thought's carrier form. The plain one is crafted from three glass and an
  **Inner Clump** (`g c g` / `. g .`); the four type variants - **Thought Container: Composure**,
  **Thought Container: Madness**, **Thought Container: Servitude**, **Thought Container: Endurance** -
  cannot be crafted, only made at the table.
* The **Thought Transcription Table** takes **three thoughts of one type**, the plain container and an
  **Inner Core**, and returns that type's variant container for **2 experience levels**.
* Put **one graded thought** in the material slots with its type's variant container and an Inner Core, and
  it returns the same thought **empowered** - the description gains the gold line "This thought has been
  empowered" - for **3 experience levels**. An empowered thought cannot be fed back in.
* Hallucination and Chaos Restraint have no variant container, so nothing of those two types can be
  transcribed.
* The window shows what it will cost before you take anything, and with **JEI** installed the same rows are
  browsable as a recipe page.

### The lost memory fragments

* **Nine fragments** exist. The **four** the story is told in sit in the creative tab added by this mod; the
  other five are handed out during play, and what hands them out is for you to find.
* Holding right click charges one with the bow-pull pose: the HUD hides, while **your hand and the item you
  are holding stay visible**.
* A charge sound starts after one second; the instant it ends the screen goes black and a **tape** plays,
  reading the script line by line over a noise band, each line announced by an eject click, ending in a
  white-out.
* The **first viewing of a fragment cannot be skipped**. After that, any key leaves it. Playing a tape
  pauses the game in single player; on a server you are protected from damage the way creative mode
  protects you.
* Every fragment ships a **Chinese and an English script**, and the client language decides which plays.
* Watch **eight of the nine** and seven of the tapes change: the words they had hidden become readable, and
  you are told once that it happened.

* **Inspiration:** the tape presentation takes its visual language from the death screen of **Poppy Playtime**. No assets from that game are used - the effect is built entirely from this mod's own code and textures.

### Shadow gunpowder and Shadow TNT

* **Shadow Gunpowder** comes from the **Screaming Crawler** and from nothing else - one or two of it at a
  time, and only when a player lands the kill.
* **Shadow TNT** is crafted from it in the vanilla TNT layout, with sand or red sand. Its blast deals
  **psychic damage that does not fall off with distance**, breaks blocks the way TNT does, and kills with a
  message that names whoever lit the fuse - or its own wording when that person is you.

## Roadmap

This mod is **under active development** — the systems below are still to come.

* **Equipment system** — thinking-enhancement items that change how sanity works for you.
* **A final encounter** — the endgame the whole sanity system is being built toward.
* More inner entities, items and sanity sources as the systems above land.

The alpha label reflects this: what is here is playable, and more content is still coming.

## Commands

`/sanity` is the root. Everything below needs **operator permission** except two things, which stay open to
any player so they still work in a hardcore world with cheats off: `/sanity selfinjury` on yourself, and the
`/sanity hint ...` pool editor with which you write your own inner lines.

| Command | Purpose |
|---|---|
| `/sanity get [target]` | read sanity |
| `/sanity set <target> <value>` | set sanity |
| `/sanity add <target> <amount>` | add or subtract sanity |
| `/sanity state <target> <state>` | force a state |
| `/sanity psychic <target> <amount>` | deal psychic damage |
| `/sanity truedamage <target> <amount>` | deal true damage |
| `/sanity selfinjury [<targets>]` | make the target perform the act once, right now (yourself by default; naming somebody else is operator-only) |
| `/sanity resist set\|add\|subtract\|clear\|get` | per-entity psychic resistance |
| `/sanity innerspawn on\|off\|status` | stop only inner entities from spawning |
| `/sanity nospawn on\|off\|status` | stop natural mob spawning |
| `/sanity hint <tier> add\|remove\|list\|clear\|show` | manage the inner-monologue hint pools (mild / severe / deep / **expiry** / **self_injury**).<br>**How the tiers are used**: at 25% sanity or below only the *severe* pool is drawn (the deep tier is no longer part of the regular draw); the *deep* tier speaks exactly one randomly picked line during the last 5 seconds before the mania damage starts; while **mania immunity** is held only the *severe* pool is drawn, and during that buff's last 5 seconds the **expiry** pool is shown instead (3 built-in lines, editable through the `expiry` tier); the **self_injury** pool answers a self-inflicted wound one tick after it lands and holds the centre of the screen for six seconds, during which no other pool is drawn (8 built-in lines) |
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

**Installing it:** drop `sanityprobe-mc1.20-2.40.0.jar` into `mods/` next to this mod. No setup is required.
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

**What it observes** — one group per mechanism, each writing its own tagged lines. The groups armed in the
build you are running are named on the probe's startup banner; the tags that appear in the log include:

* `[WALK-S]` `[WALK-C]` `[FLOAT-C]` — is the crawler actually walking: its walk-animation decision, ground
  contact and floating appearance
* `[AI-27]` `[AI-27-TL]` `[AI-27-NEWPATH]` `[AI-27-CMD]` `[CHASE-27]` — the crawler's target, the goals it is
  running, the A\* path it found, and the movement commands that were really issued
* `[CRAWLER-29]` — the sanity gates that decide what a crawler is allowed to do
* `[SOUND-C]` — every sound the mod plays, its distance, and whether the resource resolves
* `[TAPE-49]` — the fragment tape: the sequence's state, the handle behind its hiss, and whether everything
  that is not the tape was really silenced
* `[TAPE-GATE-53]` — the tape's gates, and the hidden fragment's allowance
* `[THOUGHT]` `[THOUGHT41-29]` `[THOUGHT50]` `[THOUGHT50-C]` `[THOUGHT-ATTR]` — the thoughts themselves:
  registry ids, counting types, the constants the mechanics read, the sentences the tooltips really print,
  and the attribute modifiers thoughts and mindsets put on you
* `[BLOCK-61]` — the transcription table, checked from the client
* `[VISION-58]` — the hallucination batch
* `[DEPERSON-44]` — the Depersonalization thought and its sanity-recovery ceiling
* `[STAB-C]` — the mood stabilizers
* `[SNEAK-HUD]` — the sanity value drawn above the brain meter while sneaking
* `[SHIELD-26-C]` — right-click priority between a shadow weapon and an off-hand shield
* `[ENCH-26-C]` — the psychic protection enchantment
* `[REFINE-26-C]` — the anvil shadow-refinement result
* `[HINT-TIER]` `[HINT-24]` `[HINTWIN-28]` — the inner-monologue pools, their sizes, and the warning-window
  rules
* `[TYPEHINT-43]` — the per-type inner-voice pools
* `[VOICE-32]` — the extra inner voice and the sound it triggers
* `[SELF-INJURY-75]` `[HINT-POOL-76]` — the self-injury act's client timeline, and its own hint pool
* `[BLOOD-78]` — the blood decals left on the ground
* `[SHADOW-TNT-63]` — Shadow TNT
* `[SCREAM-52]` — the low-sanity scream
* `[MACARON-60]` `[SELF-59]` `[VOID-64]` `[VESSEL-65]` — the four fragments that are handed out during play
  rather than taken from the tab
* `[UNBOUND-66]` — the tape lines that become readable once enough fragments have been watched
* `[EGG-25]` `[EGG-25-C]` `[EGG-26-C]` `[EGG-33]` — the spawn eggs and the "do not say that name" easter egg
* `[DEATH]` `[IMMUNE-24]` `[DEATH-FLAVOUR]` — the death messages and the damage chain
* `[IMPACT-24]` `[MELEE-24]` `[CMD-24]` `[LANG-24]` — psychic resistance, the inner-mob damage bonus, the
  commands and the language keys
* `[P26]` — the aggregate checklist run that writes the client-side groups in one go
* `[SPLASH-26]` — the title-screen splash lines
* `[HUD-27]` — the overlay's layout engine, and any draw failure inside it
* `[SELFTEST]` `[SYS]` `[SYSMSG]` — the probe's own startup self-test, its system messages, and every
  server-side system broadcast

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

* **Original mod**: [Sanity: Descent Into Madness](https://github.com/croissantnova/SanityDescentIntoMadness) by **croissantnova** (MIT) - the licence and notice ship inside the jar.
* **Attribution required (CC-BY)**: the low-sanity scare uses a scream by **GabrielaUPF** ([CC-BY 3.0](https://creativecommons.org/licenses/by/3.0/)); the horror sting and the tinnitus pair use recordings by **Taira Komori** ([CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/)). Source pages are listed per file in ASSETS-CREDITS.md inside the jar.
* **Inherited audio**: credited to **toujourspareil** and **Zapsplat** as in the original project.
* The **code** is MIT; **textures, models, animations, story text and sounds are all rights reserved**.

## License

The **code** of this mod is MIT — see [LICENSE](LICENSE), which also carries the original author's copyright
line. Redistribution must keep the original author's attribution.

**All other assets — textures, models, animations and the story text — are all rights reserved** unless a
file says otherwise. A permissive code license does not cover them, and it does not cover the sound effects
either: those keep the licenses listed in [`ASSETS-CREDITS.md`](ASSETS-CREDITS.md).
