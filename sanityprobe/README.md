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
