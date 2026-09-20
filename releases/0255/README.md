# BrainFly 2026.09.20-0255 — iron plan-cycle repair + async Elytra ACK

Live control output exposed two independent failures:

- `goal iron_pickaxe -> working: plan_cycle`
- `goal replan -> replan failed safely: cycle`
- `glide -> bridge did not answer` even though subsequent commands still worked.

## Planner fix

The recipe planner was incorrectly treating reversible storage recipes as acquisition routes. In vanilla Minecraft this allows loops such as:

`iron_pickaxe -> iron_ingot -> iron_block -> iron_ingot -> ...`

0255 separates world/resource acquisition from crafting transforms:

- resource-strategy outputs (cobblestone, coal, raw iron/copper/gold, lapis, redstone, diamond, emerald, obsidian, flint) are planning terminals acquired from the world;
- iron/copper/gold ingots are explicit smelting procedures;
- iron pickaxe now plans through `raw_iron -> furnace -> smelt -> iron_ingot -> iron_pickaxe`;
- station execution is generic for crafting tables and furnaces and reuses existing infrastructure;
- missing smelting fuel invokes the existing wood/fuel recovery path;
- if a genuine plan cycle remains, `!brain goal replan` prints the cycle path.

## Elytra ACK fix

The Fabric control socket waits 3.5 seconds, while an Elytra takeoff attempt can run for ~4.2 seconds. The bridge previously awaited the whole skill before replying.

`!brain glide` now launches the temporal skill asynchronously and immediately returns `elytra_started`. Use `!brain glide status` to inspect the final result and pulse count.

## Verification

- 44/44 JavaScript regression tests PASS
- `tools/smoke_test.py` PASS
- N = 165122
- E = 10511038
- effective_edges = 5016094
- Minecraft motor spike delta = 189
- new regression injects an explicit `iron_ingot <-> iron_block` cycle and verifies that the iron-pickaxe plan bypasses it through raw iron + smelting
- new regression verifies immediate non-blocking Elytra ACK

## Artifacts

- `BrainFly-0255-RUNTIME-HOTFIX.zip` SHA-256 `44018dcc9163a4284b77b8bb5abf85630e73d6f1445bfeefe6ea5b11e05533fd`
- `BrainWorkbench-Minecraft-2026.09.20-0255.zip` SHA-256 `19194ed1a532364c3fcc2fc652579c0ba350be7146486a53590c9e1187acdb65`
- `BrainFly-0255-IRON-PLAN-GLIDE-ACK.patch` SHA-256 `bf6ca03a4d113b7ff7ebf5f25a6e0d3f2f50fa3400ed8bf6a990cf3876f56f04`

This is a runtime-only hotfix over 0254. The Fabric mod does not need rebuilding.
