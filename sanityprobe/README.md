# SanityPD Probe (思维探针 / Mind Probe)

> ## ⚠️ ALPHA — developer tool, not a gameplay mod
> This is a **diagnostic probe** published for transparency and for people who want to verify the
> behaviour of [Sanity: Psychological Devastation](../README.md) on their own machine.
> It is **not** needed to play, and it changes nothing about the game.

A small client-side diagnostic mod (`modid: sanityprobe`) that logs what the main mod is doing, so that
client-side behaviour — which a dedicated server cannot observe — becomes verifiable.

## What it does

* Writes one line per event to `<instance>/logs/sanityprobe.log` (also mirrored into `latest.log` as `[PROBE]` lines).
* Draws a small overlay in the top-left corner of the screen with the current readings.
* Registers **no** items, blocks, entities or world content, and changes no gameplay values.

## What it observes

| Group | What it checks |
|---|---|
| walk / float | the crawler's walk-animation decision (GeckoLib formula), position and ground contact |
| sound | every sanitypd sound played, its distance, and whether the resource actually resolves |
| stabilizer | item cooldowns and status-effect start/end |
| sneak HUD | the sneak sanity readout and its translation keys |
| shield | whether an off-hand shield takes the right-click instead of the weapon charging |
| easter egg | whether the "do not say that name" easter egg fires as intended |
| enchant / refine | psychic-protection numbers, and the anvil shadow-refinement result |
| splash | the title-screen splash text pool |
| AI / chase | the crawler's target, running goals, A\* path and movement commands |
| HUD layout | the overlay's own line count, so overlapping text is impossible |

## Usage

* **F4** hides or shows the overlay.
* `/sanityprobe on|off|mark <text>` toggles logging and drops a marker line into the log.
* Remove the jar when you are done — nothing depends on it.

## Requirements

Minecraft 1.20 / 1.20.1, Forge 46+, and [Sanity: Psychological Devastation](../README.md) for the readings to mean anything.

## License

MIT — see [LICENSE](../LICENSE).


## About the labels in this project

Class names such as `Round26Probe` and labels such as `[P26]`, `[SPLASH-26-SAMPLE]` or
`[THOUGHT41-29]` look like build tags, and that is what they are: this probe grew one
feature set at a time, and each set got its own group letter and tag.

They are **kept on purpose**:

* the probe registers each group **by class name** - the reflective lookup that arms a
  group would break if the class were renamed;
* the tags are what existing logs and notes quote, so renaming them would make old
  evidence unreadable;
* they appear in the **probe** (a diagnostic tool), not in the mod itself.

Read them as historical group identifiers.

（中文：本工程里的 `Round26Probe`、`[P26]`、`[THOUGHT41-29]` 之类标签是**历史分组代号** ——
探针按类名注册并反射加载，改名会直接打断它；历史日志也引用这些标签，改了旧证据就读不了；
且它们只出现在**探针**（诊断工具）里。故**有意保留**。）
