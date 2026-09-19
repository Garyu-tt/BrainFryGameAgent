# 0242 raw-dig stability patch

Patch target: `BrainWorkbench-Minecraft-2026.09.19-0241`.

This branch does **not** replace the repository's newer `0351` recovery snapshot. It preserves a focused, reviewable fix for the `approached_raw -> dig_failed` failure observed on the 0241 runtime.

## What changed

- raw-resource selection prefers a candidate that Mineflayer reports as diggable and visible;
- resource navigation uses `canDig=false` while positioning, so pathing does not mutate the mining target;
- the block is re-read from the live world after navigation and again immediately before digging;
- movement is stopped before aim/dig;
- `bot.canDigBlock()` and `bot.canSeeBlock()` are checked before `bot.dig()`;
- `bot.dig()` is time-bounded using the block's dig time;
- failure logs include block/reach/visibility;
- `raw_not_reachable` and `raw_target_gone` are treated as recoverable resource-search failures by the autonomy supervisor;
- regression test added for stale block object + LOS/reach correction.

## Verification

`23/23` JavaScript regression tests under `tools/test_*.mjs` pass on the patched 0242 tree.

Local packaged release SHA-256:

`8dff231fd7037ed233e907549f7af78345f5be2bfb88b63d78a8304eec31046e`

Files in this branch:

- `BrainFly-0242-dig-stability.patch` — unified diff against 0241;
- `test_raw_dig_reach_los.mjs` — focused regression test.
