package uk.co.rstl.libsql;

import java.lang.foreign.MemorySegment;
import java.util.Iterator;

/**
 * AutoCloseable wrapper around a libsql rows result set.
 * Implements {@link Iterable} for use with enhanced for-loops.
 */
public final class Rows implements AutoCloseable, Iterable<Row> {

    private final MemorySegment handle;
    private boolean closed;

    Rows(MemorySegment handle) {
        this.handle = handle;
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
                var current = pending;
                pending = null;
                return current;
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
            LibSql.rowsDeinit(handle);
        }
    }
}
