# 0244 — mining interaction lock + Control Panel replies

Base: `2026.09.19-0243`.

## Mining fix

The neural motor loop previously remained free to issue movement and yaw while high-level `bot.dig()` was still in progress. This produced the live symptom: BrainFly started a block, ran/turned away, and only broke an occasional random block.

0244 adds one canonical `digBlockLocked()` path:

1. lock the exact block position;
2. stop native navigation and clear movement;
3. aim at the block center;
4. keep re-aiming and clearing movement for the whole dig;
5. bound the dig by a block-time-derived timeout;
6. release the interaction only after completion/error.

The bridge motor executor yields to `agency.busy` / interaction locks, so neural forward/back/yaw cannot fight a mining/crafting/use transaction.

## Control UI completion

The 0243 GUI transport is now bidirectional. `status_request` returns mode, goal, navigation/interaction lock, HP, hunger, inventory count and bridge version. GUI commands use silent execution and receive `control_result` packets, so button presses no longer spam BrainFly responses into Minecraft chat.

## Regression coverage

`tools/test_interaction_lock.mjs` creates a long-running mocked dig and asserts that:

- the interaction lock remains active until the dig finishes;
- `agency.busy` remains true during the transaction;
- movement is cleared repeatedly;
- gaze is re-aimed repeatedly while the dig is still unresolved;
- the lock and busy state release after completion.

Full suite: **25/25 PASS**.
