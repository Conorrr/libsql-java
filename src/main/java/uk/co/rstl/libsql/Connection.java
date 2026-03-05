package uk.co.rstl.libsql;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;

/**
 * AutoCloseable wrapper around a libsql connection handle.
 * Obtained via {@link Database#connect()}.
 */
public final class Connection implements AutoCloseable {

    private final Database db;
    private final MemorySegment handle;
    private final CleanAction cleanAction;
    private final Cleaner.Cleanable cleanable;
    private boolean closed;

    Connection(Database db, MemorySegment handle) {
        this.db = db;
        this.handle = handle;
        this.cleanAction = new CleanAction(handle);
        this.cleanable = LibSql.CLEANER != null
                ? LibSql.CLEANER.register(this, cleanAction)
                : null;
    }

    private static class CleanAction implements Runnable {
        private final MemorySegment handle;
        volatile boolean disarmed;
        CleanAction(MemorySegment handle) { this.handle = handle; }
        @Override public void run() {
            if (disarmed) return;
            ResourceCleaner.warn("Connection");
            LibSql.connectionDeinit(handle);
        }
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
            cleanAction.disarmed = true;
            if (cleanable != null) cleanable.clean();
            LibSql.connectionDeinit(handle);
        }
    }
}
