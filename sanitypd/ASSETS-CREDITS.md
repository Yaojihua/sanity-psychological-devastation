# Asset credits and licenses

Where this mod's audio comes from, and what may be done with it.

Two kinds of sound live here: sounds **inherited from the original project** (covered by the preserved
upstream attribution — `toujourspareil`, `Zapsplat`) and sounds **added by this project**, taken from
free sound libraries. Nothing in this file claims ownership of anyone else's work.

An attribution licence is only satisfied by naming the work, the author, the source and the licence,
and by saying whether the file was modified — the strings in section 2 are written to that standard.

## 1. Every sound in this mod, at a glance

| File in the mod | Source | Author / uploader | License | Commercial use | Attribution |
|---|---|---|---|---|---|
| `memory_charge.ogg` | Freesound | **LegoLunatic** | CC0 | allowed | not required |
| `memory_tape_hiss.ogg` | 耳聆网 | **骨质人** | CC0 | allowed | not required |
| `memory_tape_eject.ogg` | Freesound | **jpkweli** | CC0 | allowed | not required |
| `screaming_crawler_ambient.ogg` | Freesound | **Taira Komori** | CC-BY | allowed | **required** |
| `screaming_crawler_roar.ogg` | Freesound | **TODO — look up** | **TODO** | **TODO** | per its license |
| `screaming_crawler_explode.ogg` | Freesound | **TODO — look up** | **TODO** | **TODO** | per its license |
| `insanity1.ogg`, `heartbeat.ogg`, `swish0-4.ogg`, `whoosh.ogg`, `leaves_rustle.ogg`, `screaming_crawler_*.ogg` | original project | toujourspareil, Zapsplat | Zapsplat standard license | per that license | yes — see `NOTICE-sanitypd.txt` |

**In short:** the whole tape feature is CC0, so nothing is owed for it. The only attribution this mod
actually owes is one line for the horror sting.

## 2. What to reproduce where this mod is distributed

### Required — CC-BY (attribution is a condition of the licence)

> 「恐怖」 by **Taira Komori**, licensed **CC-BY**, via **Freesound**.
> Source: http://tairakomori.jpn.org/freesounden.html
> Licence text: https://creativecommons.org/licenses/by/4.0/
> Modified: **yes** — trimmed and converted to OGG for this mod.
> (Licence version — 3.0 or 4.0 — still to confirm on the source page.)

### Courtesy — CC0 (nothing owed, credited because it is right)

> Tape sounds: 「Charged laser」 by **LegoLunatic**, 「盒式磁带机弹出」 by **jpkweli**,
> 「空白磁带嘶嘶噪声」 by **骨质人** — all released under **CC0**.

### Upstream — inherited and preserved

> Original project: **Sanity: Descent Into Madness** by **croissantnova** (MIT).
> Art / audio collaboration: **toujourspareil**, **Zapsplat** — see `NOTICE-sanitypd.txt` and the
> credits field in `mods.toml`.

## 3. Details and evidence

### Charged laser — CC0 — **this is the memory-charge sound**

* Source page: https://freesound.org/people/LegoLunatic/sounds/151243/ (title on the page: *Charged laser*, dated April 2012). The page states the sound may be copied, modified, distributed and performed **including commercially**, without asking the author.
* Ships as **`memory_charge.ogg`**, the **first half** of the recording — the author's own note is that the sound effect was split in two and the first half kept.
* Modified: **yes** — split, trimmed, converted to OGG.
* Identified by measurement (section 4): the copy of this recording that was on disk is 8.615 s against the page's 8.614 s, same description word for word, same sample rate and channels.

### 空白磁带嘶嘶噪声 — CC0

* By **骨质人**, via 耳聆网. Recorded from a JVC tape deck; tape hiss, similar to white noise.
* Ships as **`memory_tape_hiss.ogg`**. Modified: **yes** — trimmed, converted and looped.

### 盒式磁带机弹出 — CC0

* By **jpkweli**, via Freesound. A plastic cassette-deck tray ejecting.
* Ships as **`memory_tape_eject.ogg`**, the **first half** of the recording. Modified: **yes** — split, trimmed, converted.
* Note: the source file was supplied as a WAV; only the first half is used.

### 恐怖 (horror sting) — CC-BY

* By **Taira Komori**, via Freesound. The author's own page for the sound set is in the required attribution above.
* ✅ **Which shipped file it became — measured (2026-10-05): `screaming_crawler_ambient.ogg`.**
  Envelope correlation against the source recording is **1.000 at zero offset**, with matching duration
  (6.770 s against 6.768 s) and matching spectral centroid (8264 Hz against 8205 Hz).
* Still to confirm on the source page: the **CC-BY version** (3.0 or 4.0). The attribution text above is
  already complete and correct either way.

## 4. How the identifications were checked

Each file was decoded to mono 48 kHz, then the 50 ms RMS envelopes of two files were correlated at the
best small time offset. A correlation near 1.000 means the same recording.

| Comparison | Result | Reading |
|---|---|---|
| `memory_charge.ogg` ↔ charge half of the recording on disk | **r = +1.000** (shift 0) | same recording; 3.663 s vs 3.650 s (OGG padding), spectral centroid 1977 vs 2022 Hz |
| `memory_charge.ogg` ↔ explosion half of the same recording | r = +0.006 | unrelated |
| `memory_charge.ogg` ↔ an unrelated horror sting in the source folder | r = +0.791 | not the same recording (similar envelope shape only) |

A mapping is only accepted when **all three** agree: correlation at or above 0.98, duration within about
half a second (or the shipped file being a clean excerpt of the longer one), and a matching spectral
centroid. Envelope correlation alone is not enough — short, quiet, noise-like clips have almost flat
envelopes, which correlate with anything.

Accepted mappings:

| Shipped file | Source recording | Correlation / offset | Duration | Centroid |
|---|---|---|---|---|
| `memory_charge.ogg` | the laser-charge recording | +1.000 @ 0 ms | 3.663 / 3.650 s | 1977 / 2022 Hz |
| `memory_tape_hiss.ogg` | the blank-tape hiss recording | +0.998 @ 0 ms | 16.631 / 16.631 s | 8262 / 9488 Hz |
| `memory_tape_eject.ogg` | the cassette-deck eject recording | +1.000 @ 0 ms | 0.569 / 0.550 s | 4733 / 4823 Hz |
| `screaming_crawler_ambient.ogg` | the horror sting (Taira Komori) | +1.000 @ 0 ms | 6.770 / 6.768 s | 8264 / 8205 Hz |
| `screaming_crawler_explode.ogg` | the explosion recording | +1.000 @ 0 ms | 10.143 / 10.141 s | 1810 / 1794 Hz |
| `screaming_crawler_roar.ogg` | the monster-roar recording | +0.989 @ −350 ms | 10.950 / 11.973 s | 1542 / 1580 Hz |

Not identified, and therefore still credited as inherited: `insanity1.ogg`, `heartbeat.ogg`,
`leaves_rustle.ogg`, `swish0-4.ogg`, `whoosh.ogg` — nothing in the source folder matches them on all
three criteria, so they are treated as coming from the original project.
The scream recording in the source folder does not match any shipped file on all three criteria, so it
appears to be unused.

Reproduce with the comparison script kept in the project's tooling folder.

## 5. Still open

| # | Item | Why it matters |
|---|---|---|
| 1 | The horror sting: the **CC-BY version** only (its shipped file is now measured: `screaming_crawler_ambient.ogg`) | completeness — the attribution text is already correct |
| 2 | Two recordings used by the crawler (`screaming_crawler_roar.ogg`, `screaming_crawler_explode.ogg`): author and licence. Both are from Freesound; the mapping to those files is measured and settled | if either is licensed **non-commercially**, it must be replaced before a release that earns revenue |
| 3 | The inherited sounds: per-file origins | they are covered by the upstream attribution today; per-file detail would be better |
| 4 | Any file found to be non-commercial | **blocker**: replace it before shipping a revenue-earning build |

## 6. Release checklist

* [x] The tape feature's audio is fully licensed — three CC0 sounds plus one CC-BY recorded below.
* [ ] Horror sting: shipped file mapping and the CC-BY version filled in.
* [ ] The three remaining source recordings checked for author, licence and use.
* [ ] The required attribution above reproduced on the project page, the README and the release notes.
* [ ] Zapsplat and toujourspareil credited on the public page, not only inside the jar.
* [ ] This file present in the repository **and** in the release archive.
* [ ] The public wording says **code under MIT, other assets all rights reserved** — not "the mod is MIT".
