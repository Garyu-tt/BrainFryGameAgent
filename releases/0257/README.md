# BrainFly 2026.09.20-0257 — Duty Arbitration Regression Hotfix

Live play after 0256 exposed an executive-arbitration regression: Gameplay Duties, schematics and den/home work could seize execution between Destiny steps, force-claim the current location as home, and continue while BrainFly was hungry or under repeated attack.

## Root cause

0256 added useful persistent obligations, but background settlement work did not consistently yield to:
- active material / Destiny goals;
- survival pressure;
- active acquisition/interaction contracts;
- repeated deaths.

The safety watchdog also only interrupted damage bursts when navigation or an interaction lease was active, allowing a stationary/building BrainFly to keep taking hits.

## 0257 priority

`survival > acquisition/interaction > active goal/Destiny > household duties`

Changes:
- Gameplay Duties defer while an explicit goal is active.
- Background schematic and den builders use the same executive gate.
- Duties defer below 16 HP or 12 food by default.
- Home duty no longer force-claims a low-quality site.
- Early home duty is tool-tier gated instead of firing immediately after spawn.
- Three deaths within 120 seconds suspend household/background building for 300 seconds.
- Repeated damage triggers a local escape even with no current navigation/interaction lease.
- Panic behavior does not run toward home while the bot is actively taking damage.
- 0255 iron-pickaxe planner and Elytra async-ACK tests remain green.

## Verification

- Node syntax checks PASS for all 4 modified runtime modules.
- `test_0257_duty_arbitration.mjs` PASS.
- `test_iron_pickaxe_procedure_plan.mjs` PASS.
- `test_elytra_async_ack.mjs` PASS.

## Artifacts

- `BrainFly-0257-RUNTIME-HOTFIX.zip` SHA-256 `8e2bb8067f7a24930ada26569a66c19b3ca74382be6625f95d0a057cb12c121e`
- `BrainFly-0257-DUTY-ARBITRATION.patch` SHA-256 `53a462d327d57f3adb7a9488ed9a7b029f59f6193b9d26971a7ef23ea3d467ae`

This is a runtime-only patch over 0256. Fabric Control does not need rebuilding. The installer preserves config, memory, logs, snapshots and external knowledge packs.
