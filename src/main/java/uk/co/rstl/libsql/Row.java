package uk.co.rstl.libsql;

import java.lang.foreign.MemorySegment;

/**
 * AutoCloseable wrapper around a single libsql row.
 * Provides typed getters for column values.
 */
public final class Row implements AutoCloseable {

    private final MemorySegment handle;
    private boolean closed;

    Row(MemorySegment handle) {
        this.handle = handle;
    }

    /** Number of columns in this row. */
    public int length() {
        checkOpen();
        return LibSql.rowLength(handle);
    }

    /** Get the raw value at the given column index (Long, Double, String, byte[], or null). */
    public Object get(int index) {
        checkOpen();
        return LibSql.extractValue(LibSql.rowValue(handle, index));
    }

    /** Get an integer column value. */
    public long getLong(int index) {
        var v = get(index);
        if (v == null) throw new NullPointerException("Column " + index + " is NULL");
        return (long) v;
    }

    /** Get a real column value. */
    public double getDouble(int index) {
        var v = get(index);
        if (v == null) throw new NullPointerException("Column " + index + " is NULL");
        return (double) v;
    }

    /** Get a text column value (null if SQL NULL). */
    public String getString(int index) {
        return (String) get(index);
    }

    /** Get a blob column value (null if SQL NULL). */
    public byte[] getBlob(int index) {
        return (byte[]) get(index);
    }

    /** Returns true if the column at the given index is NULL. */
    public boolean isNull(int index) {
        return get(index) == null;
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("Row is closed");
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            LibSql.rowDeinit(handle);
        }
    }
}
