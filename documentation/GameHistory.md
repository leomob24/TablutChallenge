# `GameHistory.java`

Source: [`src/tablutai/GameHistory.java`](../src/tablutai/GameHistory.java)

## Purpose

`GameHistory` records hashes from server states so `Search` can identify repetition draws along the current game history.

## Code map

`onState()` ignores the initial state because it is not part of the server's repeat list. Later states are appended; if the total number of pieces fell, a capture occurred and the history window is cleared before adding the new position. `size()` and `array()` expose the valid prefix to search. `clear()` resets the tracker for reuse.

## Design decisions

Position hashes compactly identify the board and side to move. Comparing total piece counts detects a capture without storing full boards or receiving a separate capture event. The backing array grows geometrically and is reused across moves to avoid routine allocations.
