package tablutai;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;

/**
 * Socket client for the competition server. Protocol: 4-byte big-endian length followed by a UTF-8 JSON string.
 * Cycle: declare name; then repeatedly read a state; if it is our turn compute and send a move.
 */
public final class Client {
    static final class StateDto {
        String[][] board;
        String turn;
    }

    static final class ActionDto {
        String from;
        String to;
        String turn;
    }

    private final boolean white;
    private final int timeoutSec;
    private final String ip;
    private final String name;
    private final Gson gson = new Gson();
    private final Eval eval = new Eval();
    private final Search search;
    private final GameHistory history = new GameHistory();
    private final boolean verbose;
    private int moveNo = 0;

    public Client(boolean white, int timeoutSec, String ip, String name, boolean verbose) {
        this.white = white;
        this.timeoutSec = timeoutSec;
        this.ip = ip;
        this.name = name;
        this.verbose = verbose;
        this.search = new Search(eval, 22);
    }

    static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        // DataOutputStream writes the required four-byte, big-endian length.
        out.writeInt(bytes.length);
        out.write(bytes, 0, bytes.length);
        out.flush();
    }

    static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes, 0, len);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Socket connect() throws IOException, InterruptedException {
        int port = white ? 5800 : 5801;
        IOException last = null;
        // the server may not be listening yet: retry for a while
        for (int attempt = 0; attempt < 240; attempt++) {
            try {
                return new Socket(ip, port);
            } catch (IOException e) {
                last = e;
                Thread.sleep(500);
            }
        }
        throw last;
    }

    static int pieceCode(String s) {
        switch (s) {
            case "WHITE": return Board.WHITE;
            case "BLACK": return Board.BLACK;
            case "KING": return Board.KING;
            default: return Board.EMPTY; // EMPTY, THRONE
        }
    }

    static Board toBoard(StateDto st) {
        int[] cells = new int[81];
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) cells[r * 9 + c] = pieceCode(st.board[r][c]);
        Board b = new Board();
        b.setFrom(cells, "WHITE".equals(st.turn) ? Board.WHITE_SIDE : Board.BLACK_SIDE);
        return b;
    }

    /** Time budget (ns) for one move given the server timeout (seconds). */
    static long budgetNs(int timeoutSec) {
        // Reserve time for network/server overhead and keep a usable floor for
        // short clocks; search itself checks the resulting monotonic deadline.
        double ms = timeoutSec * 1000.0 * 0.85 - 500.0;
        if (ms < 200) ms = 200;
        return (long) (ms * 1_000_000L);
    }

    public void run() throws Exception {
        Socket sock = connect();
        sock.setTcpNoDelay(true);
        DataOutputStream out = new DataOutputStream(sock.getOutputStream());
        DataInputStream in = new DataInputStream(sock.getInputStream());
        writeString(out, gson.toJson(name));
        final String me = white ? "WHITE" : "BLACK";
        while (true) {
            String json = readString(in);
            StateDto st = gson.fromJson(json, StateDto.class);
            String turn = st.turn;
            if ("WHITEWIN".equals(turn) || "BLACKWIN".equals(turn) || "DRAW".equals(turn)) {
                String result = "DRAW".equals(turn) ? "DRAW"
                        : (("WHITEWIN".equals(turn) == white) ? "WIN" : "LOSS");
                System.out.println("Game over: " + turn + " -> " + result);
                break;
            }
            Board b = toBoard(st);
            history.onState(b);
            if (!me.equals(turn)) continue;

            long t0 = System.nanoTime();
            int m;
            try {
                m = search.think(b, history.array(), history.size(), budgetNs(timeoutSec), 64);
            } catch (Throwable t) {
                // never lose by timeout because of a bug: play any legal move
                System.out.println("Search failed: " + t);
                int[] tmp = new int[320];
                int n = b.genMoves(tmp);
                m = n > 0 ? tmp[0] : -1;
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            if (m < 0) {
                System.out.println("No legal moves!");
                m = 0;
            }
            ActionDto a = new ActionDto();
            a.from = Board.cellName(m & 127);
            a.to = Board.cellName(m >>> 7);
            a.turn = me;
            writeString(out, gson.toJson(a));
            moveNo++;
            if (verbose || moveNo % 1 == 0) {
                System.out.println("#" + moveNo + " " + a.from + a.to + " d=" + search.reachedDepth + " s="
                        + search.bestScore + " n=" + search.nodes + " t=" + ms + "ms");
            }
        }
        sock.close();
    }
}
