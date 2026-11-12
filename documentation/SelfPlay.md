# `SelfPlay.java`

Source: [`test/SelfPlay.java`](../test/SelfPlay.java)

## Purpose

`SelfPlay` runs complete games with the course server as referee and local search players for both sides. It is intended for comparing evaluation weights and collecting search statistics.

## Code map

- `toBoard()` converts the server state for search; `parseEval()` reads comma-separated feature weights or uses defaults.
- `play()` creates a fresh referee and independent search instances, optionally plays random opening plies, then asks the appropriate side's searcher for each move. The server validates every action; illegal actions are reported distinctly. The shared `GameHistory` tracks repetition for both searchers.
- `main()` reads game count, milliseconds per move, seed, and optional white/black weights, then reports outcomes and aggregate nodes/time/depth statistics.

## Design decisions

The referee remains authoritative, so the harness measures the same legality and terminal rules used in competition. Separate `Search` objects allow each side to keep its own table and evaluate with different weights. A seeded random opening avoids testing only identical opening lines while keeping experiments reproducible. The harness depends on external course server classes and is not included in the standard build.
