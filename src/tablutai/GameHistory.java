package tablutai;

import java.util.Arrays;

/**
 * Tracks the states received from the server in order to reproduce its draw rule:
 * a new state equal to one already seen since the last capture is a draw
 * (the server's default is repeated_moves_allowed = 0). The initial state is not
 * part of the server's list, and the list is cleared when a capture occurs.
 */
public final class GameHistory {
    private long[] h = new long[256];
    private int n = 0;
    private int lastTotal = -1;
    private boolean first = true;

    public void onState(Board b) {
        int total = b.totalPieces();
        if (first) {
            first = false;
            lastTotal = total;
            return;
        }
        // The server's repetition window restarts only after a capture. Piece
        // count is sufficient here because a move cannot otherwise remove a piece.
        if (total < lastTotal) n = 0;
        lastTotal = total;
        if (n == h.length) h = Arrays.copyOf(h, n * 2);
        h[n++] = b.hash;
    }

    public int size() {
        return n;
    }

    public long[] array() {
        return h;
    }

    public void clear() {
        n = 0;
        first = true;
        lastTotal = -1;
    }
}
