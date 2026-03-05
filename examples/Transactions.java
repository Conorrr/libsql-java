import uk.co.rstl.libsql.*;

/**
 * Demonstrates transaction commit, rollback, and auto-rollback-on-close behavior.
 */
public class Transactions {
    public static void main(String[] args) {
        try (var db = Database.builder(":memory:")
                .foreignKeys(true)
                .busyTimeout(5000)
                .build();
             var conn = db.connect()) {

            conn.batch("CREATE TABLE accounts(name TEXT, balance INTEGER)");
            conn.batch("INSERT INTO accounts VALUES ('Alice', 1000), ('Bob', 500)");

            // Successful transfer
            try (var tx = conn.transaction()) {
                try (var stmt = tx.prepare("UPDATE accounts SET balance = balance - ? WHERE name = ?")) {
                    stmt.bind(200L).bind("Alice");
                    stmt.execute();
                }
                try (var stmt = tx.prepare("UPDATE accounts SET balance = balance + ? WHERE name = ?")) {
                    stmt.bind(200L).bind("Bob");
                    stmt.execute();
                }
                tx.commit();
                System.out.println("Transfer committed");
            }

            printBalances(conn);

            // Failed transfer — auto-rollback on close (no commit called)
            try (var tx = conn.transaction()) {
                try (var stmt = tx.prepare("UPDATE accounts SET balance = balance - ? WHERE name = ?")) {
                    stmt.bind(9999L).bind("Alice");
                    stmt.execute();
                }
                // Oops, realize the amount is wrong — just let tx close without committing
                System.out.println("\nTransaction not committed — auto-rolled back");
            }

            printBalances(conn);
        }
    }

    private static void printBalances(Connection conn) {
        try (var stmt = conn.prepare("SELECT name, balance FROM accounts ORDER BY name");
             var rows = stmt.query()) {
            System.out.println("Balances:");
            for (var row : rows) {
                System.out.printf("  %s: %d%n", row.getString(0), row.getLong(1));
                row.close();
            }
        }
    }
}
