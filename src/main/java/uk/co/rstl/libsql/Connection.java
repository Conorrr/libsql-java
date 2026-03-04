package uk.co.rstl.libsql;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * AutoCloseable wrapper around a libsql connection handle.
 * Obtained via {@link Database#connect()}.
 */
public final class Connection implements AutoCloseable {

    private final Database db;
    private final MemorySegment handle;
    private boolean closed;

    Connection(Database db, MemorySegment handle) {
        this.db = db;
        this.handle = handle;
    }

    /** Execute one or more SQL statements as a batch (no results returned). */
    public void batch(String sql) {
        checkOpen();
        try (var arena = Arena.ofConfined()) {
            LibSql.connectionBatch(arena, handle, sql);
        }
    }

    /** Prepare a statement for execution. Caller must close the returned Statement. */
    public Statement prepare(String sql) {
        checkOpen();
        var arena = Arena.ofConfined();
        var stmt = LibSql.connectionPrepare(arena, handle, sql);
        return new Statement(arena, stmt);
    }

    /** Begin a transaction. Caller must close the returned Transaction. */
    public Transaction transaction() {
        checkOpen();
        return new Transaction(LibSql.connectionTransaction(handle));
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("Connection is closed");
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            LibSql.connectionDeinit(handle);
        }
    }
}
