import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.unibo.ai.didattica.competition.tablut.domain.*;
import tablutai.*;

/**
 * Self-play with the real server rules as referee (default server settings: repeated=0, cache=-1).
 * Usage: SelfPlay games msPerMove [seedForOpening] [whiteWeightsFile blackWeightsFile]
 */
public class SelfPlay {
    static Board toBoard(State st) {
        int[] cells = new int[81];
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) {
                State.Pawn p = st.getPawn(r, c);
                int v = 0;
                if (p == State.Pawn.WHITE) v = Board.WHITE;
                else if (p == State.Pawn.BLACK) v = Board.BLACK;
                else if (p == State.Pawn.KING) v = Board.KING;
                cells[r * 9 + c] = v;
            }
        Board b = new Board();
        b.setFrom(cells, st.getTurn() == State.Turn.WHITE ? Board.WHITE_SIDE : Board.BLACK_SIDE);
        return b;
    }

    public static Eval parseEval(String spec) {
        Eval e = new Eval();
        if (spec == null || spec.equals("-")) return e;
        String[] p = spec.split(",");
        for (int i = 0; i < p.length && i < Eval.NW; i++) e.w[i] = Integer.parseInt(p[i].trim());
        return e;
    }

    /** Plays one game; returns +1 white win, -1 black win, 0 draw. */
    public static int play(Eval ew, Eval eb, long msW, long msB, int maxDepthW, int maxDepthB, Random rnd,
                           int randomOpeningPlies, boolean verbose, long[] stats) throws Exception {
        PrintStream real = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream() {
            @Override public synchronized void write(int b) { }
            @Override public synchronized void write(byte[] b, int o, int l) { }
        }));
        GameAshtonTablut game = new GameAshtonTablut(0, -1, "/tmp/tablut_garbage_logs", "w", "b");
        Logger.getLogger("GameLog").setLevel(Level.OFF);
        System.setOut(real);
        StateTablut st = new StateTablut();
        st.setTurn(State.Turn.WHITE);
        Search sw = new Search(ew, 20), sb = new Search(eb, 20);
        GameHistory hist = new GameHistory();
        Board b0 = toBoard(st);
        hist.onState(b0);
        int ply = 0;
        int[] tmp = new int[400];
        PrintStream nul = new PrintStream(new ByteArrayOutputStream());
        while (true) {
            State.Turn t = st.getTurn();
            if (t == State.Turn.WHITEWIN) return 1;
            if (t == State.Turn.BLACKWIN) return -1;
            if (t == State.Turn.DRAW) return 0;
            Board b = toBoard(st);
            boolean white = t == State.Turn.WHITE;
            int m;
            if (ply < randomOpeningPlies) {
                int n = b.genMoves(tmp);
                m = tmp[rnd.nextInt(n)];
            } else {
                Search s = white ? sw : sb;
                long ms = white ? msW : msB;
                int md = white ? maxDepthW : maxDepthB;
                long t0 = System.nanoTime();
                m = s.think(b, hist.array(), hist.size(), ms * 1_000_000L, md);
                stats[0] += s.nodes;
                stats[1] += (System.nanoTime() - t0) / 1000;
                stats[2] += s.reachedDepth;
                stats[3]++;
            }
            Action a = new Action(Board.cellName(m & 127), Board.cellName(m >>> 7), t);
            System.setOut(nul);
            try {
                st = (StateTablut) game.checkMove(st, a);
            } catch (Exception e) {
                System.setOut(real);
                real.println("ILLEGAL MOVE by " + t + ": " + a + " " + e.getClass().getSimpleName() + "\n" + b);
                return white ? -2 : 2;
            }
            System.setOut(real);
            hist.onState(toBoard(st));
            ply++;
            if (verbose) real.println(ply + " " + t + " " + a.getFrom() + a.getTo());
            if (ply > 400) return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        int games = Integer.parseInt(args[0]);
        long ms = Long.parseLong(args[1]);
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1;
        Eval ew = parseEval(args.length > 3 ? args[3] : null);
        Eval eb = parseEval(args.length > 4 ? args[4] : null);
        Random rnd = new Random(seed);
        int ww = 0, bw = 0, dr = 0, ill = 0;
        long[] stats = new long[4];
        for (int g = 0; g < games; g++) {
            int r = play(ew, eb, ms, ms, 64, 64, rnd, 4, false, stats);
            if (r == 1) ww++;
            else if (r == -1) bw++;
            else if (r == 0) dr++;
            else ill++;
            System.out.println("game " + g + " result " + r);
        }
        System.out.println("white=" + ww + " black=" + bw + " draw=" + dr + " illegal=" + ill
                + " nps=" + (stats[1] > 0 ? stats[0] * 1_000_000L / stats[1] : 0)
                + " avgDepth=" + (stats[3] > 0 ? (double) stats[2] / stats[3] : 0));
    }
}
