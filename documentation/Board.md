# `Board.java`

Source: [`src/tablutai/Board.java`](../src/tablutai/Board.java)

## Purpose

`Board` is the rules and position model for the 9×9 Ashton-rule game. It owns the piece array, side to move, winner, piece counts, king location, and Zobrist hash. It also generates moves, applies captures and win conditions, and supports reversible moves for search.

## Code map

- Static tables (`CAMP`, `EDGE`, `ROW`, `COL`, `RAYLEN`, `DELTA`) precompute board geometry and special squares once. This keeps hot move-generation loops small and avoids repeated coordinate calculations.
- `setStart()` and `setFrom()` initialize a position; `finishSetup()` derives counts and the hash from the cells so all cached state agrees.
- `genMoves()` scans rook-like rays and enforces throne/citadel restrictions while generating. Moves are packed as `from | (to << 7)` to pass a move as one integer without allocating a move object at every search node.
- `scanCaptures()` and `kingCaptured()` reproduce the server's different rules for white soldiers, black soldiers, and the king. `captureCount()` uses temporary board changes and restores them immediately so move ordering can estimate captures without committing a move.
- `make()` updates the board, incremental hash, counts, winner, and undo record. `unmake()` restores those values, which lets depth-first search share one board safely.
- `raysFrom()` and `kingHasOpenRay()` support escape detection and evaluation. Coordinate helpers are used at the protocol and diagnostic boundaries.

## Design decisions

The flat 81-cell array makes neighboring squares addressable by fixed offsets. `RAYLEN` is checked before applying an offset so scans stop at board edges. The hash uses deterministic Zobrist values: a given position receives the same key between runs, which makes debugging and repetition handling reproducible. Captured pieces are stored in a small fixed undo record because a move can capture at most a few adjacent soldiers; copying all 81 squares at every search node would be much more expensive.

The rules are intentionally encoded to match the course server's quirks, which are summarized in the class comment. Keep those conditions synchronized with the reference implementation when changing rules.
