package uk.co.rstl.libsql;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the high-level AutoCloseable API wrappers.
 */
class HighLevelApiTest {

    // --- Lifecycle ---

    @Test
    void openConnectClose() {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            assertNotNull(conn);
        }
    }

    @Test
    void closedDatabaseThrows() {
        var db = Database.open(":memory:");
        db.close();
        assertThrows(IllegalStateException.class, db::connect);
    }

    @Test
    void closedConnectionThrows() {
        try (var db = Database.open(":memory:")) {
            var conn = db.connect();
            conn.close();
            assertThrows(IllegalStateException.class, () -> conn.batch("SELECT 1"));
        }
    }

    @Test
    void doubleCloseIsSafe() {
        var db = Database.open(":memory:");
        db.close();
        assertDoesNotThrow(db::close);
    }

    // --- CRUD ---

    @Test
    void insertAndQuery() {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE t(id INTEGER, name TEXT)");

            try (var stmt = conn.prepare("INSERT INTO t VALUES (?, ?)")) {
                stmt.bind(1L).bind("Alice");
                stmt.execute();
            }

            try (var stmt = conn.prepare("SELECT id, name FROM t");
                 var rows = stmt.query()) {
                var row = rows.next();
                assertNotNull(row);
                assertEquals(1L, row.getLong(0));
                assertEquals("Alice", row.getString(1));
                row.close();
                assertNull(rows.next());
            }
        }
    }

    // --- Typed getters ---

    @Test
    void typedGetters() {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE t(i INTEGER, r REAL, t TEXT, b BLOB, n TEXT)");

            try (var stmt = conn.prepare("INSERT INTO t VALUES (?, ?, ?, ?, ?)")) {
                stmt.bind(42L).bind(3.14).bind("hello").bind(new byte[]{1, 2, 3}).bindNull();
                stmt.execute();
            }

            try (var stmt = conn.prepare("SELECT i, r, t, b, n FROM t");
                 var rows = stmt.query()) {
                var row = rows.next();
                assertNotNull(row);
                assertEquals(42L, row.getLong(0));
                assertEquals(3.14, row.getDouble(1), 0.001);
                assertEquals("hello", row.getString(2));
                assertArrayEquals(new byte[]{1, 2, 3}, row.getBlob(3));
                assertTrue(row.isNull(4));
                assertNull(row.getString(4));
                row.close();
            }
        }
    }

    // --- Named binding ---

    @Test
    void namedBinding() {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE t(a INTEGER, b TEXT)");

            try (var stmt = conn.prepare("INSERT INTO t VALUES (:id, :name)")) {
                stmt.bind(":id", 99L).bind(":name", "test");
                stmt.execute();
            }

            try (var stmt = conn.prepare("SELECT a, b FROM t");
                 var rows = stmt.query()) {
                var row = rows.next();
                assertNotNull(row);
                assertEquals(99L, row.getLong(0));
                assertEquals("test", row.getString(1));
                row.close();
            }
        }
    }

    // --- Iteration ---

    @Test
    void forEachIteration() {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE t(v INTEGER); INSERT INTO t VALUES (1); INSERT INTO t VALUES (2); INSERT INTO t VALUES (3)");

            int count = 0;
            try (var stmt = conn.prepare("SELECT v FROM t ORDER BY v");
                 var rows = stmt.query()) {
                for (var row : rows) {
                    count++;
                    assertEquals((long) count, row.getLong(0));
                    row.close();
                }
            }
            assertEquals(3, count);
        }
    }

    @Test
    void columnMetadata() {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE t(id INTEGER, name TEXT, score REAL); INSERT INTO t VALUES (1, 'a', 1.0)");

            try (var stmt = conn.prepare("SELECT id, name, score FROM t");
                 var rows = stmt.query()) {
                assertEquals(3, rows.columnCount());
                assertEquals("id", rows.columnName(0));
                assertEquals("name", rows.columnName(1));
                assertEquals("score", rows.columnName(2));
            }
        }
    }

    // --- Transactions ---

    @Test
    void transactionCommit() {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE t(v INTEGER)");

            try (var tx = conn.transaction()) {
                tx.batch("INSERT INTO t VALUES (1)");
                tx.commit();
            }

            try (var stmt = conn.prepare("SELECT COUNT(*) FROM t");
                 var rows = stmt.query()) {
                assertEquals(1L, rows.next().getLong(0));
            }
        }
    }

    @Test
    void transactionRollbackOnClose() {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE t(v INTEGER)");

            // Transaction not committed — should auto-rollback on close
            try (var tx = conn.transaction()) {
                tx.batch("INSERT INTO t VALUES (1)");
            }

            try (var stmt = conn.prepare("SELECT COUNT(*) FROM t");
                 var rows = stmt.query()) {
                assertEquals(0L, rows.next().getLong(0));
            }
        }
    }

    @Test
    void transactionExplicitRollback() {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE t(v INTEGER)");

            try (var tx = conn.transaction()) {
                tx.batch("INSERT INTO t VALUES (1)");
                tx.rollback();
            }

            try (var stmt = conn.prepare("SELECT COUNT(*) FROM t");
                 var rows = stmt.query()) {
                assertEquals(0L, rows.next().getLong(0));
            }
        }
    }

    @Test
    void transactionPrepare() {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE t(v INTEGER)");

            try (var tx = conn.transaction()) {
                try (var stmt = tx.prepare("INSERT INTO t VALUES (?)")) {
                    stmt.bind(77L);
                    stmt.execute();
                }
                tx.commit();
            }

            try (var stmt = conn.prepare("SELECT v FROM t");
                 var rows = stmt.query()) {
                assertEquals(77L, rows.next().getLong(0));
            }
        }
    }

    // --- Statement reset ---

    @Test
    void statementResetAndReuse() {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE t(v INTEGER)");

            try (var stmt = conn.prepare("INSERT INTO t VALUES (?)")) {
                stmt.bind(1L);
                stmt.execute();
                stmt.reset();
                stmt.bind(2L);
                stmt.execute();
            }

            try (var stmt = conn.prepare("SELECT COUNT(*) FROM t");
                 var rows = stmt.query()) {
                assertEquals(2L, rows.next().getLong(0));
            }
        }
    }

    // --- Execute returns rows changed ---

    @Test
    void executeReturnsRowsChanged() {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE t(v INTEGER); INSERT INTO t VALUES (1); INSERT INTO t VALUES (2); INSERT INTO t VALUES (3)");

            try (var stmt = conn.prepare("DELETE FROM t WHERE v > 1")) {
                assertEquals(2L, stmt.execute());
            }
        }
    }
}
