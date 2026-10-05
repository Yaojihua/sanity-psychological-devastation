# Sanity: Psychological Devastation

> ### ⚠️ ALPHA TEST BUILD
> This is an **alpha test version**. Mechanics, numbers, config keys and commands may still change between
> builds, and things may be unbalanced or broken. Back up your worlds before installing it.
> This build is the **first part test build** — the thought chain (part one) and the memory-fragment tapes
> are in; the boss stages and the rest of the story are not.

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

Also at the top level: [`CHANGELOG.md`](CHANGELOG.md) (release notes for both projects) and
[`LICENSE`](LICENSE) (MIT, with the upstream copyright preserved).

**Looking for the download?** Use the [Releases](../../releases) page, or the CurseForge project page. The
files on a Releases page are built from this source.

---

## Quick start

**To play:**

1. Install **Minecraft Forge** for **1.20.1** and run it once.
2. Put **GeckoLib** and `sanitypd-mc1.20-1.8.0.jar` into your `mods/` folder.
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

**First part test build.** The two headline features are the first part of the thought chain and the
memory-fragment tapes; both are described in full in [`sanitypd/README.md`](sanitypd/README.md) and in
[`CHANGELOG.md`](CHANGELOG.md).

* **In:** the Thought Chain (part one) — eighteen slots in two rows, six counting types, sixteen thoughts,
  tiered effects, mindsets and the Mindset Trends panel, opened with the Psychic Controller.
* **In:** four lost memory fragments, each a scripted tape presentation with its own Chinese and English
  script, unskippable on the first viewing.
* **Not in:** the boss and everything that follows the fragments — the world rupture, the stage boss and
  the final form. No date is promised for them.
* **Not in:** the rest of the thought chain, and the rest of the story beyond the four fragments.

## Credits

* Original project: **Sanity: Descent Into Madness** by **croissantnova** (MIT) — this mod's mechanics are
  derived from it, and the original license and copyright notices are preserved in full.
* Original art / audio collaboration: **toujourspareil**, **Zapsplat**. Sound effects inherited from the
  original project keep their own terms; the Zapsplat standard license is linked in
  `NOTICE-sanitypd.txt` inside the jar.
* This version: **Yaojihua**.
* AI-assisted development: parts of this mod were developed with **DeepSeek Harness** ("dsh").
  All design decisions, in-game testing and final review were done by the author.

**Sound effects added by this project.** Every file, its author and its license are listed in
[`ASSETS-CREDITS.md`](ASSETS-CREDITS.md). Two entries there matter to anyone redistributing this mod:

* **Attribution required — CC-BY.** The horror sting 「恐怖」 is by **Taira Komori**, licensed **CC-BY**,
  via Freesound (source <http://tairakomori.jpn.org/freesounden.html>, license text
  <https://creativecommons.org/licenses/by/4.0/>). It was **modified** for this mod — trimmed and
  converted to OGG — and that has to be said alongside the credit.
* **Courtesy credit — CC0.** The tape sounds — 「Charged laser」 by **LegoLunatic**, 「盒式磁带机弹出」 by
  **jpkweli** and 「空白磁带嘶嘶噪声」 by **骨质人** — are released under **CC0**, which requires no
  attribution and allows commercial use. They are credited here because it is the right thing to do, not
  because the license asks for it.

**This mod is an unofficial derivative of the original project, and is not affiliated with, endorsed by or
supported by its author.** Bug reports about this mod belong on this project's issue tracker.

## License

The **code** of this mod is MIT — see [LICENSE](LICENSE). The upstream copyright notice is preserved in
full. **All other assets — textures, models, animations and the story text — are all rights reserved**
unless a file says otherwise; a permissive code license does not cover them, and it does not cover the
sound effects either (those keep the licenses listed in [`ASSETS-CREDITS.md`](ASSETS-CREDITS.md)).
