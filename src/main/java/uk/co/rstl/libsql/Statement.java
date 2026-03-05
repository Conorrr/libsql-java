package uk.co.rstl.libsql;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;

/**
 * AutoCloseable wrapper around a libsql prepared statement.
 * Obtained via {@link Connection#prepare(String)} or {@link Transaction#prepare(String)}.
 */
public final class Statement implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment handle;
    private final CleanAction cleanAction;
    private final Cleaner.Cleanable cleanable;
    private boolean closed;

    Statement(Arena arena, MemorySegment handle) {
        this.arena = arena;
        this.handle = handle;
        this.cleanAction = new CleanAction(handle, arena);
        this.cleanable = LibSql.CLEANER != null
                ? LibSql.CLEANER.register(this, cleanAction)
                : null;
    }

    private static class CleanAction implements Runnable {
        private final MemorySegment handle;
        private final Arena arena;
        volatile boolean disarmed;
        CleanAction(MemorySegment handle, Arena arena) { this.handle = handle; this.arena = arena; }
        @Override public void run() {
            if (disarmed) return;
            ResourceCleaner.warn("Statement");
            LibSql.statementDeinit(handle);
            arena.close();
        }
    }

    // --- Positional binding ---

    public Statement bind(long value) {
        checkOpen();
        LibSql.bindValue(handle, LibSql.integer(value));
        return this;
    }

    public Statement bind(double value) {
        checkOpen();
        LibSql.bindValue(handle, LibSql.real(value));
        return this;
    }

    public Statement bind(String value) {
        checkOpen();
        if (value == null) {
            LibSql.bindValue(handle, LibSql.nullValue());
        } else {
            LibSql.bindValue(handle, LibSql.text(arena, value));
        }
        return this;
    }

    public Statement bind(byte[] value) {
        checkOpen();
        if (value == null) {
            LibSql.bindValue(handle, LibSql.nullValue());
        } else {
            LibSql.bindValue(handle, LibSql.blob(arena, value));
        }
        return this;
    }

    public Statement bindNull() {
        checkOpen();
        LibSql.bindValue(handle, LibSql.nullValue());
        return this;
    }

    // --- Named binding ---

    public Statement bind(String name, long value) {
        checkOpen();
        LibSql.bindNamed(arena, handle, name, LibSql.integer(value));
        return this;
    }

    public Statement bind(String name, double value) {
        checkOpen();
        LibSql.bindNamed(arena, handle, name, LibSql.real(value));
        return this;
    }

    public Statement bind(String name, String value) {
        checkOpen();
        if (value == null) {
            LibSql.bindNamed(arena, handle, name, LibSql.nullValue());
        } else {
            LibSql.bindNamed(arena, handle, name, LibSql.text(arena, value));
        }
        return this;
    }

    public Statement bind(String name, byte[] value) {
        checkOpen();
        if (value == null) {
            LibSql.bindNamed(arena, handle, name, LibSql.nullValue());
        } else {
            LibSql.bindNamed(arena, handle, name, LibSql.blob(arena, value));
        }
        return this;
    }

    public Statement bindNull(String name) {
        checkOpen();
        LibSql.bindNamed(arena, handle, name, LibSql.nullValue());
        return this;
    }

    // --- Execution ---

    /** Execute the statement (INSERT/UPDATE/DELETE). Returns rows changed. */
    public long execute() {
        checkOpen();
        return LibSql.statementExecute(handle);
    }

    /** Execute the statement as a query. Returns iterable Rows. Caller must close Rows. */
    public Rows query() {
        checkOpen();
        return new Rows(LibSql.statementQuery(handle));
    }

    /** Reset the statement for re-use with new bindings. */
    public void reset() {
        checkOpen();
        LibSql.statementReset(handle);
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("Statement is closed");
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            cleanAction.disarmed = true;
            if (cleanable != null) cleanable.clean();
            LibSql.statementDeinit(handle);
            arena.close();
        }
    }
}
