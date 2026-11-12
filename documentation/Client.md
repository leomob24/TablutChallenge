# `Client.java`

Source: [`src/tablutai/Client.java`](../src/tablutai/Client.java)

## Purpose

`Client` connects the player to the course server, translates protocol messages to local positions, invokes search on its turns, and sends the selected action.

## Code map

- `StateDto` and `ActionDto` describe the JSON fields exchanged with the server. Gson maps those fields directly.
- `writeString()` and `readString()` implement the framing: a four-byte big-endian byte length followed by UTF-8 data. The length is measured after UTF-8 encoding, not in Java characters.
- `connect()` selects port 5800 for White or 5801 for Black and retries because the server may start after the player.
- `pieceCode()` and `toBoard()` translate server state into the compact `Board` model; the turn is kept separately from board cells.
- `budgetNs()` leaves time for protocol/server overhead and applies a minimum search budget.
- `run()` announces the player name, records each received state for repetition handling, searches only when the active turn matches this player's role, and sends a from/to action. A legal-move fallback protects the protocol loop if search throws.

## Design decisions

The client reuses one `Search` instance so its transposition table and move-ordering knowledge can benefit later moves. The board is reconstructed from each authoritative server state rather than inferred from the previous move, avoiding drift if the server sends a corrected or terminal state. Search receives the server timeout with a safety margin because the server's clock includes communication and move processing.
