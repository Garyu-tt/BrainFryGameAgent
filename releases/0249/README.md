# BrainFly 2026.09.19-0249 — body schema + vanilla chest controls + goal HUD

0249 keeps the 0248 exact teacher telemetry and Destiny-root campaign behavior, and addresses the next embodied-agent failure class.

## Body schema

BrainFly now models the avatar as an approximately 0.6 x 1.8 block body rather than a point.

- one-block-high openings are rejected;
- walls are rejected before repeated forward pushing;
- one-block steps require enough headroom;
- left/right body clearance is sampled for local escape;
- prolonged fallback blockage returns `body_clearance_blocked`;
- neural forward motor output is gated by body-volume clearance.

## Interaction ownership

Starting a high-level interaction cancels active navigation before the interaction lock is acquired. Planner-driven mining remains autonomous, while direct learned motor-level block breaking requires an exact teacher-target coordinate match. This reduces unexplained sideways breaking and prevents navigation from dragging BrainFly away from an active dig/use action.

## Vanilla controls

The custom drawn Fabric control screen has been removed. Pressing B invokes `/brainmenu`, which opens a genuine 9x6 vanilla generic container. Each item icon maps to a BrainFly command and responses are relayed to normal Minecraft chat.

The live BrainFly inventory remains available via `/braininv BrainFly`.

## Goal HUD

A compact top-right scoreboard-style HUD displays Destiny phase, campaign objective/directive, active material goal, next unmet plan step, and current action/navigation/interaction state.

## Verification

- 32/32 JavaScript regression tests PASS
- `tools/smoke_test.py` PASS
- Node syntax checks PASS
- `test_body_schema.mjs` covers one-block-high rejection, wall rejection, valid one-block step, and open-air clearance

Packaged artifacts:
- BrainFly-0249-ONECLICK.zip SHA-256 `09356c2b56da2ae3cb52ae7d9083f3bf7cf7d6792fe892e1c7dafc98e21e193c`
- BrainFly-Control-Fabric-1.21.1-2026.09.19-0249.zip SHA-256 `dbbb660b8cd2fc3bceb797f8664813630b629457d7aed8e95b9505fab23c30f0`
- BrainWorkbench-Minecraft-2026.09.19-0249.zip SHA-256 `3c1d8cd7262b9496ff122d649670a034fb46b1a3a6ddddcd7a8e982e4d95889e`
