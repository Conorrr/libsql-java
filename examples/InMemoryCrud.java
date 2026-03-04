import dev.libsql.*;

/**
 * Demonstrates basic CRUD operations with an in-memory database.
 *
 * Run: java --enable-native-access=ALL-UNNAMED -cp build/libs/libsql-java-0.1.0-SNAPSHOT.jar examples/InMemoryCrud.java
 */
public class InMemoryCrud {
    public static void main(String[] args) {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {

            // Create
            conn.batch("CREATE TABLE users(id INTEGER PRIMARY KEY, name TEXT, email TEXT)");

            try (var stmt = conn.prepare("INSERT INTO users VALUES (?, ?, ?)")) {
                stmt.bind(1L).bind("Alice").bind("alice@example.com");
                stmt.execute();
                stmt.reset();
                stmt.bind(2L).bind("Bob").bind("bob@example.com");
                stmt.execute();
                stmt.reset();
                stmt.bind(3L).bind("Charlie").bind("charlie@example.com");
                stmt.execute();
            }

            // Read
            System.out.println("All users:");
            try (var stmt = conn.prepare("SELECT id, name, email FROM users ORDER BY id");
                 var rows = stmt.query()) {
                for (var row : rows) {
                    System.out.printf("  %d: %s <%s>%n", row.getLong(0), row.getString(1), row.getString(2));
                    row.close();
                }
            }

            // Update
            try (var stmt = conn.prepare("UPDATE users SET email = ? WHERE id = ?")) {
                stmt.bind("alice-new@example.com").bind(1L);
                long changed = stmt.execute();
                System.out.println("\nUpdated " + changed + " row(s)");
            }

            // Delete
            try (var stmt = conn.prepare("DELETE FROM users WHERE id = ?")) {
                stmt.bind(3L);
                long changed = stmt.execute();
                System.out.println("Deleted " + changed + " row(s)");
            }

            // Verify
            System.out.println("\nRemaining users:");
            try (var stmt = conn.prepare("SELECT id, name, email FROM users ORDER BY id");
                 var rows = stmt.query()) {
                for (var row : rows) {
                    System.out.printf("  %d: %s <%s>%n", row.getLong(0), row.getString(1), row.getString(2));
                    row.close();
                }
            }
        }
    }
}
