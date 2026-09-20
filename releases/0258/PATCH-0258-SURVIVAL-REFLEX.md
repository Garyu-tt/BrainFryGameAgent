# 0258 implementation notes

## Runtime changes
- `minecraft/autonomy-supervisor.mjs`: event-driven hostile escape, survival locks, critical-hunger acquisition, respawn/death-spiral handling.
- `minecraft/minecraft-bridge.mjs`: immediate reflex trigger from health events, burst-damped/rate-limited damage reinforcement, survival commands/status.
- cumulative 0257 runtime modules `embodied-agency.mjs` and `gameplay-duties.mjs` remain bundled so the hotfix is safe to apply directly over the live 0242 workbench.

## Survival arbitration
`survival > acquisition/interaction > active goal/Destiny > household duties`

The new reflex does not wait for the normal autonomy scheduler. It can cancel navigation, release interaction/acquisition ownership, orient away from the nearest hostile, sprint and jump for a bounded interval, then leave a survival lock before ordinary work resumes.

## Reinforcement protection
Repeated hits inside one hostile encounter no longer each receive an identical full-strength aversive learning update. Damage updates are rate-limited and scaled by burst count; explicit death remains a full aversive event.

## State safety
No config or learned state is replaced. Existing memory, snapshots, logs and external knowledge packs remain intact.
