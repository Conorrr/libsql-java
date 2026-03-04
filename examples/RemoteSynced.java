import uk.co.rstl.libsql.*;

/**
 * Demonstrates remote and synced replica database usage with Turso.
 *
 * Set environment variables before running:
 *   TURSO_URL=libsql://your-db.turso.io
 *   TURSO_AUTH_TOKEN=your-token
 */
public class RemoteSynced {
    public static void main(String[] args) {
        var url = System.getenv("TURSO_URL");
        var token = System.getenv("TURSO_AUTH_TOKEN");

        if (url == null || token == null) {
            System.err.println("Set TURSO_URL and TURSO_AUTH_TOKEN environment variables.");
            System.err.println("  export TURSO_URL=libsql://your-db.turso.io");
            System.err.println("  export TURSO_AUTH_TOKEN=your-token");
            System.exit(1);
        }

        // --- Remote-only connection ---
        System.out.println("=== Remote database ===");
        try (var db = Database.openRemote(url, token);
             var conn = db.connect()) {
            conn.batch("CREATE TABLE IF NOT EXISTS greetings(msg TEXT)");
            try (var stmt = conn.prepare("INSERT INTO greetings VALUES (?)")) {
                stmt.bind("Hello from Java FFM!");
                stmt.execute();
            }
            try (var stmt = conn.prepare("SELECT msg FROM greetings ORDER BY rowid DESC LIMIT 5");
                 var rows = stmt.query()) {
                for (var row : rows) {
                    System.out.println("  " + row.getString(0));
                    row.close();
                }
            }
        }

        // --- Synced replica (local file + remote) ---
        System.out.println("\n=== Synced replica ===");
        try (var db = Database.openSynced("/tmp/replica.db", url, token, 60)) {
            db.sync(); // pull latest from remote
            try (var conn = db.connect();
                 var stmt = conn.prepare("SELECT COUNT(*) FROM greetings");
                 var rows = stmt.query()) {
                System.out.println("  Greeting count: " + rows.next().getLong(0));
            }
        }
    }
}
