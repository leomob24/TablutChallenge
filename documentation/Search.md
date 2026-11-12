# `Search.java`

Source: [`src/tablutai/Search.java`](../src/tablutai/Search.java)

## Purpose

`Search` chooses a move using time-bounded iterative-deepening negamax. It combines alpha-beta pruning, principal-variation search, a Zobrist transposition table, repetition handling, and move ordering.

## Code map

- `think()` copies the root position, initializes the repetition line and deadline, checks trivial/immediate wins, then searches progressively deeper while retaining the last completed best move.
- `negamax()` handles terminal escape/capture situations, leaf evaluation, transposition-table cutoffs, legal move generation, recursive search, and table storage.
- `apply()`/`undo()` coordinate `Board.make()`/`unmake()` with repetition history. Captures restart the repetition window; a repeated hash returns the configured draw score.
- `scoreMoves()` prioritizes a transposition-table move, captures, urgent king escapes/blocks, then killer and history moves. `pick()` selects the best remaining move in place.
- Packed table values store score, depth, bound type, generation, and best move. Mate scores are adjusted by ply when stored/restored so the same position reached at different depths retains correct distance-to-mate meaning.
- `blackCanCaptureKing()` performs a cheap exposure check before scanning legal moves, improving leaf handling for immediate king danger.

## Design decisions

Iterative deepening gives the caller a usable move even if the deadline interrupts a later iteration. The deadline uses `System.nanoTime()` and is polled periodically to reduce clock-check overhead. PVS spends a narrow-window search on later moves and widens only promising candidates; late-move reduction similarly reduces selected quiet moves, then re-searches if they improve alpha. Search reuses per-ply arrays and a fixed-size transposition table to limit garbage collection during a timed search.
