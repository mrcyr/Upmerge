# UpMerge native port — gameplay parity checklist

Reference: original Android/WebView build 4.11 (`app/src/main/assets/index.html`).

## Critical gameplay rules — do not regress

- [x] **Per-column hard capacity: 7 cubes** (the board has 7 rows).
- [x] A column with fewer than 7 cubes accepts a new cube in the first free cell from the active gravity edge.
- [x] A full column accepts a launch only when the incoming value matches the cube on the entry edge; this is a forced merge into that edge cube.
- [x] A full column with a mismatching value rejects the launch and shows feedback: `МАКСИМУМ 7 — НУЖЕН <value>`.
- [x] A column with a moving/falling cube rejects another launch until the motion settles.
- [x] After every merge, compact each lane toward gravity without changing the relative order of cubes.
- [x] When no lane can legally accept the current value, run the board-capacity check; offer emergency split when charged and expose an explicit end-run action.
- [x] Emergency split consumes a saved charge and asks the player to select one cube to remove; then resume and re-check board capacity.
- [x] Engine exposes a stable-id removal operation and compacts the affected lane.
- [x] Add persistent coin rewards for merges and a native shop with separate packs for all five tools.
- [x] Add matching in-game tool buttons: Разбить, Переместить, Обмен, Бомба, Отмена.
- [x] Port board operations for moving a cube, swapping two cubes, and a 3×3 bomb removal.
- [x] Add one-step undo for the last launch, restoring the settled board and queue.
- [x] Add persistent native achievements with one-time coin rewards and a dedicated screen.

## Queue parity — original 4.11

- [x] Two-slot queue: current drop and upcoming preview.
- [x] Weighted random selection from six consecutive powers of two for the current goal tier.
- [x] On reaching the goal, double the goal and reroll both queue slots.
- [x] Verify the exact original tier progression and weights against the 4.11 source.
- [x] Start at 2048; after a merged value >= goal, double goal and reroll both queue slots.
- [x] Preserve goal progress across ordinary restarts; reset to 2048 only for a fresh game.
- [x] Preserve 4.11 rounding: merge result is the smallest power of two >= (cube value × group size).
- [x] Match 4.11 compact suffixes through the currently supported `long` range: K, M, B, T, Qa, Qi.
- [ ] Extend beyond signed 64-bit only if/when BigInteger migration is approved.

## Native port progress

- [x] Board snapshot serialization/restoration for settled states.
- [x] Persist board, score, direction, combo, and queue slots using libGDX Preferences.
- [x] Restore the challenge target and terminal run state; clamp restored timer and split charges to valid ranges.
- [x] Persist balances for split, move, swap, bomb, and undo tool charges across new runs and app restarts.
- [x] Add persistent lifetime statistics: games, merges, launches, best combo, largest cube, and the six most recent completed results, with a dedicated statistics screen.
- [x] Add a rotating daily task (launches, merges, or score), persistent progress, a claimable coin reward, and a dedicated task screen.
- [ ] Persist remaining per-run fields such as elapsed time and full run snapshot details.
- [x] Add complete menu flow: New Game / Continue / Settings (including a persistent merge-haptics toggle).
- [x] Add initial mode selector for Classic / Timed (90 seconds) / Challenge.
- [x] Add timed countdown, visible timer HUD, challenge goal completion, and basic pause / resume / restart / return-to-menu controls.
- [x] Show current level goal in the HUD and brief feedback when the goal advances.
- [x] Show a full-screen celebratory level-up announcement with glow, rays, new target value, and explicit continue action.
- [x] Replace the default ASCII-only bitmap font with a runtime-generated Android system font atlas covering Russian Cyrillic for HUD and overlays.
- [x] Add emergency capacity recovery overlay, split-selection mode, and explicit end-run screen with restart.
- [x] Add separate haptic pulses for each cube starting gravity movement (including a newly launched cube), each landing, and every completed merge.
- [x] Add engine regression tests for launch, landing, gravity-repack, and emergency-removal haptic event counts.
- [x] Validate restored queue goals as supported power-of-two tiers, including the signed-64-bit ceiling.
- [x] Reject board snapshots containing gaps against the active gravity edge; preserve live state on rejection.
- [x] Reject duplicate stable cube IDs in restored board snapshots.
- [x] Prevent stable cube ID overflow when the restored ID counter reaches the signed-64-bit limit.
- [x] Add persistence regression tests for queue + board consistency after interrupted launches.
