package uk.co.rstl.libsql;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;

/**
 * AutoCloseable wrapper around a libsql transaction handle.
 * <p>
 * If not explicitly committed before close, the transaction is rolled back.
 */
public final class Transaction implements AutoCloseable {

    private final MemorySegment handle;
    private final CleanAction cleanAction;
    private final Cleaner.Cleanable cleanable;
    private boolean finished;

    Transaction(MemorySegment handle) {
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
            ResourceCleaner.warn("Transaction");
            LibSql.transactionRollback(handle);
        }
    }

    /** Execute one or more SQL statements as a batch within this transaction. */
    public void batch(String sql) {
        checkActive();
        try (var arena = Arena.ofConfined()) {
            LibSql.transactionBatch(arena, handle, sql);
        }
    }

    /** Prepare a statement within this transaction. Caller must close the returned Statement. */
    public Statement prepare(String sql) {
        checkActive();
        var arena = Arena.ofConfined();
        try {
            var stmt = LibSql.transactionPrepare(arena, handle, sql);
            return new Statement(arena, stmt);
        } catch (Throwable t) {
            arena.close();
            throw t;
        }
    }

    /** Commit the transaction. */
    public void commit() {
        checkActive();
        finished = true;
        cleanAction.disarmed = true;
        if (cleanable != null) cleanable.clean();
        LibSql.transactionCommit(handle);
    }

    /** Explicitly roll back the transaction. */
    public void rollback() {
        checkActive();
        finished = true;
        cleanAction.disarmed = true;
        if (cleanable != null) cleanable.clean();
        LibSql.transactionRollback(handle);
    }

    private void checkActive() {
        if (finished) throw new IllegalStateException("Transaction already finished");
    }

    /** Rolls back if not already committed or rolled back. */
    @Override
    public void close() {
        if (!finished) {
            finished = true;
            cleanAction.disarmed = true;
            if (cleanable != null) cleanable.clean();
            LibSql.transactionRollback(handle);
        }
    }
}
