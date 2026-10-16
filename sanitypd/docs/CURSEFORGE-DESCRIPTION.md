# CurseForge 项目页文案（直接复制粘贴）

> 用法：CurseForge 建项目时，**Project Description** 支持富文本/Markdown。
> 下面两段分别对应"英文区"和"中文区"，可整段粘贴；如果只能填一份，用英文那份（CurseForge 主受众）。
> **本项目页文案是面向玩家的**：只写玩法与安装，不要写开发过程、待办、版本号内幕。
> **术语一律照游戏内名称**（物品 / 方块 / 效果 / 附魔以 `lang` 文件为准）。

---

## 项目基本信息（表单字段）

| 字段 | 填什么 |
|---|---|
| **Project name** | `Sanity: Psychological Devastation` |
| **Summary（一句话，英文）** | `[ALPHA] A sanity system for Minecraft: new mechanics, items, mobs and commands built on the original Sanity mod.` |
| **Summary（一句话，中文）** | `【ALPHA 测试版】为 Minecraft 带来理智系统：在原版 Sanity 模组基础上新增机制、物品、生物与命令。` |
| **Categories** | Adventure and RPG · Mobs · World Gen（至少选 Adventure and RPG） |
| **Mod loader** | Forge |
| **Game version** | 1.20.1（1.20 亦可） |
| **License** | MIT（**必须保留上游 croissantnova 的署名**，见下方 Credits） |
| **Contact / Issues** | 项目页 / Project page: `https://github.com/Yaojihua/sanity-psychological-devastation` |
| **Release type** | **Beta** ← 当前务必选 **Beta**（选择 Alpha 的文件不会同步到 CurseForge App，玩家在 App 里搜不到） |

---

## 英文描述（粘贴用）

```
⚠️ ALPHA TEST BUILD
This is an alpha test version. Mechanics, numbers, config keys and commands may still change between
builds, and things may be unbalanced or broken. Back up your worlds before installing it.

A sanity system for Minecraft — new mechanics, items, mobs and commands built on top of the original
Sanity mod.

Sanity is point-based, and EVERY living entity has it. Low sanity corrupts what you see, what you hear,
and eventually what comes for you.

REQUIREMENTS
• Minecraft 1.20 / 1.20.1
• Forge 46+
• GeckoLib — REQUIRED. Use 4.2 on Forge 46, or 4.8.4 on Forge 47.1+.
  (A wrong pairing makes the game report a missing "geckolib" dependency; the real cause is the Forge version.)
• Jade — optional, adds a sanity line to its tooltip
• JEI — optional, gives the Thought Transcription Table its own recipe page

FEATURES
• The Thought Chain: the Psychic Controller opens your own eighteen-slot chain — two rows of nine,
  Worldview and Methodology, and a Hallucination thought may sit in either row. Six counting types
  (Composure, Madness, Servitude, Chaos Restraint, Endurance, Hallucination) and twenty-four thoughts, with
  tiered effects, mindset bonuses and a Mindset Trends panel. Hold Shift in a thought's tooltip for the
  full tier table.
• Thought Containers and the Thought Transcription Table: three thoughts of one type plus the plain
  container and an Inner Core make that type's variant container for 2 experience levels; one graded thought
  with its variant container and an Inner Core comes back empowered for 3. Hallucination and Chaos Restraint
  have no variant container, so nothing of those two types can be transcribed. With JEI installed the same
  rows are browsable as a recipe page.
• Lost memory fragments, nine in all: the four the story is told in sit in the creative tab, and five more
  are handed out during play. Hold right click to charge with the bow-pull pose, and a tape plays — white
  lines arriving blurred and resolving into focus, trembling, over a running noise band and dropout lines
  that never stop. The first viewing of a fragment cannot be skipped; after that any key leaves it. Single
  player pauses the game while a tape plays, and on a server you are protected from damage the way creative
  mode protects you. Watch eight of the nine and seven of the tapes have their hidden words made readable.
• Non-Suicidal Self-Injury, a thought and a mechanic: it takes real health but never the last point, does not
  move you at all, gives sanity back below a quarter of your meter and takes it away at or above that, leaves
  blood on the ground for half a minute, and speaks one line per act. Try it with /sanity selfinjury — on
  yourself it needs no permission, so it works in a hardcore world with cheats off.
• Shadow Gunpowder and Shadow TNT: the gunpowder drops from the Screaming Crawler, the TNT is crafted from it
  in the vanilla TNT layout, and its blast deals psychic damage that does not fall off with distance.
• Point-based sanity: players cap at 100, other mobs cap at their max health; every living entity has sanity.
• Passive and active sanity sources: light, darkness, weather, water, hunger, nearby monsters, pets, music,
  blocks underfoot, sleeping, breeding, trading, fishing, shearing, advancements, eating and more.
• Psychic damage is resolved on its own sanity track (sanity first, and only the part past zero sanity becomes
  true damage), so it never goes through the ordinary armour calculation: armour, vanilla resistance and vanilla
  enchantments cannot reduce it — only a blocking shield, the mod's own psychic resistance, or the Psychic
  Protection enchantment can.
• Status effects: Confusion, Mania, Mania Immunity, Inner Immunity, Psychic Drain, Sanity Recovery.
• Enchantments: Psychic Protection, Psychic Deprivation, Psychic Drain.
• Items: Shadow Sword and Shadow Axe (charge to repair durability at the cost of sanity; an off-hand shield
  takes priority), anvil-based shadow refinement (1 Inner Clump = +1 level = +1 psychic damage = 1 XP level,
  cap 100, no XP penalty, hold Shift to consume every clump at once), Inner Shard / Clump / Core,
  Mood Stabilizer α / β / γ / δ, Garland, Macarons, and spawn eggs for all three inner entities.
• Inner entities: Rotting Stalker, Sneaking Terror and Screaming Crawler. They have no sanity of their own,
  and confusion and mania do nothing to them.
  Psychic damage on them follows a different rule than on players: with no sanity to drain, the whole amount
  is multiplied by their own resistance factor AND by 2.5, then applied as true damage
  (crawler resistance 20 -> 0.8 factor -> 100 psychic damage becomes 200 true damage).
  The Screaming Crawler hunts players below 20% sanity, paths around obstacles with A* pathfinding,
  jumps up ledges and explodes at close range; its eyes glow in the dark.
  Two sanity gates keep a recovering player from being punished by it: at 75% sanity or above it cannot
  track you at all (it refuses you as a target and instantly drops a chase in progress), and between 50% and
  75% its blast destroys no blocks (the damage is unchanged).
  IMPORTANT: its blast deals a large amount of PSYCHIC damage. Psychic damage drains sanity, and any damage
  that goes past zero sanity is converted 1:1 into true damage (which ignores armour, enchantments, shields
  and effects). From full sanity the blast only drains sanity; at low sanity it kills you. Note that the
  crawler never harms other inner entities — its blast skips everything tagged `sanitypd:inner_entities`.
• Commands: /sanity get|set|add|state|psychic|truedamage|resist|innerspawn|nospawn|config reload, plus
  /sanity selfinjury [<targets>] and /sanity hint <tier> add|remove|list|clear|show for the five inner-voice
  pools (mild / severe / deep / expiry / self_injury). The inner voices speak in the centre of the screen:
  the deep pool is reserved for the last five seconds before the mania damage starts, the expiry pool warns
  during the final five seconds of the Mania Immunity or Inner Immunity buff, and the self_injury pool answers
  a self-inflicted wound one tick after it lands and holds the centre of the screen for six seconds.
• Fully commented config: passive and active sources, item and block lists, multiplayer and client options.

COMPANION TOOL: SanityPD PROBE (思维探针 / "Mind Probe")
An optional, separate diagnostic mod (sanityprobe-mc1.20-2.40.0.jar). It changes nothing about the game and
is not needed to play; it exists so client-side behaviour (rendering, HUD, animation) can be verified.
  • Install: drop the jar into mods/ next to the main mod. Delete it when done.
  • Read: <instance>/logs/sanityprobe.log (one tagged line per event) — also mirrored into latest.log as [PROBE].
  • Overlay: a small panel in the top-left corner; press F4 to hide/show it (rebindable in Controls).
  • Commands: /sanityprobe on | off | mark <text> (client-side, no operator permission needed).
  • It observes, one tagged group per mechanism: the crawler's walk animation, ground contact and floating
    appearance; its target, goals, A* path and the movement commands really issued; the sanity gates that
    decide what it may do; every sound played, its distance and whether the resource resolves; the memory
    tape and its gates; the thoughts, their counting types, the constants the mechanics read and the
    sentences the tooltips print; the transcription table; the hallucination batch; the mood stabilizers;
    the sneak sanity readout; shield-vs-weapon right-click priority; psychic protection and anvil refinement;
    the inner-voice pools and their warning windows; self-injury and its own pool; the blood decals; shadow
    TNT; the handed-out fragments; the tape lines that become readable; the spawn eggs and the "do not say
    that name" easter egg; the death messages; the title-screen splash pool; and the overlay's own layout.
  • Feedback: attach logs/sanityprobe.log — that single file is enough.

MODPACKS
You are free to include this mod in modpacks.

ROADMAP (what is still coming)
This build is the SECOND PART of a larger mod, not the finished game. The rest of the thought chain and the
rest of the memory fragments are in, and these are still to come:
• The rest of the story, and the final encounter the whole sanity system is being built toward.
• An equipment system — thinking-enhancement items that change how sanity works for you.
• More thoughts, inner entities, items and sanity sources.
No dates are promised for any of it.

CREDITS
• Original project: Sanity: Descent Into Madness by croissantnova (MIT) — this mod's mechanics are derived
  from it, and the original license and copyright notices are preserved in full.
• Original art / audio collaboration: toujourspareil, Zapsplat. Inherited sound effects keep their own
  terms; the Zapsplat standard license is linked in NOTICE-sanitypd.txt inside the jar.
• This version: Yaojihua.
• AI-assisted development: parts of this mod were developed with DeepSeek Harness ("dsh").
  All design decisions, in-game testing and final review were done by the author.
• Sound effects added by this project (full list: ASSETS-CREDITS.md in the repository):
  - ATTRIBUTION REQUIRED, CC-BY 4.0: the horror sting 「恐怖」 (terror.mp3) and the tinnitus pair
    (tinnitus1, tinnitus2), all three by Taira Komori, via Freesound
    (sources https://freesound.org/people/Taira%20Komori/sounds/217148/, https://freesound.org/s/217155/,
    https://freesound.org/s/217154/, license https://creativecommons.org/licenses/by/4.0/).
    Modified for this mod: converted to OGG and downmixed to mono.
  - ATTRIBUTION REQUIRED, CC-BY 3.0: the woman's scream (screaming_woman_horror.mp3, from the author's
    pack "Screaming 666moviescreams") by GabrielaUPF, via Freesound
    (source https://freesound.org/people/GabrielaUPF/sounds/220302/, license
    https://creativecommons.org/licenses/by/3.0/). Modified for this mod: converted to OGG.
  - CC0, no attribution required (credited anyway): the tape sounds 「Charged laser」 by LegoLunatic,
    「盒式磁带机弹出」 by jpkweli and 「空白磁带嘶嘶噪声」 by 骨质人.
• This mod is an unofficial derivative of the original project and is not affiliated with, endorsed by or
  supported by its author.
• Code is MIT; all other assets — textures, models, animations and the story text — are all rights
  reserved.

CONTACT: https://github.com/Yaojihua/sanity-psychological-devastation
```

---

## 中文描述（粘贴用）

```
⚠️ ALPHA 测试版
这是 alpha 测试版：机制、数值、配置键与命令在版本之间仍可能变动，内容也可能失衡或存在缺陷。
安装前请先备份存档。

为 Minecraft 带来"理智"系统 —— 在原版 Sanity 模组的基础上进行了新的机制、物品、生物、命令设计。

理智是点数制，而且**所有生物**都有理智。理智越低，你看到的东西、听到的声音，以及最终朝你扑来的
东西，都会变得不对劲。

前置要求
• Minecraft 1.20 / 1.20.1
• Forge 46+
• GeckoLib —— 必装。Forge 46 用 4.2，Forge 47.1+ 用 4.8.4。
  （搭配错了游戏会报"缺少 geckolib 前置"，但真正原因是 Forge 版本不匹配。）
• Jade（玉）—— 可选，装了会在提示框多一行理智数值
• JEI —— 可选，会为思维转录台提供一页配方页

功能
• 思维链：精神控制仪打开你自己的十八格思维链 —— 两行九格：世界观与方法论，幻视类思维两行都能放。
  六种计数类型（镇静 / 疯狂 / 奴役 / 混沌限制 / 忍耐 / 幻视）与二十四枚思维，效果分档，并有思维定式加成与
  "思维趋势"面板。在思维的提示框里按住 Shift 可查看完整档位表。
• 思维容器与思维转录台：三枚同类型思维 ＋ 不分类型的思维容器 ＋ 内在核心，可产出该类型的变种容器，花费 2 级经验；
  一枚带档位的思维 ＋ 它的变种容器 ＋ 内在核心，可拿回同一枚已被强化的思维，花费 3 级经验。
  幻视与混沌限制没有变种容器，因此无法转录。装了 JEI 的话同一套配方可以在配方页里翻看。
• 失落的记忆片段（共九枚）：四枚主线放在创造栏里，另外五枚要在游戏里被交到你手上。长按右键以拉弓姿势
  蓄力，随后播放一盘磁带 —— 白字逐句出现、先模糊后清晰、微微抖动，底下是始终不断运行的噪声带与坏线。
  **某枚片段的首播不可跳过**；看过一次之后，任意键即可退出。播放时单人模式会暂停，联机时你受到与创造
  模式同等的免伤保护。看满九枚中的八枚后，七盘磁带原先被遮住的字会变成可读文字。
• 非自杀性自伤：一枚思维，也是一套机制。它会扣真实生命但绝不拿走最后一点，不会推动你，理智低于四分之一
  时回理智、达到或高于四分之一时反而扣理智，每刀在地上留下半分钟的血迹，每次生效只说一句。可以用
  `/sanity selfinjury` 立刻试 —— 对自己用不需要权限，所以在关闭作弊的极限模式里也能用。
• 暗影火药与暗影TNT：火药只由尖啸伏爬者掉落，暗影TNT 用它按原版 TNT 摆法合成，爆炸造成无视距离衰减的
  精神伤害。
• 点数制理智：玩家上限 100，其它生物上限 = 其最大生命值；所有生物都有理智。
• 被动与主动理智来源：光照、黑暗、天气、泡水、饥饿、附近怪物、宠物、音乐、脚下方块，
  以及睡觉、繁殖、交易、钓鱼、剪羊毛、成就、进食等。
• 精神伤害走理智这条独立通路（先扣理智，超出部分 1:1 转真伤），**不经过原版护甲结算** ⇒ 护甲 / 原版抗性 / 原版附魔都减不了它；能减的只有举盾格挡、本模组的精神抗性、或"精神庇护"附魔。
• 状态效果：混乱、狂躁、狂躁免疫、无视内在、精神流失、理智恢复。
• 附魔：精神庇护、精神剥夺、精神流失。
• 物品：暗影剑与暗影斧（蓄力补耐久、消耗理智；副手拿盾时盾牌优先）、铁砧精锻
  （1 内在团块 = +1 级 = +1 精神伤害 = 1 经验等级，上限 100，不参与经验惩罚，按 Shift 一次吃光）、
  内在碎片 / 团块 / 核心、心境稳定剂 α/β/γ/δ、花环、马卡龙，以及三只内在生物的刷怪蛋。
• 内在生物：腐朽跟踪者、惊骇潜行者、尖啸伏爬者。它们没有理智、免疫混乱与狂躁，
  **精神伤害打到它们身上的规则与打到玩家身上不同**：它们没有理智可扣 ⇒ 整份伤害 × 自身抗性系数 × 2.5 全部转成真伤
  （爬者抗性 20 ⇒ 系数 0.8 ⇒ 100 点精神伤害 = 200 点真伤）。尖啸伏爬者会猎杀理智低于 20% 的玩家，
  用 A* 寻路绕开障碍、跨台阶与跳跃、近身自爆，眼睛在黑暗中发光。
  **两条理智闸门**：理智 **≥75% 时它完全无法追踪你**（拒绝把你设为目标，正在追的立刻脱战）；
  **50%~75% 时它可以追、可以炸，但爆炸不破坏方块**（伤害不变）。
  **重要**：爬者自爆造成的是**大量精神伤害**。精神伤害削减理智，而**超出当前理智的那部分会 1:1 转成真实伤害**
  （真实伤害无视护甲、附魔、盾牌与状态效果）⇒ 理智充足时只是掉理智，理智偏低时**会直接致死**。
  另：**爬者不会伤到其他内在生物** —— 爆炸结算按 `sanitypd:inner_entities` 标签跳过它们。
• 命令：/sanity get|set|add|state|psychic|truedamage|resist|innerspawn|nospawn|config reload，以及
  /sanity selfinjury [<目标>] 与 /sanity hint <档位> add|remove|list|clear|show（五个内心标语池：
  温和 / 严重 / 深层 / 免疫到期 / 自残）。内心标语显示在屏幕中央：深层池只在真伤开始前 5 秒说话，
  免疫到期池在"狂躁免疫 / 无视内在"结束前 5 秒警告，自残池在自残生效 1 tick 后说出并占据中央 6 秒。
• 配置文件每项都有注释：被动/主动来源、物品与方块列表、多人游戏与客户端选项。

陪跑工具：SanityPD 思维探针（Mind Probe）
可选的独立诊断模组（`sanityprobe-mc1.20-2.40.0.jar`）。**玩法不需要它**，也不改变任何游戏内容；
它的意义是让"只有真实客户端才看得到的行为"（渲染 / HUD / 动画）可被验证。
  • 安装：把 jar 与主模组一起放进 `mods/`；用完删掉即可。
  • 看日志：`<实例>/logs/sanityprobe.log`（一行一件事、带标签），同时镜像到 `latest.log` 的 `[PROBE]` 行。
  • 屏幕叠加层：左上角一小块读数；**F4** 隐藏/显示（可在"控制"里改键）。
  • 命令：`/sanityprobe on | off | mark <文本>`（客户端命令，无需管理员权限）。
  • 它观察（一个机制一组带标签的行）：爬者的走路动画、着地状态与浮空表现；它的目标、GOAL、A* 路径与真正
    下发的移动指令；决定它能做什么的两条理智闸门；播放的每个声音、距离与资源解析；记忆磁带及其闸门；
    思维本身、计数类型、机制读取的常量与 tooltip 真正打出的句子；思维转录台；幻视那一批；心境稳定剂；
    潜行理智读数；盾牌与武器的右键优先级；精神庇护与铁砧精锻；内心标语各池与警告窗口；自残及其专属池；
    地上的血渍；暗影TNT；被交到手上的那几枚片段；变为可读的磁带行；刷怪蛋与"不许直呼那个名字"彩蛋；
    死亡消息；标题屏标语；以及叠加层自身的版式。
  • 反馈 bug：附上 `logs/sanityprobe.log` 这一个文件就够了。

整合包
欢迎把本模组放进你的整合包。

开发路线（还没到的部分）
现在这一版是**第二部分**，不是完成品：思维链与记忆片段的剩余部分已经加入，后面还会做：
• **后续剧情**，以及整套理智系统最终要指向的终局内容。
• **装备系统** —— 能改变理智运作方式的思维强化类物品。
• 更多思维、内在生物、物品与理智来源。
以上都不承诺时间表。

致谢
• 原作：Sanity: Descent Into Madness（作者 croissantnova，MIT 协议）—— 本模组机制源自它，
  原作者的许可与著作权声明被完整保留。
• 原作美术 / 音效协作：toujourspareil、Zapsplat。沿用自原作的音效保持其原有条款；
  Zapsplat 标准授权页链接写在包内 NOTICE-sanitypd.txt 里。
• 本版本作者：Yaojihua。
• AI 辅助开发：部分内容在 DeepSeek Harness（dsh）辅助下开发；所有设计决策、游戏内测试与最终审核
  均由作者完成。
• 本模组新增的音效（完整清单见仓库里的 ASSETS-CREDITS.md）：
  - **必须署名，CC-BY 4.0**：恐怖音效「恐怖」（terror.mp3）与两条耳鸣（tinnitus1、tinnitus2），
    作者均为 Taira Komori，来自 Freesound（来源 https://freesound.org/people/Taira%20Komori/sounds/217148/、
    https://freesound.org/s/217155/、https://freesound.org/s/217154/，协议文本
    https://creativecommons.org/licenses/by/4.0/）。这些音效**已被修改**：转为 OGG 并下混为单声道。
  - **必须署名，CC-BY 3.0**：女人的尖叫（screaming_woman_horror.mp3，出自作者音效包 "Screaming 666moviescreams"）
    作者 GabrielaUPF，来自 Freesound（来源 https://freesound.org/people/GabrielaUPF/sounds/220302/，
    协议文本 https://creativecommons.org/licenses/by/3.0/）。该音效**已被修改**：转为 OGG。
  - **CC0，不要求署名**（此处仍致谢）：磁带音效「Charged laser」作者 LegoLunatic、
    「盒式磁带机弹出」作者 jpkweli、「空白磁带嘶嘶噪声」作者 骨质人。
• 本模组是原项目的非官方衍生作品，与原作者无隶属关系，也未获其背书或支持。
• **代码采用 MIT 协议；其余素材 —— 贴图、模型、动画与剧情文本 —— 保留所有权利。**

联系方式：https://github.com/Yaojihua/sanity-psychological-devastation
```

---

## 发布前勾选清单

- [ ] Release type 选 **Beta**（**不要选 Alpha**：Alpha 文件不会同步到 CurseForge App，玩家在 App 里搜不到）
- [ ] 上传的文件：`sanitypd-mc1.20-1.21.7.jar`
- [ ] 依赖里写明 **GeckoLib（必装，需与 Forge 版本搭配）**
- [ ] 描述里保留**上游 croissantnova 的署名与 MIT 许可**（CurseForge 会检查）
- [ ] 保留 **AI 辅助开发声明**
- [ ] 上传 `LICENSE` 与 `CHANGELOG.md` 的内容（CurseForge 的文件页可填 changelog）
- [ ] 版本关系：标为 **1.20.1 / Forge**
