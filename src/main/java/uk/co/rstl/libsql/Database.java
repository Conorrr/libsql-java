package uk.co.rstl.libsql;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AutoCloseable wrapper around a libsql database handle.
 * <p>
 * Use {@link #builder(String)} to configure PRAGMAs and open a database,
 * or {@link #open(String)} as a shorthand with no configuration.
 */
public final class Database implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment handle;
    private final List<String> pragmas;
    private final CleanAction cleanAction;
    private final Cleaner.Cleanable cleanable;
    private boolean closed;

    private Database(Arena arena, MemorySegment handle, List<String> pragmas) {
        this.arena = arena;
        this.handle = handle;
        this.pragmas = pragmas;
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
            ResourceCleaner.warn("Database");
            LibSql.databaseDeinit(handle);
            arena.close();
        }
    }

    /** Open a local or in-memory database (use ":memory:" for in-memory). Shorthand for {@code builder(path).build()}. */
    public static Database open(String path) {
        return builder(path).build();
    }

    /** Open a remote database (Turso). Shorthand for {@code builder().url(url).authToken(token).build()}. */
    public static Database openRemote(String url, String authToken) {
        return builder().url(url).authToken(authToken).build();
    }

    /** Open a synced replica (local file + remote). */
    public static Database openSynced(String path, String url, String authToken, long syncInterval) {
        return builder(path).url(url).authToken(authToken).syncInterval(syncInterval).build();
    }

    /** Create a builder for a local or in-memory database. */
    public static Builder builder(String path) {
        return new Builder().path(path);
    }

    /** Create a builder for a remote or synced database. */
    public static Builder builder() {
        return new Builder();
    }

    /** Pull latest changes from remote (synced replicas only). */
    public void sync() {
        checkOpen();
        LibSql.databaseSync(handle);
    }

    /** Create a new connection to this database. PRAGMAs configured via the builder are applied automatically. */
    public Connection connect() {
        checkOpen();
        var conn = new Connection(this, LibSql.databaseConnect(handle));
        if (!pragmas.isEmpty()) {
            try {
                conn.batch(String.join("; ", pragmas));
            } catch (Exception e) {
                conn.close();
                throw e;
            }
        }
        return conn;
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("Database is closed");
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            cleanAction.disarmed = true;
            if (cleanable != null) cleanable.clean();
            LibSql.databaseDeinit(handle);
            arena.close();
        }
    }

    /** Configures and opens a {@link Database} with optional PRAGMAs. */
    public static final class Builder {
        private String path;
        private String url;
        private String authToken;
        private long syncInterval;
        private final List<String> pragmas = new ArrayList<>();

        private Builder() {}

        /** Local file path or ":memory:". */
        public Builder path(String path) { this.path = path; return this; }

        /** Remote URL (e.g. libsql://your-db.turso.io). */
        public Builder url(String url) { this.url = url; return this; }

        /** Auth token for remote connections. */
        public Builder authToken(String authToken) { this.authToken = authToken; return this; }

        /** Sync interval in seconds (synced replicas only). */
        public Builder syncInterval(long syncInterval) { this.syncInterval = syncInterval; return this; }

        /** Set journal mode (e.g. "WAL", "DELETE"). */
        public Builder journalMode(String mode) { return pragma("journal_mode", mode); }

        /** Set busy timeout in milliseconds. */
        public Builder busyTimeout(int ms) { return pragma("busy_timeout", String.valueOf(ms)); }

        /** Enable or disable foreign key enforcement. */
        public Builder foreignKeys(boolean enabled) { return pragma("foreign_keys", enabled ? "ON" : "OFF"); }

        /** Set synchronous mode (e.g. "NORMAL", "FULL"). */
        public Builder synchronous(String mode) { return pragma("synchronous", mode); }

        /** Set page cache size (negative = KiB, e.g. -2000 ≈ 2 MB). */
        public Builder cacheSize(int pages) { return pragma("cache_size", String.valueOf(pages)); }

        /** Set an arbitrary PRAGMA. */
        public Builder pragma(String key, String value) {
            pragmas.add("PRAGMA " + key + " = " + value);
            return this;
        }

        /** Build and open the database. */
        public Database build() {
            var arena = Arena.ofShared();
            try {
                var handle = LibSql.databaseInit(arena, path, url, authToken, syncInterval);
                return new Database(arena, handle, Collections.unmodifiableList(new ArrayList<>(pragmas)));
            } catch (Throwable t) {
                arena.close();
                throw t;
            }
        }
    }
}
