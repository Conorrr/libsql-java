import uk.co.rstl.libsql.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Demonstrates using a local file-backed database.
 */
public class LocalFileDatabase {
    public static void main(String[] args) throws Exception {
        var dbPath = Files.createTempFile("libsql-example-", ".db");
        try {
            // Write data
            try (var db = Database.builder(dbPath.toString())
                    .journalMode("WAL")
                    .foreignKeys(true)
                    .busyTimeout(5000)
                    .build();
                 var conn = db.connect()) {
                conn.batch("CREATE TABLE notes(id INTEGER PRIMARY KEY, content TEXT, created_at TEXT)");

                try (var stmt = conn.prepare("INSERT INTO notes VALUES (?, ?, datetime('now'))")) {
                    stmt.bind(1L).bind("First note");
                    stmt.execute();
                    stmt.reset();
                    stmt.bind(2L).bind("Second note");
                    stmt.execute();
                }
                System.out.println("Wrote 2 notes to " + dbPath);
            }

            // Re-open and read back
            try (var db = Database.builder(dbPath.toString())
                    .journalMode("WAL")
                    .busyTimeout(5000)
                    .build();
                 var conn = db.connect()) {
                try (var stmt = conn.prepare("SELECT id, content, created_at FROM notes ORDER BY id");
                     var rows = stmt.query()) {
                    System.out.println("\nNotes from disk:");
                    for (var row : rows) {
                        System.out.printf("  [%d] %s (created %s)%n",
                                row.getLong(0), row.getString(1), row.getString(2));
                        row.close();
                    }
                }
            }
        } finally {
            Files.deleteIfExists(dbPath);
            Files.deleteIfExists(Path.of(dbPath + "-wal"));
            Files.deleteIfExists(Path.of(dbPath + "-shm"));
        }
    }
}
