# BrainFly 2026.09.20-0258 — Hard Survival Reflex

Live play after 0257 showed that fixing duty arbitration was not enough: survival itself still depended too much on the ~1.8 s autonomy scheduler. Repeated hostile hits could arrive much faster than the next autonomy tick, and critical hunger without carried food could fall through to normal progression.

## Root cause

- damage escape was scheduler-driven rather than event-driven;
- the old damage guard used a generic explore action instead of a threat-relative escape;
- the safety guard only ate food already in inventory and did not actively seek food when none was carried;
- every health decrement could independently reinforce aversion, over-weighting repeated damage bursts.

## 0258 survival priority

A hard event-driven reflex now fires directly from Minecraft health events:

`damage event -> preempt interaction/navigation/acquisition -> identify nearest hostile -> turn away -> sprint + bounded jump -> resume only after survival lock`

Additional behavior:
- point-blank non-creeper threats may receive one knockback counterattack when BrainFly has enough health;
- respawn creates a short survival lock and delayed hostile scan;
- three deaths within 120 s create a longer 12 s survival lock, while the existing five-minute household/building suspension remains active;
- critical hunger with no carried edible item actively starts food acquisition instead of falling through to Destiny;
- repeated damage reinforcement is rate-limited and down-weighted inside a burst, while death punishment remains full strength;
- panic does not run toward home while the immediate area is unsafe.

Commands:
- `!brain survival`
- `!brain survival status`
- `!brain survival reflex`
- `!brain escape now`

Status/stats expose survival lock, active reflex, damage burst, reflex count, and suppressed damage-reinforcement count.

## Verification

- **50/50** JavaScript regression tests PASS
- `tools/smoke_test.py` PASS
- N = 165122
- E = 10511038
- effective_edges = 5016094
- Minecraft motor spike delta = 127
- 0257 duty-arbitration regression remains covered
- iron-pickaxe procedure and Elytra async-ACK regressions remain covered

## Artifacts

- `BrainFly-0258-RUNTIME-HOTFIX.zip` SHA-256 `c75b26799bbbeb50168910b658e4c967d2b60d2dac8422a9534d9153cfefbc03`
- `BrainWorkbench-Minecraft-2026.09.20-0258.zip` SHA-256 `0130b2afd7a1d710c56fd45c05cbc05874edf6199ac1967f399c61259bca1fa5`
- `BrainFly-0258-SURVIVAL-REFLEX.patch` SHA-256 `1e1569a02ec36e7433f1ebb1f453d0777ee75ff4c17d1652c3c6ad40debb2f63`

0258 is runtime-only and cumulative over 0257/0256. The hotfix preserves config, learned memory, logs, snapshots and external knowledge packs. Fabric Control does not need rebuilding.
