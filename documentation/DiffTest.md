# `DiffTest.java`

Source: [`test/DiffTest.java`](../test/DiffTest.java)

## Purpose

`DiffTest` is a standalone differential harness comparing the local `Board` implementation against the course server's `GameAshtonTablut` rules.

## Code map

- Conversion helpers translate local piece codes and positions to server states.
- `checkPosition()` checks every same-rank/file destination for each piece on move, compares legality, applies legal moves in both implementations, then checks board and turn/winner equivalence. Local moves are unmade after comparison so the input position remains reusable.
- `randomPosition()` builds varied positions; the main routine also walks random games from the normal opening, with capture-biased modes to exercise special cases.
- Output counters summarize tested positions, legal pairs, captures, wins, and mismatches. A mismatch prints a bounded diagnostic and returns a failing process status.

## Design decisions

The test compares the full legal destination set rather than only sampled moves, which makes move-generation omissions visible. Randomized positions and games broaden coverage beyond the opening. A fixed default seed makes a failing run reproducible; pass a different seed to explore another sequence. This harness requires the course server classes on its classpath and is not part of the normal player build.
