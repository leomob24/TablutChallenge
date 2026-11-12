package tablutai;

/**
 * Compact Tablut (Ashton rules) board, ported from the course server
 * (GameAshtonTablut.java). The rules are replicated exactly, including the
 * quirks of the reference implementation:
 *  - the empty throne is an obstacle: nobody can stop on it or cross it (king included);
 *  - camps ("citadels") can be entered/crossed only by pieces that start the move inside a camp;
 *  - a black soldier is captured against white/king/empty throne/camp (except the 4 edge-centre camps);
 *  - a white soldier is captured against black/throne (even if occupied)/any camp;
 *  - the king is captured against 2 sides (black or camp) away from the throne,
 *    3 sides next to the throne, 4 sides on the throne;
 *  - the king wins by reaching ANY edge square (camps are unreachable anyway).
 */
public final class Board {
    public static final int N = 9, SZ = 81, THRONE = 40;
    public static final int EMPTY = 0, WHITE = 1, BLACK = 2, KING = 3;
    public static final int WHITE_SIDE = 0, BLACK_SIDE = 1;
    /** winner values */
    public static final int NONE = 0, WHITE_WIN = 1, BLACK_WIN = 2;

    public static final boolean[] CAMP = new boolean[SZ];
    public static final boolean[] CAMP_NO_BARRIER = new boolean[SZ];
    public static final boolean[] EDGE = new boolean[SZ];
    public static final int[] DELTA = {-9, 9, -1, 1}; // up, down, left, right
    public static final int[] ROW = new int[SZ], COL = new int[SZ];
    /** number of steps available from a cell towards the edge in each direction */
    public static final int[][] RAYLEN = new int[4][SZ];
    static final long[][] ZP = new long[4][SZ];
    static final long ZSIDE;

    static {
        int[][] camps = {{3, 0}, {4, 0}, {5, 0}, {4, 1}, {0, 3}, {0, 4}, {0, 5}, {1, 4},
                {3, 8}, {4, 8}, {5, 8}, {4, 7}, {8, 3}, {8, 4}, {8, 5}, {7, 4}};
        for (int[] c : camps) CAMP[c[0] * 9 + c[1]] = true;
        int[][] nb = {{4, 8}, {0, 4}, {8, 4}, {4, 0}};
        for (int[] c : nb) CAMP_NO_BARRIER[c[0] * 9 + c[1]] = true;
        for (int i = 0; i < SZ; i++) {
            ROW[i] = i / 9;
            COL[i] = i % 9;
            EDGE[i] = ROW[i] == 0 || ROW[i] == 8 || COL[i] == 0 || COL[i] == 8;
            RAYLEN[0][i] = ROW[i];
            RAYLEN[1][i] = 8 - ROW[i];
            RAYLEN[2][i] = COL[i];
            RAYLEN[3][i] = 8 - COL[i];
        }
        java.util.Random rnd = new java.util.Random(0x7AB1E7L);
        for (int p = 0; p < 4; p++) for (int i = 0; i < SZ; i++) ZP[p][i] = rnd.nextLong();
        ZSIDE = rnd.nextLong();
    }

    public final int[] b = new int[SZ];
    public int side;
    public long hash;
    public int kingPos;
    public int whiteCount, blackCount; // soldiers only
    public int winner;

    // Store only move deltas and captured pieces so search can reuse one board
    // and restore it exactly without allocating a full copy at every node.
    private static final int MAXSP = 1024;
    private final int[] uMove = new int[MAXSP];
    private final int[] uPiece = new int[MAXSP];
    private final long[] uHash = new long[MAXSP];
    private final int[] uWinner = new int[MAXSP];
    private final int[] uKing = new int[MAXSP];
    private final int[] uCapN = new int[MAXSP];
    private final int[][] uCapCell = new int[MAXSP][3];
    private final int[][] uCapPiece = new int[MAXSP][3];
    private final int[] capBuf = new int[4];
    private int sp = 0;
    /** soldiers captured by the last make() (king capture excluded) */
    public int lastCaptures = 0;

    public Board() {
        setStart();
    }

    public Board(Board o) {
        copyFrom(o);
    }

    public void copyFrom(Board o) {
        System.arraycopy(o.b, 0, b, 0, SZ);
        side = o.side;
        hash = o.hash;
        kingPos = o.kingPos;
        whiteCount = o.whiteCount;
        blackCount = o.blackCount;
        winner = o.winner;
        sp = 0;
        lastCaptures = 0;
    }

    public void setStart() {
        java.util.Arrays.fill(b, EMPTY);
        int[][] w = {{2, 4}, {3, 4}, {5, 4}, {6, 4}, {4, 2}, {4, 3}, {4, 5}, {4, 6}};
        for (int[] c : w) b[c[0] * 9 + c[1]] = WHITE;
        b[THRONE] = KING;
        int[][] bl = {{0, 3}, {0, 4}, {0, 5}, {1, 4}, {8, 3}, {8, 4}, {8, 5}, {7, 4},
                {3, 0}, {4, 0}, {5, 0}, {4, 1}, {3, 8}, {4, 8}, {5, 8}, {4, 7}};
        for (int[] c : bl) b[c[0] * 9 + c[1]] = BLACK;
        side = WHITE_SIDE;
        finishSetup();
    }

    /** Sets up from arrays of piece codes (0..3) and side to move. */
    public void setFrom(int[] cells, int sideToMove) {
        System.arraycopy(cells, 0, b, 0, SZ);
        side = sideToMove;
        finishSetup();
    }

    private void finishSetup() {
        hash = side == BLACK_SIDE ? ZSIDE : 0L;
        whiteCount = 0;
        blackCount = 0;
        kingPos = -1;
        for (int i = 0; i < SZ; i++) {
            int p = b[i];
            if (p == EMPTY) continue;
            hash ^= ZP[p][i];
            if (p == WHITE) whiteCount++;
            else if (p == BLACK) blackCount++;
            else kingPos = i;
        }
        winner = NONE;
        sp = 0;
        lastCaptures = 0;
    }

    public int totalPieces() {
        return whiteCount + blackCount + (kingPos >= 0 ? 1 : 0);
    }

    // ------------------------------------------------------------------ moves

    /** Generates legal moves for the side to move; returns count. Move = from | to&lt;&lt;7. */
    public int genMoves(int[] out) {
        int n = 0;
        final boolean white = side == WHITE_SIDE;
        for (int from = 0; from < SZ; from++) {
            int p = b[from];
            if (p == EMPTY) continue;
            if (white ? (p == BLACK) : (p != BLACK)) continue;
            final boolean fromCamp = CAMP[from];
            for (int d = 0; d < 4; d++) {
                int len = RAYLEN[d][from];
                int q = from;
                final int delta = DELTA[d];
                for (int k = 1; k <= len; k++) {
                    q += delta;
                    if (b[q] != EMPTY || q == THRONE) break;
                    if (CAMP[q]) {
                        if (!fromCamp) break;
                        if (k > 5) continue; // camp-to-camp farther than 5 is rejected by the server
                    }
                    out[n++] = from | (q << 7);
                }
            }
        }
        return n;
    }

    /** True if the given move is legal in the current position. */
    public boolean isLegal(int m) {
        int[] tmp = new int[300];
        int n = genMoves(tmp);
        for (int i = 0; i < n; i++) if (tmp[i] == m) return true;
        return false;
    }

    private static final int KFLAG = 1 << 10;

    /**
     * Captures that would result from a piece of type {@code piece} standing on {@code to}
     * (board already updated). Writes captured cells to out (king capture flagged with KFLAG).
     */
    private int scanCaptures(int to, int piece, int[] out) {
        int n = 0;
        if (piece != BLACK) {
            for (int d = 0; d < 4; d++) {
                if (RAYLEN[d][to] < 2) continue;
                int nb = to + DELTA[d];
                if (b[nb] != BLACK) continue;
                int f = nb + DELTA[d];
                int fp = b[f];
                if (fp == WHITE || fp == KING || (f == THRONE && fp == EMPTY)
                        || (CAMP[f] && !CAMP_NO_BARRIER[f])) {
                    out[n++] = nb;
                }
            }
        } else {
            for (int d = 0; d < 4; d++) {
                if (RAYLEN[d][to] < 1) continue;
                int nb = to + DELTA[d];
                int np = b[nb];
                if (np == WHITE) {
                    if (RAYLEN[d][to] < 2) continue;
                    int f = nb + DELTA[d];
                    if (b[f] == BLACK || f == THRONE || CAMP[f]) out[n++] = nb;
                } else if (np == KING) {
                    if (kingCaptured(nb, d)) out[n++] = nb | KFLAG;
                }
            }
        }
        return n;
    }

    private boolean kingCaptured(int kp, int dirFromMover) {
        if (kp == THRONE) {
            for (int d = 0; d < 4; d++) if (b[kp + DELTA[d]] != BLACK) return false;
            return true;
        }
        if (kp == THRONE - 9 || kp == THRONE + 9 || kp == THRONE - 1 || kp == THRONE + 1) {
            for (int d = 0; d < 4; d++) {
                int q = kp + DELTA[d];
                if (q == THRONE) continue;
                if (b[q] != BLACK) return false;
            }
            return true;
        }
        if (RAYLEN[dirFromMover][kp] < 1) return false;
        int f = kp + DELTA[dirFromMover];
        return b[f] == BLACK || CAMP[f];
    }

    /** Number of soldiers (and king flag) captured if the move were played. Does not modify the board. */
    public int captureCount(int m) {
        int from = m & 127, to = m >>> 7;
        int p = b[from];
        b[from] = EMPTY;
        b[to] = p;
        int n = scanCaptures(to, p, capBuf);
        int score = 0;
        for (int i = 0; i < n; i++) score += (capBuf[i] & KFLAG) != 0 ? 100 : 1;
        b[to] = EMPTY;
        b[from] = p;
        return score;
    }

    public void make(int m) {
        final int from = m & 127, to = m >>> 7;
        final int p = b[from];
        uMove[sp] = m;
        uPiece[sp] = p;
        uHash[sp] = hash;
        uWinner[sp] = winner;
        uKing[sp] = kingPos;
        b[from] = EMPTY;
        b[to] = p;
        // Update the Zobrist key incrementally; the previous key is saved for
        // exact restoration by unmake(), including all captured-piece changes.
        hash ^= ZP[p][from] ^ ZP[p][to] ^ ZSIDE;
        if (p == KING) kingPos = to;
        int nc = scanCaptures(to, p, capBuf);
        int stored = 0;
        for (int i = 0; i < nc; i++) {
            int c = capBuf[i];
            if ((c & KFLAG) != 0) {
                winner = BLACK_WIN; // the king stays on the board as in the server
            } else {
                int cp = b[c];
                uCapCell[sp][stored] = c;
                uCapPiece[sp][stored] = cp;
                stored++;
                b[c] = EMPTY;
                hash ^= ZP[cp][c];
                if (cp == WHITE) whiteCount--;
                else blackCount--;
            }
        }
        uCapN[sp] = stored;
        lastCaptures = stored;
        if (p == KING && EDGE[to]) winner = WHITE_WIN;
        side ^= 1;
        sp++;
    }

    public void unmake() {
        sp--;
        final int m = uMove[sp];
        final int from = m & 127, to = m >>> 7;
        final int p = uPiece[sp];
        b[to] = EMPTY;
        b[from] = p;
        int n = uCapN[sp];
        for (int i = 0; i < n; i++) {
            int c = uCapCell[sp][i], cp = uCapPiece[sp][i];
            b[c] = cp;
            if (cp == WHITE) whiteCount++;
            else blackCount++;
        }
        hash = uHash[sp];
        winner = uWinner[sp];
        kingPos = uKing[sp];
        side ^= 1;
        lastCaptures = 0;
    }

    // ------------------------------------------------------------ king helpers

    /** Number of open lines from cell q to a free edge square (king moving, ignoring {@code ignore} cell). */
    public int raysFrom(int q, int ignore) {
        int cnt = 0;
        for (int d = 0; d < 4; d++) {
            int len = RAYLEN[d][q];
            if (len == 0) continue;
            int c = q;
            boolean open = true;
            for (int k = 0; k < len; k++) {
                c += DELTA[d];
                if (c == THRONE || CAMP[c] || (c != ignore && b[c] != EMPTY)) {
                    open = false;
                    break;
                }
            }
            if (open) cnt++;
        }
        return cnt;
    }

    public boolean kingHasOpenRay() {
        return kingPos >= 0 && raysFrom(kingPos, -1) > 0;
    }

    // ------------------------------------------------------------------ misc

    public static String cellName(int idx) {
        return "" + (char) ('a' + COL[idx]) + (ROW[idx] + 1);
    }

    public static int cellIndex(String s) {
        return (s.charAt(1) - '1') * 9 + (Character.toLowerCase(s.charAt(0)) - 'a');
    }

    public static String moveToString(int m) {
        return cellName(m & 127) + "->" + cellName(m >>> 7);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                int i = r * 9 + c;
                char ch = ".WBK".charAt(b[i]);
                if (b[i] == EMPTY && i == THRONE) ch = 'T';
                else if (b[i] == EMPTY && CAMP[i]) ch = 'c';
                sb.append(ch);
            }
            sb.append('\n');
        }
        sb.append(side == WHITE_SIDE ? "W" : "B");
        return sb.toString();
    }
}
