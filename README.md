# Sanity: Psychological Devastation

> ### ⚠️ ALPHA TEST BUILD
> This is an **alpha test version**. Mechanics, numbers, config keys and commands may still change between
> builds, and things may be unbalanced or broken. Back up your worlds before installing it.
> This build is the **second part test build** — the rest of the thought chain and the rest of the
> memory-fragment tapes are in; the boss stages and the ending are not.

A sanity system for Minecraft — new mechanics, items, mobs and commands built on top of the original
**Sanity: Descent Into Madness** by [croissantnova](https://github.com/croissantnova/SanityDescentIntoMadness) (MIT).

Sanity is a point-based mental state. Every living entity has it. Low sanity corrupts what you see, what you
hear, and eventually what comes for you.

---

## What is in this repository

This is a two-project repository. Each folder is a self-contained Gradle project.

| Folder | What it is |
|---|---|
| **`sanitypd/`** | **The mod itself.** This is the thing you download and play. Start with [`sanitypd/README.md`](sanitypd/README.md), which covers features, requirements, installation, configuration and every command. |
| **`sanityprobe/`** | An **optional, separate diagnostic mod**. It is not needed to play and changes nothing about the game; it exists so behaviour that only shows up on a real client (rendering, HUD, animation, sound) can actually be verified. See [`sanityprobe/README.md`](sanityprobe/README.md). |

Also at the top level: [`CHANGELOG.md`](CHANGELOG.md) (release notes for both projects),
[`ASSETS-CREDITS.md`](ASSETS-CREDITS.md) (the per-file source and licence of every sound) and
[`LICENSE`](LICENSE) (MIT, with the upstream copyright preserved).

**Looking for the download?** Use the [Releases](../../releases) page. The files there are built from this
source.

---

## Quick start

**To play:**

1. Install **Minecraft Forge** for **1.20.1** and run it once.
2. Put **GeckoLib** and `sanitypd-mc1.20-1.21.7.jar` into your `mods/` folder.
   *GeckoLib is required.* Use **4.2** on Forge 46, or **4.8.4** on Forge 47.1+ — a mismatched pair makes the
   game report a missing `geckolib` dependency, but the real cause is the Forge version.
3. Launch the game. The config file is generated on first run.

**To build from source:**

```bash
cd sanitypd
./gradlew build          # Windows: gradlew.bat build
# the jar lands in build/libs/
```

Requires **Java 17** (the same version Minecraft 1.20.1 and Forge 46/47 run on). The dev client and a
dedicated server can both be started with `./gradlew runClient` and `./gradlew runServer`.

---

## Support

Bug reports and feedback are welcome — please include:

* the **mod version** and your **Forge** and **GeckoLib** versions,
* the relevant part of `logs/latest.log`,
* and, if the problem is something visual, `logs/sanityprobe.log` together with a screenshot.

The full feature list, configuration reference and command list live in
[`sanitypd/README.md`](sanitypd/README.md).

---

## What is in this build

**Second part test build.** The two headline pieces of work are the rest of the thought chain and the rest of
the memory fragments; everything in this build is described in full in
[`sanitypd/README.md`](sanitypd/README.md) and listed release by release in [`CHANGELOG.md`](CHANGELOG.md).

* **In:** the Thought Chain as it stands — eighteen slots in two rows, six counting types, twenty-four
  thoughts, thought containers and the Thought Transcription Table, tiered effects, mindsets and the
  Mindset Trends panel, opened with the Psychic Controller.
* **In:** nine lost memory fragments — the four the story is told in, and five more handed out during play —
  each a scripted tape presentation with its own Chinese and English script, unskippable on the first
  viewing.
* **In:** the Non-Suicidal Self-Injury thought and its mechanic, plus Shadow Gunpowder and Shadow TNT.
* **Not in:** the boss and everything that follows the fragments — the world rupture, the stage boss and
  the final form. No date is promised for them.
* **Not in:** the rest of the story beyond the fragments.

* **Inspiration:** the tape presentation takes its visual language from the death screen of **Poppy Playtime**. No assets from that game are used - the effect is built entirely from this mod's own code and textures.

## Credits

* Original project: **Sanity: Descent Into Madness** by **croissantnova** (MIT) — this mod's mechanics are
  derived from it, and the original license and copyright notices are preserved in full.
* Original art / audio collaboration: **toujourspareil**, **Zapsplat**. Sound effects inherited from the
  original project keep their own terms; the Zapsplat standard license is linked in
  `NOTICE-sanitypd.txt` inside the jar.
* This version: **Yaojihua**.
* AI-assisted development: parts of this mod were developed with **DeepSeek Harness** ("dsh").
  All design decisions, in-game testing and final review were done by the author.

**Sound effects added by this project.** Every file, its author and its licence are listed in
[`ASSETS-CREDITS.md`](ASSETS-CREDITS.md). Credit is a condition of two of those licences, so it is repeated
here:

* **CC-BY 4.0** — the horror sting 「恐怖」 (*terror.mp3*) and the tinnitus pair, all three by **Taira Komori**,
  via Freesound: [217148](https://freesound.org/people/Taira%20Komori/sounds/217148/),
  [217155](https://freesound.org/s/217155/), [217154](https://freesound.org/s/217154/), licence
  [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/). All three were **modified** for this mod:
  converted to OGG and downmixed to mono.
* **CC-BY 3.0** — the woman's scream (*screaming_woman_horror.mp3*) by **GabrielaUPF**, via Freesound
  ([220302](https://freesound.org/people/GabrielaUPF/sounds/220302/), licence
  [CC-BY 3.0](https://creativecommons.org/licenses/by/3.0/)). **Modified** for this mod: converted to OGG.
* The other recordings this project adds are **CC0** — no attribution required — and `ASSETS-CREDITS.md`
  names their authors anyway.

**This mod is an unofficial derivative of the original project, and is not affiliated with, endorsed by or
supported by its author.** Bug reports about this mod belong on this project's issue tracker.

## License

The **code** of this mod is MIT — see [LICENSE](LICENSE). The upstream copyright notice is preserved in
full. **All other assets — textures, models, animations and the story text — are all rights reserved**
unless a file says otherwise; a permissive code license does not cover them, and it does not cover the
sound effects either (those keep the licenses listed in [`ASSETS-CREDITS.md`](ASSETS-CREDITS.md)).
