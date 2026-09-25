# CurseForge 项目页文案（直接复制粘贴）

> 用法：CurseForge 建项目时，**Project Description** 支持富文本/Markdown。
> 下面两段分别对应"英文区"和"中文区"，可整段粘贴；如果只能填一份，用英文那份（CurseForge 主受众）。
> **本项目页文案是面向玩家的**：只写玩法与安装，不要写开发过程、待办、版本号内幕。

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
| **Contact / Issues** | `piloser21.87038fjl@qq.com` |
| **Release type** | **Beta / Alpha** ← 当前务必选 **Alpha** |

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

FEATURES
• Point-based sanity: players cap at 100, other mobs cap at their max health; every living entity has sanity.
• Passive and active sanity sources: light, darkness, weather, water, hunger, nearby monsters, pets, music,
  blocks underfoot, sleeping, breeding, trading, fishing, shearing, advancements, eating and more.
• Psychic damage ignores armour by design — only the Psychic Protection enchantment or a blocking shield helps.
• Status effects: Confusion, Mania, Mania Immunity, Inner Immunity, Psychic Drain.
• Enchantments: Psychic Protection, Psychic Deprivation, Psychic Drain.
• Items: Shadow Sword and Shadow Axe (charge to repair durability at the cost of sanity; an off-hand shield
  takes priority), anvil-based shadow refinement (1 Inner Clump = +1 level = +1 psychic damage = 1 XP level,
  cap 100, no XP penalty, hold Shift to consume every clump at once), Inner Shard / Clump / Core,
  Stabilizer α / β / γ, Garland, and spawn eggs for all three inner entities.
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
  /sanity hint <tier> add|remove|list|clear|show for the four inner-voice pools (mild / severe / deep /
  expiry). The inner voices speak in the centre of the screen: the deep pool is reserved for the last five
  seconds before the mania damage starts, and the expiry pool warns during the final five seconds of the
  Mania Immunity or Inner Immunity buff.
• Fully commented config: passive and active sources, item and block lists, multiplayer and client options.

COMPANION TOOL: SanityPD PROBE (思维探针 / "Mind Probe")
An optional, separate diagnostic mod (sanityprobe-mc1.20-2.9.0.jar). It changes nothing about the game and
is not needed to play; it exists so client-side behaviour (rendering, HUD, animation) can be verified.
  • Install: drop the jar into mods/ next to the main mod. Delete it when done.
  • Read: <instance>/logs/sanityprobe.log (one tagged line per event) — also mirrored into latest.log as [PROBE].
  • Overlay: a small panel in the top-left corner; press F4 to hide/show it (rebindable in Controls).
  • Commands: /sanityprobe on | off | mark <text> (client-side, no operator permission needed).
  • It observes: walk animation and ground contact; every sound played and whether it resolves; stabilizer
    cooldowns and effects; the sneak sanity readout; shield-vs-weapon right-click priority; psychic
    protection and anvil refinement numbers; the title-screen splash pool; the crawler's target, goals,
    A* path and movement commands; and the overlay's own line count.
  • Feedback: attach logs/sanityprobe.log — that single file is enough.

MODPACKS
You are free to include this mod in modpacks.

ROADMAP (what is still coming)
This build is the FRAMEWORK, not the finished game. Still to come:
• An equipment system — thinking-enhancement gear and self-imposed restrictions that change how sanity
  works for you.
• A final encounter — the endgame the whole sanity system is being built toward.
• More inner entities, items and sanity sources.
The alpha label reflects exactly this.

CREDITS
• Original project: Sanity: Descent Into Madness by croissantnova (MIT) — this mod's mechanics are derived
  from it, and the original license and copyright notices are preserved in full.
• Original art / audio collaboration: toujourspareil, Zapsplat.
• This version: Yaojihua.
• AI-assisted development: parts of this mod were developed with DeepSeek Harness ("dsh").
  All design decisions, in-game testing and final review were done by the author.

CONTACT: piloser21.87038fjl@qq.com
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

功能
• 点数制理智：玩家上限 100，其它生物上限 = 其最大生命值；所有生物都有理智。
• 被动与主动理智来源：光照、黑暗、天气、泡水、饥饿、附近怪物、宠物、音乐、脚下方块，
  以及睡觉、繁殖、交易、钓鱼、剪羊毛、成就、进食等。
• 精神伤害不吃护甲（设计如此）—— 只有"精神庇护"附魔或举盾格挡能减伤。
• 状态效果：混乱、躁狂、狂躁免疫、无视内在、精神流失。
• 附魔：精神庇护、精神剥夺、精神流失。
• 物品：暗影剑与暗影斧（蓄力补耐久、消耗理智；副手拿盾时盾牌优先）、铁砧精锻
  （1 内在团块 = +1 级 = +1 精神伤害 = 1 经验等级，上限 100，不参与经验惩罚，按 Shift 一次吃光）、
  内在碎片 / 团块 / 核心、心境稳定剂 α/β/γ、花环，以及三只内在生物的刷怪蛋。
• 内在生物：腐朽跟踪者、惊骇潜行者、尖啸伏爬者。它们没有理智、免疫混乱与躁狂，
  **精神伤害打到它们身上的规则与打到玩家身上不同**：它们没有理智可扣 ⇒ 整份伤害 × 自身抗性系数 × 2.5 全部转成真伤
  （爬者抗性 20 ⇒ 系数 0.8 ⇒ 100 点精神伤害 = 200 点真伤）。尖啸伏爬者会猎杀理智低于 20% 的玩家，
  用 A* 寻路绕开障碍、跨台阶与跳跃、近身自爆，眼睛在黑暗中发光。
  **两条理智闸门**：理智 **≥75% 时它完全无法追踪你**（拒绝把你设为目标，正在追的立刻脱战）；
  **50%~75% 时它可以追、可以炸，但爆炸不破坏方块**（伤害不变）。
  **重要**：爬者自爆造成的是**大量精神伤害**。精神伤害削减理智，而**超出当前理智的那部分会 1:1 转成真实伤害**
  （真实伤害无视护甲、附魔、盾牌与状态效果）⇒ 理智充足时只是掉理智，理智偏低时**会直接致死**。
  另：**爬者不会伤到其他内在生物** —— 爆炸结算按 `sanitypd:inner_entities` 标签跳过它们。
• 命令：/sanity get|set|add|state|psychic|truedamage|resist|innerspawn|nospawn|config reload，以及
  /sanity hint <档位> add|remove|list|clear|show（四个内心标语池：温和 / 严重 / 深层 / 到期）。
  内心标语显示在屏幕中央：深层池只在真伤开始前 5 秒说话，到期池在"狂躁免疫 / 无视内在"结束前 5 秒警告。
• 配置文件每项都有注释：被动/主动来源、物品与方块列表、多人游戏与客户端选项。

陪跑工具：SanityPD 思维探针（Mind Probe）
可选的独立诊断模组（`sanityprobe-mc1.20-2.9.0.jar`）。**玩法不需要它**，也不改变任何游戏内容；
它的意义是让"只有真实客户端才看得到的行为"（渲染 / HUD / 动画）可被验证。
  • 安装：把 jar 与主模组一起放进 `mods/`；用完删掉即可。
  • 看日志：`<实例>/logs/sanityprobe.log`（一行一件事、带标签），同时镜像到 `latest.log` 的 `[PROBE]` 行。
  • 屏幕叠加层：左上角一小块读数；**F4** 隐藏/显示（可在"控制"里改键）。
  • 命令：`/sanityprobe on | off | mark <文本>`（客户端命令，无需管理员权限）。
  • 它观察：走路动画与着地、音效播放与资源解析、稳定剂冷却与效果、潜行理智读数、盾牌与武器的右键优先级、
    精神庇护与铁砧精锻数值、标题屏标语池、爬者的目标/GOAL/A* 路径/移动指令、以及叠加层自身行数。
  • 反馈 bug：附上 `logs/sanityprobe.log` 这一个文件就够了。

整合包
欢迎把本模组放进你的整合包。

开发路线（还没到的部分）
现在这一版是**骨架**，不是完成品。后面会做：
• **装备系统** —— 思维强化类装备，以及"自限制"类装备（给自己上约束，从而改变理智的运作方式）。
• **最终战斗** —— 整套理智系统最终要指向的终局内容。
• 更多内在生物、物品与理智来源。
alpha 标注的含义就是这个。

致谢
• 原作：Sanity: Descent Into Madness（作者 croissantnova，MIT 协议）—— 本模组机制源自它，
  原作者的许可与著作权声明被完整保留。
• 原作美术 / 音效协作：toujourspareil、Zapsplat。
• 本版本作者：Yaojihua。
• AI 辅助开发：部分内容在 DeepSeek Harness（dsh）辅助下开发；所有设计决策、游戏内测试与最终审核
  均由作者完成。

联系方式：piloser21.87038fjl@qq.com
```

---

## 发布前勾选清单

- [ ] Release type 选 **Alpha**
- [ ] 上传的文件：`sanitypd-mc1.20-1.1.0.jar`
- [ ] 依赖里写明 **GeckoLib（必装，需与 Forge 版本搭配）**
- [ ] 描述里保留**上游 croissantnova 的署名与 MIT 许可**（CurseForge 会检查）
- [ ] 保留 **AI 辅助开发声明**
- [ ] 上传 `LICENSE` 与 `CHANGELOG.md` 的内容（CurseForge 的文件页可填 changelog）
- [ ] 版本关系：标为 **1.20.1 / Forge**
