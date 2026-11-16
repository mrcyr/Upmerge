# UpMerge native port — gameplay parity checklist

Reference: original Android/WebView build 4.11 (`app/src/main/assets/index.html`).

## Critical gameplay rules — do not regress

- [ ] **Per-column hard capacity: 7 cubes** (the board has 7 rows).
- [ ] A column with fewer than 7 cubes accepts a new cube in the first free cell from the active gravity edge.
- [ ] A full column accepts a launch only when the incoming value matches the cube on the entry edge; this is a forced merge into that edge cube.
- [ ] A full column with a mismatching value rejects the launch. Show the 4.11-style feedback: `МАКСИМУМ 7 — НУЖЕН <value>`.
- [ ] A column with a moving/falling cube rejects another launch until the motion settles.
- [ ] After every merge, compact each lane toward gravity without changing the relative order of cubes.
- [ ] When no lane can legally accept the current value, run the board-capacity check. If a split charge is available, offer emergency split; otherwise end the run. Do not silently allow an impossible move.
- [ ] Emergency split consumes a charge/coins and asks the player to select one cube to remove; then resume and re-check board capacity.

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
- [ ] Add emergency capacity recovery UI and split-selection mode.
- [ ] Add persistence regression tests for queue + board consistency after interrupted launches.
