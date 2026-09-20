# BrainFly 2026.09.20-0254 — keepInventory-aware death recovery

0254 makes death-site recovery conditional on actual item loss instead of blindly returning to the death point.

## World-rule hint

Commands:

- `!brain gamerule keepInventory true`
- `!brain gamerule keepInventory false`
- `!brain gamerule keepInventory auto`
- `!brain death status`
- `!brain death recovery skip`

`true` is an explicit teacher/world hint that inventory is preserved and death-site recovery must be skipped. `false` allows recovery, but only when pre/post-death inventory comparison proves that something was actually lost. `auto` infers the rule after respawn.

## Automatic semantics

- inventory is snapshotted before death;
- empty inventory at death => no death recovery;
- after respawn the inventory is compared after a short settle window;
- preserved inventory away from the death site => infer keepInventory=true and resume Destiny;
- actual loss => keep bounded death recovery active;
- if respawn occurs essentially on the death site, preserved inventory skips that recovery but does not permanently infer keepInventory, avoiding false inference from immediate item pickup.

## Control menu

The vanilla chest menu now includes:
- `keepInventory = TRUE`
- `Смерть / recovery: статус`

## Verification

- 42/42 JavaScript regression tests PASS
- `tools/smoke_test.py` PASS
- existing bounded death-recovery regression still passes
- new death inventory policy regression covers explicit hint, empty inventory, auto inference, and actual loss

## Artifacts

- `BrainFly-0254-ONECLICK.zip` SHA-256 `637f901e8f8abb566017fc97ac2da597b1988a1a323a188e2644fba9f4e3eeb7`
- `BrainFly-Control-Fabric-1.21.1-2026.09.20-0254.zip` SHA-256 `7fd96b2926eeecb513ae6c1526a19735819457aa52babf25c44fee33154cf150`
- `BrainWorkbench-Minecraft-2026.09.20-0254.zip` SHA-256 `8930b9fd722fc6b1a7db229aaa7cf1cb4804fe144588c9cf77d940497a80845d`
- `BrainFly-0254-DEATH-POLICY.patch` SHA-256 `3e70eb096785da6dc45812afedcfdf023817fa5ae3d0265e0ce9a9a649d9d527`

0254 is cumulative over 0253 feedback-credit protection and 0252 world-model/infrastructure/mining changes. The one-click installer preserves config, memory, logs, snapshots, and external knowledge packs.
