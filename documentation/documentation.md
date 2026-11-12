# Tablut Player Documentation

## Overview

This repository contains a Java 8+ player for the Ashton-rule 9×9 Tablut game. The executable connects to the course game server, reads JSON game states over a length-prefixed TCP protocol, chooses a legal move, and sends it back. The source is under `src/tablutai/`; `TablutPlayer.jar` is the packaged executable and includes Gson.

## Project layout

| Path | Purpose |
| --- | --- |
| `src/tablutai/Main.java` | Parses command-line arguments and starts the client. |
| `src/tablutai/Client.java` | Connects to the server, reads/writes protocol messages, tracks history, and requests moves from search. |
| `src/tablutai/Board.java` | 9×9 board representation, legal move generation, captures, win conditions, and make/unmake operations. |
| `src/tablutai/Eval.java` | Static position evaluation from White’s perspective. |
| `src/tablutai/Search.java` | Time-bounded iterative-deepening negamax search with alpha-beta/PVS, transposition table, and move ordering. |
| `src/tablutai/GameHistory.java` | Tracks position hashes since the last capture for the repeated-position draw rule. |
| `runmyplayer.sh` | Convenience launcher for the packaged player. |
| `build.sh` | Compiles sources and rebuilds `TablutPlayer.jar`. |
| `test/DiffTest.java` | Compares the local board rules and move generation against the course server implementation. |
| `test/SelfPlay.java` | Runs games using the course server as referee and the local search player for both sides. |

Each Java source has a focused guide covering its code structure and the reasons behind its main implementation choices:
[`Board`](Board.md), [`Client`](Client.md), [`Eval`](Eval.md), [`GameHistory`](GameHistory.md), [`Main`](Main.md), [`Search`](Search.md), [`DiffTest`](DiffTest.md), and [`SelfPlay`](SelfPlay.md).

## Build and run

Build with a JDK installed:

```sh
./build.sh
```

The script compiles against the included `gson-2.2.2.jar`, targets Java 8, and packages Gson into `TablutPlayer.jar`.

Run directly with Java:

```sh
java -jar TablutPlayer.jar ROLE [TIMEOUT_SECONDS] [SERVER_IP] [PLAYER_NAME]
```

Or use the wrapper (which supplies the first three arguments):

```sh
./runmyplayer.sh ROLE TIMEOUT_SECONDS SERVER_IP
```

`ROLE` must be `WHITE` or `BLACK` (case-insensitive). The direct Java invocation defaults to a 60-second timeout, `localhost`, and the name `VikingAB`. The wrapper expects all three arguments to be supplied. White connects to server port 5800 and Black to port 5801. The client retries the connection for up to about two minutes if the server is not ready.

The per-move search budget is calculated as 85% of the server timeout minus 500 ms, with a minimum of 200 ms. Search uses a maximum iterative-deepening depth of 64 in the client; the actual depth depends on the position and available time.

## Server communication

Messages use a four-byte big-endian length followed by that many UTF-8 bytes containing JSON. The first outbound message is the player name as a JSON string. Each incoming state has a board and a turn, for example the structure expected by `Client.StateDto`. When it is the player’s turn, the client sends an action with `from`, `to`, and `turn` fields. Coordinates are files `a`–`i` and ranks `1`–`9` (for example, `e4`). Terminal turns (`WHITEWIN`, `BLACKWIN`, or `DRAW`) end the client loop.

## Game representation and rules

`Board` stores the 81 squares in a flat array, with index `row * 9 + column`. Piece codes are `EMPTY`, `WHITE`, `BLACK`, and `KING`; the side-to-move is stored separately. Moves are packed into an integer as `from | (to << 7)`.

The implementation documents and models the course server’s Ashton-rule details, including the throne and citadel movement restrictions, side-specific soldier capture rules, the king’s special capture conditions, and the king winning by reaching an edge square. `make()` updates the board, hash, counts, and winner; `unmake()` restores the previous position using an undo stack. Zobrist hashes support search transpositions and repetition detection.

## Move selection

`Eval.evalWhite()` scores material and king-related features from White’s perspective, including open escape rays, king mobility and threats, proximity to the edge, hostile adjacency, and nearby soldiers. `evalSide()` flips the score for the side to move. The default weights are in `Eval.DEFAULTS`.

`Search.think()` searches successive depths until the time budget expires or a decisive result is found. The search uses negamax with alpha-beta pruning and principal-variation search, plus a Zobrist transposition table. Move ordering prioritizes transposition-table moves, captures, king escape opportunities, forced blocks, killer moves, and history scores. A fallback legal move is sent if search throws an exception in the client.

`GameHistory` receives each server state and clears the saved repetition sequence after a capture. The search also tracks positions along its current line so repeated positions can be scored as draws.

## Diagnostics and tests

The client prints each selected move with the completed search depth, score, node count, and elapsed time. It prints the final game result when the server reports a terminal state.

The two Java files under `test/` are standalone harnesses rather than a configured test framework. They depend on the course server classes (`it.unibo.ai.didattica.competition.tablut.domain.*`), which are not included in this project’s listed files. `DiffTest` checks move legality and resulting states; `SelfPlay` can compare weight sets and report game/search statistics. Their command-line usage is described in each source file’s class comment.

## Configuration notes

- Change the default player name in `Main.DEFAULT_NAME` and rebuild the jar.
- Evaluation defaults and feature names are defined by `Eval.DEFAULTS` and `Eval.NAMES`.
- The client’s transposition table is created with 22 bits in `Client`; the standalone self-play harness uses 20 bits per search instance.
- The client currently logs every move (`moveNo % 1 == 0`), regardless of the `verbose` flag.
