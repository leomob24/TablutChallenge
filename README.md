# Tablut player (alpha-beta) — v1

## Contenuto
- `runmyplayer.sh`  : script di avvio richiesto dalla consegna (`./runmyplayer.sh WHITE 60 <IP_SERVER>`; ruolo case-insensitive)
- `TablutPlayer.jar`: player già compilato (Java 8+), Gson incluso
- `src/tablutai/`   : sorgenti (Board, Eval, Search, GameHistory, Client, Main)
- `test/`           : DiffTest (confronto col motore del server) e SelfPlay (partite con arbitro reale)
- `build.sh`        : ricompila il jar (serve un JDK)

## Installazione nella VM
1. Copiare il contenuto di questa cartella in `/home/tablut/tablut/` (togliere il vecchio `RandomPlayer.jar`, ma tenete una copia: serve come avversario di prova).
2. `chmod +x /home/tablut/tablut/runmyplayer.sh`
3. Provare: terminale 1 server, terminale 2 `./runmyplayer.sh WHITE 60 localhost`, terminale 3 un random black
   (come da README del repo: `ant server`, `ant randomblack`).

## Parametri
`runmyplayer.sh ROLE TIMEOUT SERVER_IP` — il player usa circa l'85% del timeout meno 0,5 s per mossa.
Il nome inviato al server è `VikingAB` (il server ne tiene solo i primi 10 caratteri alfanumerici);
si cambia in `src/tablutai/Main.java` (DEFAULT_NAME) e con `./build.sh`.
