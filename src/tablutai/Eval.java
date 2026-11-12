package tablutai;

/**
 * Static evaluation: weighted sum of features (linear evaluation function, as in the course slides
 * Eval(s) = w1 f1(s) + ... + wn fn(s)). Scores are from the WHITE point of view.
 * The weights are an int[] so that they can be tuned offline (self-play + genetic algorithm).
 */
public final class Eval {
    public static final int I_WSOLD = 0, I_BSOLD = 1, I_RAY1 = 2, I_RAY2 = 3, I_THREAT = 4, I_FORK = 5,
            I_ADJ = 6, I_MOB = 7, I_KDIST = 8, I_BNEAR = 9, I_WNEAR = 10;
    public static final int NW = 11;

    public static final String[] NAMES = {"wSoldier", "bSoldier", "ray1", "ray2", "threat", "fork",
            "kingAdj", "kingMob", "kingDist", "blackNearKing", "whiteNearKing"};

    public static final int[] DEFAULTS = {100, 55, 300, 1200, 25, 150, 30, 3, 40, 6, 4};

    public final int[] w = new int[NW];

    public Eval() {
        System.arraycopy(DEFAULTS, 0, w, 0, NW);
    }

    public Eval(int[] weights) {
        System.arraycopy(weights, 0, w, 0, NW);
    }

    /** Evaluation from white's point of view. */
    public int evalWhite(Board bd) {
        final int[] b = bd.b;
        int s = w[I_WSOLD] * bd.whiteCount - w[I_BSOLD] * bd.blackCount;
        final int kp = bd.kingPos;
        if (kp < 0) return s;

        int r0 = bd.raysFrom(kp, -1);
        if (r0 >= 1) s += r0 >= 2 ? w[I_RAY2] : w[I_RAY1];

        // king one-move reach: mobility, cells with open rays (threats) and double rays (forks)
        int threat = 0, fork = 0, mob = 0;
        for (int d = 0; d < 4; d++) {
            int len = Board.RAYLEN[d][kp];
            int q = kp;
            for (int k = 0; k < len; k++) {
                q += Board.DELTA[d];
                if (b[q] != Board.EMPTY || q == Board.THRONE || Board.CAMP[q]) break;
                mob++;
                int rr = bd.raysFrom(q, kp);
                if (rr >= 2) fork++;
                else if (rr == 1) threat++;
            }
        }
        s += w[I_THREAT] * Math.min(threat, 6) + w[I_FORK] * Math.min(fork, 3) + w[I_MOB] * mob;

        // king neighbourhood: hostile neighbours (black soldiers / camps)
        int adj = 0;
        for (int d = 0; d < 4; d++) {
            if (Board.RAYLEN[d][kp] < 1) continue;
            int nb = kp + Board.DELTA[d];
            if (b[nb] == Board.BLACK || Board.CAMP[nb]) adj++;
        }
        s -= w[I_ADJ] * adj;

        // closeness of king to the border (Manhattan-like distance to nearest edge)
        int dr = Math.min(Board.ROW[kp], 8 - Board.ROW[kp]);
        int dc = Math.min(Board.COL[kp], 8 - Board.COL[kp]);
        int edgeDist = Math.min(dr, dc);
        s += w[I_KDIST] * (4 - edgeDist);

        // soldiers around the king: white pieces nearby protect it, black ones threaten it
        int bn = 0, wn = 0;
        int kr = Board.ROW[kp], kc = Board.COL[kp];
        for (int r = Math.max(0, kr - 2); r <= Math.min(8, kr + 2); r++) {
            for (int c = Math.max(0, kc - 2); c <= Math.min(8, kc + 2); c++) {
                int p = b[r * 9 + c];
                if (p == Board.BLACK) bn++;
                else if (p == Board.WHITE) wn++;
            }
        }
        s += w[I_WNEAR] * wn - w[I_BNEAR] * bn;
        return s;
    }

    /** Evaluation from the point of view of the side to move. */
    public int evalSide(Board bd) {
        int s = evalWhite(bd);
        return bd.side == Board.WHITE_SIDE ? s : -s;
    }
}
