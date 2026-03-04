package dev.libsql;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * AutoCloseable wrapper around a libsql database handle.
 * <p>
 * Use {@link #open(String)} for local/in-memory databases, or the builder-style
 * static methods for remote and synced replicas.
 */
public final class Database implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment handle;
    private boolean closed;

    private Database(Arena arena, MemorySegment handle) {
        this.arena = arena;
        this.handle = handle;
    }

    /** Open a local or in-memory database (use ":memory:" for in-memory). */
    public static Database open(String path) {
        var arena = Arena.ofConfined();
        var handle = LibSql.databaseInit(arena, path, null, null, 0);
        return new Database(arena, handle);
    }

    /** Open a remote database (Turso). */
    public static Database openRemote(String url, String authToken) {
        var arena = Arena.ofConfined();
        var handle = LibSql.databaseInit(arena, null, url, authToken, 0);
        return new Database(arena, handle);
    }

    /** Open a synced replica (local file + remote). */
    public static Database openSynced(String path, String url, String authToken, long syncInterval) {
        var arena = Arena.ofConfined();
        var handle = LibSql.databaseInit(arena, path, url, authToken, syncInterval);
        return new Database(arena, handle);
    }

    /** Pull latest changes from remote (synced replicas only). */
    public void sync() {
        checkOpen();
        LibSql.databaseSync(handle);
    }

    /** Create a new connection to this database. */
    public Connection connect() {
        checkOpen();
        return new Connection(this, LibSql.databaseConnect(handle));
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("Database is closed");
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            LibSql.databaseDeinit(handle);
            arena.close();
        }
    }
}
