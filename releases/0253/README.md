# BrainFly 2026.09.20-0253 — Manual Feedback Credit Hotfix

Live event logs exposed a severe feedback-input bug:

- teacher.good could fire at keyboard-repeat speed, producing dozens of full-strength reward events in a few seconds;
- teacher.no showed the same behavior;
- each accepted manual feedback event triggered neural reinforcement across thousands of changed edges;
- episodic credit was assigned to the teacher actor rather than BrainFly's recent behavior.

## 0253 fixes

- bridge-side manual feedback refractory gate: 700 ms;
- burst cap: 4 accepted feedback events per 5 seconds per teacher+valence;
- duplicate feedback packets are suppressed before neural reinforcement;
- manual feedback snapshots BrainFly's current goal/navigation/interaction/controls and credits BrainFly;
- episodic credit is bounded to six meaningful recent BrainFly events;
- scene/input_state/feedback noise is excluded from manual credit;
- the same episode cannot receive the same feedback repeatedly within 1.2 seconds;
- Fabric Good/Bad hotkeys use rising-edge semantics and drain GLFW/OS key-repeat;
- vanilla chest feedback buttons use a 650 ms same-slot debounce;
- diagnostics: `!brain feedback`, `!brain feedback status`, `!brain feedback reset`;
- `!brain stats` includes accepted/suppressed feedback counts.

0253 is cumulative over 0252 and retains the persistent infrastructure, mine/resource provenance and navigation improvements.

## Verification

- 41/41 JavaScript regression tests PASS
- `tools/smoke_test.py` PASS
- feedback-gate regression verifies refractory and burst limiting
- episodic-credit regression verifies Teacher events are not rewarded while BrainFly events are

## Artifacts

- `BrainFly-0253-ONECLICK.zip` SHA-256 `402eba2d6eaaccc5bf1d0aca49deccda10297090969eb59f175b62dbf9683851`
- `BrainFly-Control-Fabric-1.21.1-2026.09.20-0253.zip` SHA-256 `fb5483daa154be4c6792a884858b6a79a2dc5fbe524fa3b682c2e6e90ad8b66f`
- `BrainWorkbench-Minecraft-2026.09.20-0253.zip` SHA-256 `fdfebcda920c6622d28ca6fbe6cb22095380d1436e1db309dfe61926dae7f5c8`
- `BrainFly-0253-FEEDBACK-CREDIT.patch` SHA-256 `9248f8c252eca768b399b492e9b7f9745cce703a9755c716548e9e9df9b1528b`

The one-click installer preserves config, memory, logs, snapshots and external knowledge packs.
