import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.unibo.ai.didattica.competition.tablut.domain.*;
import tablutai.Board;

/**
 * Differential test: my Board against the course server's GameAshtonTablut.
 * For every position, every (from,to) pair of a piece of the side to move is checked:
 *  - legal for the server  <=>  legal for my generator
 *  - the resulting state (board, turn/win) is identical
 */
public class DiffTest {
    static PrintStream realOut = System.out;
    static GameAshtonTablut server;
    static long positions = 0, pairs = 0, legalPairs = 0, captureMoves = 0, kingCaps = 0, whiteWins = 0, mismatches = 0;
    static Random rnd;

    static State.Pawn toPawn(int p) {
        switch (p) {
            case Board.WHITE: return State.Pawn.WHITE;
            case Board.BLACK: return State.Pawn.BLACK;
            case Board.KING: return State.Pawn.KING;
            default: return State.Pawn.EMPTY;
        }
    }

    static StateTablut toServer(Board bd) {
        StateTablut st = new StateTablut();
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) st.getBoard()[r][c] = toPawn(bd.b[r * 9 + c]);
        if (bd.b[Board.THRONE] == Board.EMPTY) st.getBoard()[4][4] = State.Pawn.THRONE;
        st.setTurn(bd.side == Board.WHITE_SIDE ? State.Turn.WHITE : State.Turn.BLACK);
        return st;
    }

    static boolean sameResult(StateTablut s, Board bd) {
        // turn / winner
        State.Turn t = s.getTurn();
        if (t == State.Turn.WHITEWIN) { if (bd.winner != Board.WHITE_WIN) return false; }
        else if (t == State.Turn.BLACKWIN) { if (bd.winner != Board.BLACK_WIN) return false; }
        else if (t == State.Turn.DRAW) return false;
        else {
            if (bd.winner != Board.NONE) return false;
            if ((t == State.Turn.WHITE) != (bd.side == Board.WHITE_SIDE)) return false;
        }
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) {
                State.Pawn sp = s.getBoard()[r][c];
                int mine = bd.b[r * 9 + c];
                State.Pawn expected = toPawn(mine);
                if (expected == State.Pawn.EMPTY && r == 4 && c == 4) expected = State.Pawn.THRONE;
                if (sp != expected) return false;
            }
        return true;
    }

    static void checkPosition(Board bd, int[] moves, StringBuilder err) {
        int n = bd.genMoves(moves);
        Set<Integer> mine = new HashSet<>();
        for (int i = 0; i < n; i++) mine.add(moves[i]);
        StateTablut base = toServer(bd);
        State.Turn turn = base.getTurn();
        positions++;
        int seen = 0;
        for (int from = 0; from < 81; from++) {
            int p = bd.b[from];
            if (p == Board.EMPTY) continue;
            boolean ownPiece = bd.side == Board.WHITE_SIDE ? (p != Board.BLACK) : (p == Board.BLACK);
            if (!ownPiece) continue;
            for (int to = 0; to < 81; to++) {
                if (to / 9 != from / 9 && to % 9 != from % 9) continue;
                if (to == from) continue;
                pairs++;
                Action a;
                try {
                    a = new Action(Board.cellName(from), Board.cellName(to), turn);
                } catch (Exception e) { throw new RuntimeException(e); }
                StateTablut copy = base.clone();
                State res = null;
                boolean legalServer;
                try {
                    res = server.checkMove(copy, a);
                    legalServer = true;
                } catch (Exception e) {
                    legalServer = false;
                }
                int m = from | (to << 7);
                boolean legalMine = mine.contains(m);
                if (legalServer != legalMine) {
                    mismatches++;
                    err.append("LEGALITY mismatch ").append(Board.moveToString(m)).append(" server=").append(legalServer)
                            .append(" mine=").append(legalMine).append("\n").append(bd).append("\n");
                    continue;
                }
                if (legalServer) {
                    legalPairs++;
                    seen++;
                    int before = bd.totalPieces();
                    bd.make(m);
                    if (bd.totalPieces() != before) captureMoves++;
                    if (bd.winner == Board.BLACK_WIN) kingCaps++;
                    if (bd.winner == Board.WHITE_WIN) whiteWins++;
                    if (!sameResult((StateTablut) res, bd)) {
                        mismatches++;
                        err.append("RESULT mismatch after ").append(Board.moveToString(m)).append("\nSERVER:\n")
                                .append(res.toString()).append("\nMINE:\n").append(bd).append("\nBEFORE:\n");
                        bd.unmake();
                        err.append(bd).append("\n");
                    } else {
                        bd.unmake();
                    }
                }
            }
        }
        if (seen != n) {
            mismatches++;
            err.append("COUNT mismatch seen=").append(seen).append(" mine=").append(n).append("\n").append(bd).append("\n");
        }
    }

    static Board randomPosition() {
        int[] cells = new int[81];
        int w = rnd.nextInt(9), bl = rnd.nextInt(17);
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < 81; i++) if (i != 40) free.add(i);
        Collections.shuffle(free, rnd);
        int idx = 0;
        // king: often near throne, sometimes on throne
        int kr = rnd.nextInt(10);
        int kpos;
        if (kr < 3) kpos = 40;
        else if (kr < 6) { int[] near = {31, 49, 39, 41}; kpos = near[rnd.nextInt(4)]; }
        else kpos = free.get(idx++);
        cells[kpos] = Board.KING;
        // keep own pieces away from the camps with probability (reachable-ish positions)
        for (int i = 0; i < w && idx < free.size(); i++) {
            int c = free.get(idx++);
            if (c == kpos || Board.CAMP[c] && rnd.nextInt(4) != 0) { i--; continue; }
            cells[c] = Board.WHITE;
        }
        for (int i = 0; i < bl && idx < free.size(); i++) {
            int c = free.get(idx++);
            if (c == kpos) { i--; continue; }
            cells[c] = Board.BLACK;
        }
        Board b = new Board();
        b.setFrom(cells, rnd.nextInt(2));
        return b;
    }

    static int pickMove(Board bd, int[] moves, int n, int mode) {
        if (mode == 0) return moves[rnd.nextInt(n)];
        // biased: prefer captures / king moves
        List<Integer> good = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int m = moves[i];
            if (bd.captureCount(m) > 0) good.add(m);
            else if (mode == 2 && bd.b[m & 127] == Board.KING) good.add(m);
        }
        if (!good.isEmpty() && rnd.nextInt(100) < 70) return good.get(rnd.nextInt(good.size()));
        return moves[rnd.nextInt(n)];
    }

    public static void main(String[] args) throws Exception {
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int randomPositions = args.length > 1 ? Integer.parseInt(args[1]) : 3000;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1234L;
        rnd = new Random(seed);
        server = new GameAshtonTablut(99, 0, "/tmp/tablut_garbage_logs", "x", "y");
        Logger.getLogger("GameLog").setLevel(Level.OFF);
        PrintStream nullOut = new PrintStream(new ByteArrayOutputStream() {
            @Override public synchronized void write(int b) { }
            @Override public synchronized void write(byte[] b, int o, int l) { }
        });
        System.setOut(nullOut);
        StringBuilder err = new StringBuilder();
        int[] moves = new int[400];
        long t0 = System.currentTimeMillis();
        for (int g = 0; g < games && mismatches == 0; g++) {
            Board bd = new Board();
            int mode = g % 3;
            for (int ply = 0; ply < 300 && bd.winner == Board.NONE; ply++) {
                checkPosition(bd, moves, err);
                if (mismatches > 0) break;
                int n = bd.genMoves(moves);
                if (n == 0) break;
                bd.make(pickMove(bd, moves, n, mode));
            }
        }
        for (int i = 0; i < randomPositions && mismatches == 0; i++) {
            Board bd = randomPosition();
            checkPosition(bd, moves, err);
        }
        System.setOut(realOut);
        realOut.println("positions=" + positions + " pairs=" + pairs + " legal=" + legalPairs
                + " captureMoves=" + captureMoves + " kingCaptures=" + kingCaps + " whiteWins=" + whiteWins
                + " mismatches=" + mismatches + " time=" + (System.currentTimeMillis() - t0) + "ms");
        if (mismatches > 0) realOut.println(err.substring(0, Math.min(err.length(), 4000)));
        System.exit(mismatches == 0 ? 0 : 1);
    }
}
