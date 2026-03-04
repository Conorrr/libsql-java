package dev.libsql;

import org.junit.jupiter.api.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LibSql FFM bindings using in-memory databases.
 * Requires the native library to be built and available.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LibSqlTest {

    @BeforeAll
    static void init() {
        LibSql.setup();
    }

    // --- Lifecycle ---

    @Test
    @Order(1)
    void setupInitConnectDeinit() {
        var arena = Arena.ofConfined();
        var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
        assertNotNull(db);
        var conn = LibSql.databaseConnect(db);
        assertNotNull(conn);
        LibSql.connectionDeinit(conn);
        LibSql.databaseDeinit(db);
        arena.close();
    }

    // --- Value round-trips ---

    @Test
    void integerRoundTrip() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn, "CREATE TABLE t(v INTEGER)");

            var stmt = LibSql.connectionPrepare(arena, conn, "INSERT INTO t VALUES (?)");
            LibSql.bindValue(stmt, LibSql.integer(42));
            LibSql.statementExecute(stmt);
            LibSql.statementDeinit(stmt);

            stmt = LibSql.connectionPrepare(arena, conn, "SELECT v FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertFalse(LibSql.rowEmpty(row));
            var val = LibSql.extractValue(LibSql.rowValue(row, 0));
            assertEquals(42L, val);

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    @Test
    void realRoundTrip() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn, "CREATE TABLE t(v REAL)");

            var stmt = LibSql.connectionPrepare(arena, conn, "INSERT INTO t VALUES (?)");
            LibSql.bindValue(stmt, LibSql.real(3.14));
            LibSql.statementExecute(stmt);
            LibSql.statementDeinit(stmt);

            stmt = LibSql.connectionPrepare(arena, conn, "SELECT v FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertFalse(LibSql.rowEmpty(row));
            var val = LibSql.extractValue(LibSql.rowValue(row, 0));
            assertEquals(3.14, (double) val, 0.001);

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    @Test
    void textRoundTrip() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn, "CREATE TABLE t(v TEXT)");

            var stmt = LibSql.connectionPrepare(arena, conn, "INSERT INTO t VALUES (?)");
            LibSql.bindValue(stmt, LibSql.text(arena, "hello world"));
            LibSql.statementExecute(stmt);
            LibSql.statementDeinit(stmt);

            stmt = LibSql.connectionPrepare(arena, conn, "SELECT v FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertFalse(LibSql.rowEmpty(row));
            var val = LibSql.extractValue(LibSql.rowValue(row, 0));
            assertEquals("hello world", val);

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    @Test
    void blobRoundTrip() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn, "CREATE TABLE t(v BLOB)");

            byte[] data = {1, 2, 3, 4, 5};
            var stmt = LibSql.connectionPrepare(arena, conn, "INSERT INTO t VALUES (?)");
            LibSql.bindValue(stmt, LibSql.blob(arena, data));
            LibSql.statementExecute(stmt);
            LibSql.statementDeinit(stmt);

            stmt = LibSql.connectionPrepare(arena, conn, "SELECT v FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertFalse(LibSql.rowEmpty(row));
            var val = LibSql.extractValue(LibSql.rowValue(row, 0));
            assertArrayEquals(data, (byte[]) val);

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    @Test
    void nullRoundTrip() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn, "CREATE TABLE t(v TEXT)");

            var stmt = LibSql.connectionPrepare(arena, conn, "INSERT INTO t VALUES (?)");
            LibSql.bindValue(stmt, LibSql.nullValue());
            LibSql.statementExecute(stmt);
            LibSql.statementDeinit(stmt);

            stmt = LibSql.connectionPrepare(arena, conn, "SELECT v FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertFalse(LibSql.rowEmpty(row));
            var val = LibSql.extractValue(LibSql.rowValue(row, 0));
            assertNull(val);

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    // --- Prepared statements ---

    @Test
    void positionalBinding() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn, "CREATE TABLE t(a INTEGER, b TEXT)");

            var stmt = LibSql.connectionPrepare(arena, conn, "INSERT INTO t VALUES (?, ?)");
            LibSql.bindValue(stmt, LibSql.integer(1));
            LibSql.bindValue(stmt, LibSql.text(arena, "one"));
            LibSql.statementExecute(stmt);
            LibSql.statementDeinit(stmt);

            stmt = LibSql.connectionPrepare(arena, conn, "SELECT a, b FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertEquals(1L, LibSql.extractValue(LibSql.rowValue(row, 0)));
            assertEquals("one", LibSql.extractValue(LibSql.rowValue(row, 1)));

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    @Test
    void namedBinding() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn, "CREATE TABLE t(a INTEGER, b TEXT)");

            var stmt = LibSql.connectionPrepare(arena, conn, "INSERT INTO t VALUES (:id, :name)");
            LibSql.bindNamed(arena, stmt, ":id", LibSql.integer(99));
            LibSql.bindNamed(arena, stmt, ":name", LibSql.text(arena, "test"));
            LibSql.statementExecute(stmt);
            LibSql.statementDeinit(stmt);

            stmt = LibSql.connectionPrepare(arena, conn, "SELECT a, b FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertEquals(99L, LibSql.extractValue(LibSql.rowValue(row, 0)));
            assertEquals("test", LibSql.extractValue(LibSql.rowValue(row, 1)));

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    @Test
    void statementResetAndReExecute() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn, "CREATE TABLE t(v INTEGER)");

            var stmt = LibSql.connectionPrepare(arena, conn, "INSERT INTO t VALUES (?)");

            LibSql.bindValue(stmt, LibSql.integer(1));
            LibSql.statementExecute(stmt);
            LibSql.statementReset(stmt);

            LibSql.bindValue(stmt, LibSql.integer(2));
            LibSql.statementExecute(stmt);
            LibSql.statementDeinit(stmt);

            stmt = LibSql.connectionPrepare(arena, conn, "SELECT COUNT(*) FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertEquals(2L, LibSql.extractValue(LibSql.rowValue(row, 0)));

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    // --- Batch operations ---

    @Test
    void batchOperations() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);

            LibSql.connectionBatch(arena, conn,
                    "CREATE TABLE t(v INTEGER); INSERT INTO t VALUES (1); INSERT INTO t VALUES (2); INSERT INTO t VALUES (3)");

            var stmt = LibSql.connectionPrepare(arena, conn, "SELECT COUNT(*) FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertEquals(3L, LibSql.extractValue(LibSql.rowValue(row, 0)));

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    // --- Transactions ---

    @Test
    void transactionCommit() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn, "CREATE TABLE t(v INTEGER)");

            var tx = LibSql.connectionTransaction(conn);
            LibSql.transactionBatch(arena, tx, "INSERT INTO t VALUES (1)");
            LibSql.transactionCommit(tx);

            var stmt = LibSql.connectionPrepare(arena, conn, "SELECT COUNT(*) FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertEquals(1L, LibSql.extractValue(LibSql.rowValue(row, 0)));

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    @Test
    void transactionRollback() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn, "CREATE TABLE t(v INTEGER)");

            var tx = LibSql.connectionTransaction(conn);
            LibSql.transactionBatch(arena, tx, "INSERT INTO t VALUES (1)");
            LibSql.transactionRollback(tx);

            var stmt = LibSql.connectionPrepare(arena, conn, "SELECT COUNT(*) FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertEquals(0L, LibSql.extractValue(LibSql.rowValue(row, 0)));

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    // --- Row iteration ---

    @Test
    void rowIterationColumnCountAndNames() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn, "CREATE TABLE t(id INTEGER, name TEXT, score REAL)");
            LibSql.connectionBatch(arena, conn, "INSERT INTO t VALUES (1, 'alice', 9.5)");

            var stmt = LibSql.connectionPrepare(arena, conn, "SELECT id, name, score FROM t");
            var rows = LibSql.statementQuery(stmt);

            assertEquals(3, LibSql.rowsColumnCount(rows));
            assertEquals("id", LibSql.rowsColumnName(rows, 0));
            assertEquals("name", LibSql.rowsColumnName(rows, 1));
            assertEquals("score", LibSql.rowsColumnName(rows, 2));

            var row = LibSql.rowsNext(rows);
            assertFalse(LibSql.rowEmpty(row));
            assertEquals(3, LibSql.rowLength(row));

            LibSql.rowDeinit(row);

            // Next row should be empty (end of results)
            var row2 = LibSql.rowsNext(rows);
            assertTrue(LibSql.rowEmpty(row2));

            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    // --- Error handling ---

    @Test
    void invalidSqlThrowsLibSqlException() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);

            assertThrows(LibSqlException.class, () ->
                    LibSql.connectionBatch(arena, conn, "NOT VALID SQL AT ALL"));

            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    @Test
    void executeReturnsRowsChanged() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn,
                    "CREATE TABLE t(v INTEGER); INSERT INTO t VALUES (1); INSERT INTO t VALUES (2); INSERT INTO t VALUES (3)");

            var stmt = LibSql.connectionPrepare(arena, conn, "DELETE FROM t WHERE v > 1");
            long changed = LibSql.statementExecute(stmt);
            assertEquals(2, changed);

            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }

    // --- Multi-connection ---

    @Test
    void multipleConnectionsToSameDb() throws Exception {
        var tmpDb = Files.createTempFile("libsql-test-", ".db");
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, tmpDb.toString(), null, null, 0);
            var conn1 = LibSql.databaseConnect(db);
            var conn2 = LibSql.databaseConnect(db);

            LibSql.connectionBatch(arena, conn1, "CREATE TABLE t(v INTEGER)");
            LibSql.connectionBatch(arena, conn1, "INSERT INTO t VALUES (42)");

            var stmt = LibSql.connectionPrepare(arena, conn2, "SELECT v FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertFalse(LibSql.rowEmpty(row));
            assertEquals(42L, LibSql.extractValue(LibSql.rowValue(row, 0)));

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn2);
            LibSql.connectionDeinit(conn1);
            LibSql.databaseDeinit(db);
        } finally {
            Files.deleteIfExists(tmpDb);
        }
    }

    // --- Transaction prepare ---

    @Test
    void transactionPrepareAndExecute() {
        try (var arena = Arena.ofConfined()) {
            var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
            var conn = LibSql.databaseConnect(db);
            LibSql.connectionBatch(arena, conn, "CREATE TABLE t(v INTEGER)");

            var tx = LibSql.connectionTransaction(conn);
            var stmt = LibSql.transactionPrepare(arena, tx, "INSERT INTO t VALUES (?)");
            LibSql.bindValue(stmt, LibSql.integer(77));
            LibSql.statementExecute(stmt);
            LibSql.statementDeinit(stmt);
            LibSql.transactionCommit(tx);

            stmt = LibSql.connectionPrepare(arena, conn, "SELECT v FROM t");
            var rows = LibSql.statementQuery(stmt);
            var row = LibSql.rowsNext(rows);
            assertEquals(77L, LibSql.extractValue(LibSql.rowValue(row, 0)));

            LibSql.rowDeinit(row);
            LibSql.rowsDeinit(rows);
            LibSql.statementDeinit(stmt);
            LibSql.connectionDeinit(conn);
            LibSql.databaseDeinit(db);
        }
    }
}
