package tablutai;

/**
 * Iterative-deepening negamax with alpha-beta pruning (course: MIN-MAX with cutoff test + alpha-beta),
 * principal variation search, transposition table (Zobrist hashing), killer/history move ordering and
 * a repetition-aware draw rule that mirrors the server.
 */
public final class Search {
    public static final int MATE = 100000;
    public static final int INF = 1000000;
    static final int MAXPLY = 96;

    private final Board bd = new Board();
    private final Eval eval;

    private long deadline;
    private boolean stop;
    public long nodes;
    public int reachedDepth;
    public int bestScore;
    public int drawScore = 0;

    // transposition table
    private final int ttMask;
    private final long[] ttKey;
    private final long[] ttVal;
    private int gen = 0;

    private final int[][] mv = new int[MAXPLY + 2][320];
    private final int[][] sc = new int[MAXPLY + 2][320];
    private final int[][] killers = new int[MAXPLY + 2][2];
    private final int[][][] history = new int[2][81][81];
    private final boolean[] mark = new boolean[81];
    private final int[] tmpMoves = new int[320];

    // repetition tracking: hashes since last capture
    private long[] rep = new long[4096];
    private int repLen, repStart;
    private final int[] savedStart = new int[MAXPLY + 4];
    private int tmpScore;

    private static final int FLAG_EXACT = 0, FLAG_LOWER = 1, FLAG_UPPER = 2;

    public Search(Eval eval, int ttBits) {
        this.eval = eval;
        int size = 1 << ttBits;
        ttMask = size - 1;
        ttKey = new long[size];
        ttVal = new long[size];
    }

    public void clearTT() {
        java.util.Arrays.fill(ttKey, 0L);
        java.util.Arrays.fill(ttVal, 0L);
    }

    // ---------------------------------------------------------------- TT
    private static long packTT(int score, int depth, int flag, int gen, int move) {
        return (score & 0xFFFFFFFFL) | ((long) (depth & 0xFF) << 32) | ((long) flag << 40)
                | ((long) (gen & 0xFF) << 42) | ((long) (move & 0x3FFF) << 50);
    }

    private static int ttScore(long v) {
        return (int) (v & 0xFFFFFFFFL);
    }

    private static int ttDepth(long v) {
        return (int) ((v >>> 32) & 0xFF);
    }

    private static int ttFlag(long v) {
        return (int) ((v >>> 40) & 0x3);
    }

    private static int ttGen(long v) {
        return (int) ((v >>> 42) & 0xFF);
    }

    private static int ttMove(long v) {
        return (int) ((v >>> 50) & 0x3FFF);
    }

    private void ttStore(long key, int depth, int score, int flag, int move, int ply) {
        if (score > MATE - 1000) score += ply;
        else if (score < -MATE + 1000) score -= ply;
        int idx = (int) key & ttMask;
        long old = ttVal[idx];
        if (ttKey[idx] != 0 && ttKey[idx] != key && ttGen(old) == (gen & 0xFF) && ttDepth(old) > depth) return;
        ttKey[idx] = key;
        ttVal[idx] = packTT(score, depth, flag, gen, move);
    }

    // ---------------------------------------------------------------- helpers
    private int evalSide() {
        return eval.evalSide(bd);
    }

    /** Applies move; returns 0 = continue (position pushed), 1 = mover wins, 2 = draw by repetition. */
    private int apply(int m, int ply) {
        bd.make(m);
        savedStart[ply] = repStart;
        if (bd.winner != Board.NONE) {
            tmpScore = MATE - (ply + 1);
            return 1;
        }
        if (bd.lastCaptures > 0) {
            repStart = repLen;
        } else {
            for (int i = repStart; i < repLen; i++) {
                if (rep[i] == bd.hash) {
                    tmpScore = drawScore;
                    return 2;
                }
            }
        }
        if (repLen == rep.length) rep = java.util.Arrays.copyOf(rep, repLen * 2);
        rep[repLen++] = bd.hash;
        return 0;
    }

    private void undo(int ply, int code) {
        bd.unmake();
        if (code == 0) repLen--;
        repStart = savedStart[ply];
    }

    /** True if black (to move) can capture the king right now. Cheap pre-check first. */
    private boolean blackCanCaptureKing() {
        final int[] b = bd.b;
        final int kp = bd.kingPos;
        if (kp < 0) return false;
        boolean exposed;
        if (kp == Board.THRONE) {
            int c = 0;
            for (int d = 0; d < 4; d++) if (b[kp + Board.DELTA[d]] == Board.BLACK) c++;
            exposed = c >= 3;
        } else if (kp == Board.THRONE - 9 || kp == Board.THRONE + 9 || kp == Board.THRONE - 1
                || kp == Board.THRONE + 1) {
            int c = 0;
            for (int d = 0; d < 4; d++) {
                int q = kp + Board.DELTA[d];
                if (q != Board.THRONE && b[q] == Board.BLACK) c++;
            }
            exposed = c >= 2;
        } else {
            exposed = false;
            for (int d = 0; d < 4; d++) {
                if (Board.RAYLEN[d][kp] < 1) continue;
                int q = kp + Board.DELTA[d];
                if (b[q] == Board.BLACK || Board.CAMP[q]) {
                    exposed = true;
                    break;
                }
            }
        }
        if (!exposed) return false;
        int n = bd.genMoves(tmpMoves);
        for (int i = 0; i < n; i++) if (bd.captureCount(tmpMoves[i]) >= 100) return true;
        return false;
    }

    private int leaf(int ply) {
        if (bd.side == Board.BLACK_SIDE && blackCanCaptureKing()) return MATE - ply - 1;
        return evalSide();
    }

    // ---------------------------------------------------------------- move ordering
    private void scoreMoves(int ply, int n, int ttMove) {
        final int[] m = mv[ply];
        final int[] s = sc[ply];
        final int side = bd.side;
        boolean blackMustBlock = false;
        if (side == Board.BLACK_SIDE && bd.kingPos >= 0) {
            int kp = bd.kingPos;
            for (int d = 0; d < 4; d++) {
                int len = Board.RAYLEN[d][kp];
                if (len == 0) continue;
                int c = kp;
                boolean open = true;
                for (int k = 0; k < len; k++) {
                    c += Board.DELTA[d];
                    if (c == Board.THRONE || Board.CAMP[c] || bd.b[c] != Board.EMPTY) {
                        open = false;
                        break;
                    }
                }
                if (open) {
                    blackMustBlock = true;
                    c = kp;
                    for (int k = 0; k < len; k++) {
                        c += Board.DELTA[d];
                        mark[c] = true;
                    }
                }
            }
        }
        for (int i = 0; i < n; i++) {
            int mm = m[i];
            int v = 0;
            if (mm == ttMove) {
                v = 10_000_000;
            } else {
                int cap = bd.captureCount(mm);
                if (cap > 0) v = 1_000_000 + cap * 1000;
                int from = mm & 127, to = mm >>> 7;
                if (blackMustBlock && mark[to]) v += 600_000;
                if (side == Board.WHITE_SIDE && bd.b[from] == Board.KING) {
                    int rr = bd.raysFrom(to, from);
                    if (rr >= 2) v += 900_000;
                    else if (rr == 1) v += 500_000;
                    else v += 2_000;
                }
                if (v < 1_000_000) {
                    if (mm == killers[ply][0]) v += 400_000;
                    else if (mm == killers[ply][1]) v += 390_000;
                    v += history[side][from][to];
                }
            }
            s[i] = v;
        }
        if (blackMustBlock) java.util.Arrays.fill(mark, false);
    }

    /** Selection sort step: brings the best remaining move to position i. */
    private void pick(int ply, int i, int n) {
        final int[] m = mv[ply];
        final int[] s = sc[ply];
        int best = i;
        for (int j = i + 1; j < n; j++) if (s[j] > s[best]) best = j;
        if (best != i) {
            int t = m[i];
            m[i] = m[best];
            m[best] = t;
            t = s[i];
            s[i] = s[best];
            s[best] = t;
        }
    }

    // ---------------------------------------------------------------- search
    private int negamax(int depth, int alpha, int beta, int ply) {
        if ((++nodes & 2047) == 0 && System.nanoTime() >= deadline) stop = true;
        if (stop) return 0;
        if (bd.side == Board.WHITE_SIDE && bd.kingHasOpenRay()) return MATE - ply - 1;
        if (depth <= 0 || ply >= MAXPLY - 1) return leaf(ply);

        final long key = bd.hash;
        int ttMv = 0;
        final int idx = (int) key & ttMask;
        if (ttKey[idx] == key) {
            long v = ttVal[idx];
            ttMv = ttMove(v);
            if (ttDepth(v) >= depth) {
                int s = ttScore(v);
                if (s > MATE - 1000) s -= ply;
                else if (s < -MATE + 1000) s += ply;
                int f = ttFlag(v);
                if (f == FLAG_EXACT) return s;
                if (f == FLAG_LOWER && s >= beta) return s;
                if (f == FLAG_UPPER && s <= alpha) return s;
            }
        }

        final int n = bd.genMoves(mv[ply]);
        if (n == 0) return -(MATE - ply);
        scoreMoves(ply, n, ttMv);

        final int origAlpha = alpha;
        int best = -INF;
        int bestMove = 0;
        final int side = bd.side;
        for (int i = 0; i < n; i++) {
            pick(ply, i, n);
            final int m = mv[ply][i];
            final int code = apply(m, ply);
            int score;
            if (code != 0) {
                score = tmpScore;
            } else {
                int ndepth = depth - 1;
                if (i == 0) {
                    score = -negamax(ndepth, -beta, -alpha, ply + 1);
                } else {
                    // Later moves first get a cheap null-window search; only
                    // moves that improve alpha receive the full-width search.
                    int red = 0;
                    if (i >= 6 && depth >= 3 && sc[ply][i] < 380_000 && bd.lastCaptures == 0) red = 1;
                    score = -negamax(ndepth - red, -alpha - 1, -alpha, ply + 1);
                    if (!stop && score > alpha && (red > 0 || score < beta)) {
                        score = -negamax(ndepth, -beta, -alpha, ply + 1);
                    }
                }
            }
            undo(ply, code);
            if (stop) return 0;
            if (score > best) {
                best = score;
                bestMove = m;
                if (score > alpha) {
                    alpha = score;
                    if (alpha >= beta) {
                        if (sc[ply][i] < 1_000_000) {
                            if (killers[ply][0] != m) {
                                killers[ply][1] = killers[ply][0];
                                killers[ply][0] = m;
                            }
                            int h = history[side][m & 127][m >>> 7] + depth * depth;
                            history[side][m & 127][m >>> 7] = Math.min(h, 300_000);
                        }
                        break;
                    }
                }
            }
        }
        int flag = best <= origAlpha ? FLAG_UPPER : (best >= beta ? FLAG_LOWER : FLAG_EXACT);
        ttStore(key, depth, best, flag, bestMove, ply);
        return best;
    }

    /**
     * Searches the position and returns the best move (from | to&lt;&lt;7), or -1 if there is none.
     *
     * @param root     position to search
     * @param hist     hashes of the game since the last capture
     * @param histN    number of valid entries in hist
     * @param budgetNs time budget in nanoseconds
     * @param maxDepth maximum iterative deepening depth
     */
    public int think(Board root, long[] hist, int histN, long budgetNs, int maxDepth) {
        bd.copyFrom(root);
        if (histN + 8 > rep.length) rep = new long[(histN + 8) * 2];
        repLen = 0;
        for (int i = 0; i < histN; i++) rep[repLen++] = hist[i];
        repStart = 0;
        deadline = System.nanoTime() + budgetNs;
        stop = false;
        nodes = 0;
        reachedDepth = 0;
        gen = (gen + 1) & 0xFF;
        for (int i = 0; i < killers.length; i++) killers[i][0] = killers[i][1] = 0;
        for (int s = 0; s < 2; s++)
            for (int i = 0; i < 81; i++)
                for (int j = 0; j < 81; j++) history[s][i][j] >>= 3;

        final int[] rootMoves = new int[320];
        final int n = bd.genMoves(rootMoves);
        if (n == 0) return -1;
        if (n == 1) {
            bestScore = 0;
            return rootMoves[0];
        }
        // immediate wins
        for (int i = 0; i < n; i++) {
            bd.make(rootMoves[i]);
            boolean win = bd.winner != Board.NONE;
            bd.unmake();
            if (win) {
                bestScore = MATE - 1;
                return rootMoves[i];
            }
        }
        // initial ordering
        System.arraycopy(rootMoves, 0, mv[0], 0, n);
        scoreMoves(0, n, 0);
        for (int i = 0; i < n; i++) pick(0, i, n);
        System.arraycopy(mv[0], 0, rootMoves, 0, n);

        int best = rootMoves[0];
        bestScore = -INF;
        for (int depth = 1; depth <= maxDepth; depth++) {
            int alpha = -INF, beta = INF;
            int iterBest = -1, iterScore = -INF;
            for (int i = 0; i < n; i++) {
                final int m = rootMoves[i];
                final int code = apply(m, 0);
                int score;
                if (code != 0) {
                    score = tmpScore;
                } else if (i == 0) {
                    score = -negamax(depth - 1, -beta, -alpha, 1);
                } else {
                    score = -negamax(depth - 1, -alpha - 1, -alpha, 1);
                    if (!stop && score > alpha) score = -negamax(depth - 1, -beta, -alpha, 1);
                }
                undo(0, code);
                if (stop) break;
                if (score > iterScore) {
                    iterScore = score;
                    iterBest = m;
                    if (score > alpha) alpha = score;
                }
            }
            if (iterBest != -1) {
                best = iterBest;
                bestScore = iterScore;
                reachedDepth = stop ? Math.max(reachedDepth, depth - 1) : depth;
                // move the best move to the front for the next iteration
                for (int i = 0; i < n; i++) {
                    if (rootMoves[i] == best) {
                        System.arraycopy(rootMoves, 0, rootMoves, 1, i);
                        rootMoves[0] = best;
                        break;
                    }
                }
            }
            if (stop) break;
            if (Math.abs(bestScore) > MATE - 1000) break;
        }
        return best;
    }
}
