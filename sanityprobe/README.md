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

One **tagged group per mechanism** (`ProbeLog` writes `[TAG] message`). The startup banner names the groups
armed in the build you are running; this is what the tags mean.

| Tag(s) | What it checks |
|---|---|
| `[WALK-S]` `[WALK-S VERDICT]` `[ROAR-S]` | server side: is the crawler actually walking — walk animation, ground contact, and the roar it plays |
| `[WALK-C]` `[FLOAT-C]` | client side: walk-animation detection and floating appearance |
| `[CHASE-27]` `[AI-27]` `[AI-27-TL]` `[AI-27-NEWPATH]` `[AI-27-CMD]` | the crawler's target, the goals it is running, the A\* path it found, and the movement commands really issued |
| `[CRAWLER-29]` | the sanity gates that decide what a screaming crawler is allowed to do |
| `[SOUND-C]` | sanitypd sound playback: every sound, its distance, and whether the resource resolves |
| `[TAPE-49]` | the lost-memory-fragment tape: the sequence's state, the handle behind its hiss bed, and whether everything that is not the tape was really silenced |
| `[TAPE-GATE-53]` | the tape's gates, and the hidden fragment's allowance |
| `[THOUGHT]` | the thought chain, client side |
| `[THOUGHT41-29]` | stress-induced analgesia, irritability and conversion disorder, read off the running mod |
| `[THOUGHT50]` `[THOUGHT50-C]` | the Stockholm/Overdose batch, and the sentences their tooltips really print |
| `[THOUGHT-ATTR]` | the attribute modifiers thoughts and mindsets put on the player |
| `[BLOCK-61]` | the thought transcription table, checked from the client |
| `[VISION-58]` | the hallucination batch |
| `[DEPERSON-44]` | Depersonalization and its sanity recovery ceiling |
| `[STAB-C]` | the mood stabilizers |
| `[SNEAK-HUD]` | the sanity value drawn above the brain meter while sneaking |
| `[SHIELD-26-C]` | right-click priority between a shadow weapon and an off-hand shield |
| `[ENCH-26-C]` | the psychic protection enchantment |
| `[REFINE-26-C]` | shadow weapon refinement |
| `[HINT-TIER]` `[HINT-24]` | the inner-monologue tiers and their sizes |
| `[HINTWIN-28]` | the warning-window rules of the inner monologue |
| `[TYPEHINT-43]` | the per-type inner-voice pools |
| `[VOICE-32]` | the extra inner-voice pool and the sound it triggers |
| `[SELF-INJURY-75]` | the non-suicidal self-injury act's client timeline |
| `[HINT-POOL-76]` | the self-injury hint pool |
| `[BLOOD-78]` | the blood decal layer |
| `[SHADOW-TNT-63]` | shadow TNT |
| `[SCREAM-52]` | the low-sanity scream |
| `[MACARON-60]` | the hidden fragment of the macaron's name |
| `[SELF-59]` | the hidden fragment of the player's own name |
| `[VOID-64]` | the hidden fragment of the inner void |
| `[VESSEL-65]` | the hidden fragment of the vessel |
| `[UNBOUND-66]` | the void lifting its limits on the player's understanding |
| `[EGG-25]` `[EGG-25-C]` | the three inner-mob spawn eggs |
| `[EGG-26-C]` `[EGG-33]` | the "do not say that name" easter egg, and its client/server split |
| `[DEATH]` `[IMMUNE-24]` `[DEATH-FLAVOUR]` `[DEATH-KEY]` | death messages and the damage chain |
| `[IMPACT-24]` `[MELEE-24]` `[CMD-24]` `[LANG-24]` | psychic resistance, the inner-mob damage bonus, the commands and the language keys |
| `[P26]` | the aggregate checklist run that writes the client-side groups in one go |
| `[SPLASH-26]` `[SPLASH-26-SAMPLE]` | the mod's title-screen splash lines |
| `[HUD-27]` | the overlay's own layout, and any draw failure inside it |
| `[SELFTEST]` `[SYS]` `[SYSMSG]` `[PLAYERDIE]` `[MARK]` | the probe's startup self-test, its own messages, server-side system broadcasts and the player death entry point, and the marker line written by `/sanityprobe mark` |

## Usage

* **F4** hides or shows the overlay.
* `/sanityprobe on|off|mark <text>` toggles logging and drops a marker line into the log.
* Remove the jar when you are done — nothing depends on it.

## Requirements

Minecraft 1.20 / 1.20.1, Forge 46+, and [Sanity: Psychological Devastation](../README.md) for the readings to mean anything.

## License

MIT — see [LICENSE](../LICENSE).


## About the labels in this project

Class names such as `Round26Probe` and labels such as `[P26]`, `[SPLASH-26-SAMPLE]`,
`[THOUGHT41-29]` or `[TAPE-49]` look like build tags, and that is what they are: this probe grew one
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
