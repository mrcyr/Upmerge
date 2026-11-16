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
- [ ] Add coin payment when the native economy/shop is ported; current emergency flow uses stored split charges only.

## Queue parity — original 4.11

- [x] Two-slot queue: current drop and upcoming preview.
- [x] Weighted random selection from six consecutive powers of two for the current goal tier.
- [x] On reaching the goal, double the goal and reroll both queue slots.
- [ ] Verify the exact original tier progression and weights against the source before changing them.

## Native port progress

- [x] Board snapshot serialization/restoration for settled states.
- [x] Persist board, score, direction, combo, and queue slots using libGDX Preferences.
- [ ] Persist all remaining 4.11 run fields (shots, elapsed time, run stats, tools/charges, etc.) once their native systems exist.
- [ ] Add complete menu flow: New Game / Continue / Settings.
- [x] Replace the default ASCII-only bitmap font with a runtime-generated Android system font atlas covering Russian Cyrillic for HUD and overlays.
- [x] Add emergency capacity recovery overlay, split-selection mode, and explicit end-run screen with restart.
- [x] Add persistence regression tests for queue + board consistency after interrupted launches.
