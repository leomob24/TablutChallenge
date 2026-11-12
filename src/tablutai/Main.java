package tablutai;

/**
 * Entry point. Usage: java -jar TablutPlayer.jar ROLE [TIMEOUT_SECONDS] [SERVER_IP] [PLAYER_NAME]
 * ROLE is WHITE or BLACK (case-insensitive). Defaults: timeout 60, ip localhost.
 */
public final class Main {
    public static final String DEFAULT_NAME = "VikingAB";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ROLE(WHITE|BLACK) [timeout_seconds] [server_ip] [name]");
            System.exit(2);
        }
        String role = args[0].trim().toUpperCase();
        boolean white;
        if (role.equals("WHITE")) white = true;
        else if (role.equals("BLACK")) white = false;
        else {
            System.err.println("Role must be WHITE or BLACK");
            System.exit(2);
            return;
        }
        int timeout = 60;
        if (args.length > 1) {
            try {
                timeout = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid timeout, using 60");
            }
        }
        String ip = args.length > 2 ? args[2].trim() : "localhost";
        String name = args.length > 3 ? args[3].trim() : DEFAULT_NAME;
        new Client(white, timeout, ip, name, false).run();
    }
}
