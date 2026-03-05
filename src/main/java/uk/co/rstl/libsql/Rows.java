package uk.co.rstl.libsql;

import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.util.Iterator;

/**
 * AutoCloseable wrapper around a libsql rows result set.
 * Implements {@link Iterable} for use with enhanced for-loops.
 */
public final class Rows implements AutoCloseable, Iterable<Row> {

    private final MemorySegment handle;
    private final CleanAction cleanAction;
    private final Cleaner.Cleanable cleanable;
    private boolean closed;

    Rows(MemorySegment handle) {
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
            ResourceCleaner.warn("Rows");
            LibSql.rowsDeinit(handle);
        }
    }

    /** Column count for this result set. */
    public int columnCount() {
        checkOpen();
        return LibSql.rowsColumnCount(handle);
    }

    /** Column name at the given index. */
    public String columnName(int index) {
        checkOpen();
        return LibSql.rowsColumnName(handle, index);
    }

    /** Advance to the next row, or return null if exhausted. */
    public Row next() {
        checkOpen();
        var rowSeg = LibSql.rowsNext(handle);
        if (LibSql.rowEmpty(rowSeg)) return null;
        return new Row(rowSeg);
    }

    @Override
    public Iterator<Row> iterator() {
        return new Iterator<>() {
            private Row pending;
            private Row previous;
            private boolean done;

            @Override
            public boolean hasNext() {
                if (done) return false;
                if (pending == null) {
                    pending = Rows.this.next();
                    if (pending == null) {
                        done = true;
                        return false;
                    }
                }
                return true;
            }

            @Override
            public Row next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                if (previous != null) previous.close();
                previous = pending;
                pending = null;
                return previous;
            }
        };
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("Rows is closed");
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            cleanAction.disarmed = true;
            if (cleanable != null) cleanable.clean();
            LibSql.rowsDeinit(handle);
        }
    }
}
