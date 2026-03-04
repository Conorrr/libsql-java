package uk.co.rstl.libsql;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * AutoCloseable wrapper around a libsql transaction handle.
 * <p>
 * If not explicitly committed before close, the transaction is rolled back.
 */
public final class Transaction implements AutoCloseable {

    private final MemorySegment handle;
    private boolean finished;

    Transaction(MemorySegment handle) {
        this.handle = handle;
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
        var stmt = LibSql.transactionPrepare(arena, handle, sql);
        return new Statement(arena, stmt);
    }

    /** Commit the transaction. */
    public void commit() {
        checkActive();
        finished = true;
        LibSql.transactionCommit(handle);
    }

    /** Explicitly roll back the transaction. */
    public void rollback() {
        checkActive();
        finished = true;
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
            LibSql.transactionRollback(handle);
        }
    }
}
