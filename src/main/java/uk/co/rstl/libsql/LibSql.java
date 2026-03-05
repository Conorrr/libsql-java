package uk.co.rstl.libsql;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.foreign.ValueLayout.*;

/**
 * FFM bindings to libsql-c (libsql.h).
 * <p>
 * Struct layouts match the C header. Return structs use { err_ptr, inner_ptr } layout
 * (or { err_ptr } for batch/bind). All pointers are ADDRESS.
 */
public final class LibSql {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LIB = LibSqlLoader.get();

    // --- Struct Layouts ---

    /** { libsql_error_t *err; void *inner; } — used by database_t, connection_t, statement_t, transaction_t, rows_t, row_t */
    static final StructLayout HANDLE_LAYOUT = MemoryLayout.structLayout(
            ADDRESS.withName("err"),
            ADDRESS.withName("inner")
    ).withName("libsql_handle_t");

    /** { libsql_error_t *err; } — batch_t, bind_t */
    static final StructLayout ERR_ONLY_LAYOUT = MemoryLayout.structLayout(
            ADDRESS.withName("err")
    ).withName("libsql_err_only_t");

    /** { const void *ptr; size_t len; } — libsql_slice_t */
    static final StructLayout SLICE_LAYOUT = MemoryLayout.structLayout(
            ADDRESS.withName("ptr"),
            JAVA_LONG.withName("len")
    ).withName("libsql_slice_t");

    /**
     * libsql_value_union_t is a union of: int64, double, slice, slice.
     * Max size = slice (ptr + len) = 16 bytes on 64-bit.
     * We lay it out as two longs to cover all union members.
     */
    static final StructLayout VALUE_UNION_LAYOUT = MemoryLayout.structLayout(
            JAVA_LONG.withName("first"),
            JAVA_LONG.withName("second")
    ).withName("libsql_value_union_t");

    /** { libsql_value_union_t value; int type; padding; } — libsql_value_t */
    static final StructLayout VALUE_LAYOUT = MemoryLayout.structLayout(
            VALUE_UNION_LAYOUT.withName("value"),
            JAVA_INT.withName("type"),
            MemoryLayout.paddingLayout(4)
    ).withName("libsql_value_t");

    /** { libsql_error_t *err; libsql_value_t ok; } — libsql_result_value_t */
    static final StructLayout RESULT_VALUE_LAYOUT = MemoryLayout.structLayout(
            ADDRESS.withName("err"),
            VALUE_LAYOUT.withName("ok")
    ).withName("libsql_result_value_t");

    /** { libsql_error_t *err; uint64_t rows_changed; } — libsql_execute_t */
    static final StructLayout EXECUTE_LAYOUT = MemoryLayout.structLayout(
            ADDRESS.withName("err"),
            JAVA_LONG.withName("rows_changed")
    ).withName("libsql_execute_t");

    /** { libsql_error_t *err; uint64_t frame_no; uint64_t frames_synced; } — libsql_sync_t */
    static final StructLayout SYNC_LAYOUT = MemoryLayout.structLayout(
            ADDRESS.withName("err"),
            JAVA_LONG.withName("frame_no"),
            JAVA_LONG.withName("frames_synced")
    ).withName("libsql_sync_t");

    /** { libsql_error_t *err; int64_t last_inserted_rowid; uint64_t total_changes; } */
    static final StructLayout CONNECTION_INFO_LAYOUT = MemoryLayout.structLayout(
            ADDRESS.withName("err"),
            JAVA_LONG.withName("last_inserted_rowid"),
            JAVA_LONG.withName("total_changes")
    ).withName("libsql_connection_info_t");

    /**
     * libsql_database_desc_t — see libsql.h.
     * Fields: url, path, auth_token, encryption_key, sync_interval,
     *         cypher(int), disable_read_your_writes(bool), webpki(bool), synced(bool),
     *         disable_safety_assert(bool), namespace.
     * We use sequential layout matching C struct packing.
     */
    static final StructLayout DATABASE_DESC_LAYOUT = MemoryLayout.structLayout(
            ADDRESS.withName("url"),
            ADDRESS.withName("path"),
            ADDRESS.withName("auth_token"),
            ADDRESS.withName("encryption_key"),
            JAVA_LONG.withName("sync_interval"),
            JAVA_INT.withName("cypher"),
            JAVA_BOOLEAN.withName("disable_read_your_writes"),
            JAVA_BOOLEAN.withName("webpki"),
            JAVA_BOOLEAN.withName("synced"),
            JAVA_BOOLEAN.withName("disable_safety_assert"),
            ADDRESS.withName("namespace")
    ).withName("libsql_database_desc_t");

    /** { void (*logger)(...); const char *version; } — libsql_config_t */
    static final StructLayout CONFIG_LAYOUT = MemoryLayout.structLayout(
            ADDRESS.withName("logger"),
            ADDRESS.withName("version")
    ).withName("libsql_config_t");

    // --- Method Handles ---

    // const libsql_error_t *libsql_setup(libsql_config_t config)
    private static final MethodHandle SETUP = downcall("libsql_setup",
            FunctionDescriptor.of(ADDRESS, CONFIG_LAYOUT));

    // const char *libsql_error_message(libsql_error_t *self)
    private static final MethodHandle ERROR_MESSAGE = downcall("libsql_error_message",
            FunctionDescriptor.of(ADDRESS, ADDRESS));

    // libsql_database_t libsql_database_init(libsql_database_desc_t desc)
    private static final MethodHandle DATABASE_INIT = downcall("libsql_database_init",
            FunctionDescriptor.of(HANDLE_LAYOUT, DATABASE_DESC_LAYOUT));

    // libsql_sync_t libsql_database_sync(libsql_database_t self)
    private static final MethodHandle DATABASE_SYNC = downcall("libsql_database_sync",
            FunctionDescriptor.of(SYNC_LAYOUT, HANDLE_LAYOUT));

    // libsql_connection_t libsql_database_connect(libsql_database_t self)
    private static final MethodHandle DATABASE_CONNECT = downcall("libsql_database_connect",
            FunctionDescriptor.of(HANDLE_LAYOUT, HANDLE_LAYOUT));

    // libsql_transaction_t libsql_connection_transaction(libsql_connection_t self)
    private static final MethodHandle CONNECTION_TRANSACTION = downcall("libsql_connection_transaction",
            FunctionDescriptor.of(HANDLE_LAYOUT, HANDLE_LAYOUT));

    // libsql_batch_t libsql_connection_batch(libsql_connection_t self, const char *sql)
    private static final MethodHandle CONNECTION_BATCH = downcall("libsql_connection_batch",
            FunctionDescriptor.of(ERR_ONLY_LAYOUT, HANDLE_LAYOUT, ADDRESS));

    // libsql_connection_info_t libsql_connection_info(libsql_connection_t self)
    private static final MethodHandle CONNECTION_INFO = downcall("libsql_connection_info",
            FunctionDescriptor.of(CONNECTION_INFO_LAYOUT, HANDLE_LAYOUT));

    // libsql_batch_t libsql_transaction_batch(libsql_transaction_t self, const char *sql)
    private static final MethodHandle TRANSACTION_BATCH = downcall("libsql_transaction_batch",
            FunctionDescriptor.of(ERR_ONLY_LAYOUT, HANDLE_LAYOUT, ADDRESS));

    // libsql_statement_t libsql_connection_prepare(libsql_connection_t self, const char *sql)
    private static final MethodHandle CONNECTION_PREPARE = downcall("libsql_connection_prepare",
            FunctionDescriptor.of(HANDLE_LAYOUT, HANDLE_LAYOUT, ADDRESS));

    // libsql_statement_t libsql_transaction_prepare(libsql_transaction_t self, const char *sql)
    private static final MethodHandle TRANSACTION_PREPARE = downcall("libsql_transaction_prepare",
            FunctionDescriptor.of(HANDLE_LAYOUT, HANDLE_LAYOUT, ADDRESS));

    // libsql_execute_t libsql_statement_execute(libsql_statement_t self)
    private static final MethodHandle STATEMENT_EXECUTE = downcall("libsql_statement_execute",
            FunctionDescriptor.of(EXECUTE_LAYOUT, HANDLE_LAYOUT));

    // libsql_rows_t libsql_statement_query(libsql_statement_t self)
    private static final MethodHandle STATEMENT_QUERY = downcall("libsql_statement_query",
            FunctionDescriptor.of(HANDLE_LAYOUT, HANDLE_LAYOUT));

    // void libsql_statement_reset(libsql_statement_t self)
    private static final MethodHandle STATEMENT_RESET = downcall("libsql_statement_reset",
            FunctionDescriptor.ofVoid(HANDLE_LAYOUT));

    // size_t libsql_statement_column_count(libsql_statement_t self)
    private static final MethodHandle STATEMENT_COLUMN_COUNT = downcall("libsql_statement_column_count",
            FunctionDescriptor.of(JAVA_LONG, HANDLE_LAYOUT));

    // libsql_bind_t libsql_statement_bind_value(libsql_statement_t self, libsql_value_t value)
    private static final MethodHandle STATEMENT_BIND_VALUE = downcall("libsql_statement_bind_value",
            FunctionDescriptor.of(ERR_ONLY_LAYOUT, HANDLE_LAYOUT, VALUE_LAYOUT));

    // libsql_bind_t libsql_statement_bind_named(libsql_statement_t self, const char *name, libsql_value_t value)
    private static final MethodHandle STATEMENT_BIND_NAMED = downcall("libsql_statement_bind_named",
            FunctionDescriptor.of(ERR_ONLY_LAYOUT, HANDLE_LAYOUT, ADDRESS, VALUE_LAYOUT));

    // libsql_row_t libsql_rows_next(libsql_rows_t self)
    private static final MethodHandle ROWS_NEXT = downcall("libsql_rows_next",
            FunctionDescriptor.of(HANDLE_LAYOUT, HANDLE_LAYOUT));

    // libsql_slice_t libsql_rows_column_name(libsql_rows_t self, int32_t index)
    private static final MethodHandle ROWS_COLUMN_NAME = downcall("libsql_rows_column_name",
            FunctionDescriptor.of(SLICE_LAYOUT, HANDLE_LAYOUT, JAVA_INT));

    // int32_t libsql_rows_column_count(libsql_rows_t self)
    private static final MethodHandle ROWS_COLUMN_COUNT = downcall("libsql_rows_column_count",
            FunctionDescriptor.of(JAVA_INT, HANDLE_LAYOUT));

    // libsql_result_value_t libsql_row_value(libsql_row_t self, int32_t index)
    private static final MethodHandle ROW_VALUE = downcall("libsql_row_value",
            FunctionDescriptor.of(RESULT_VALUE_LAYOUT, HANDLE_LAYOUT, JAVA_INT));

    // libsql_slice_t libsql_row_name(libsql_row_t self, int32_t index)
    private static final MethodHandle ROW_NAME = downcall("libsql_row_name",
            FunctionDescriptor.of(SLICE_LAYOUT, HANDLE_LAYOUT, JAVA_INT));

    // int32_t libsql_row_length(libsql_row_t self)
    private static final MethodHandle ROW_LENGTH = downcall("libsql_row_length",
            FunctionDescriptor.of(JAVA_INT, HANDLE_LAYOUT));

    // bool libsql_row_empty(libsql_row_t self)
    private static final MethodHandle ROW_EMPTY = downcall("libsql_row_empty",
            FunctionDescriptor.of(JAVA_BOOLEAN, HANDLE_LAYOUT));

    // Value constructors
    // libsql_value_t libsql_integer(int64_t integer)
    private static final MethodHandle VALUE_INTEGER = downcall("libsql_integer",
            FunctionDescriptor.of(VALUE_LAYOUT, JAVA_LONG));
    // libsql_value_t libsql_real(double real)
    private static final MethodHandle VALUE_REAL = downcall("libsql_real",
            FunctionDescriptor.of(VALUE_LAYOUT, JAVA_DOUBLE));
    // libsql_value_t libsql_text(const char *ptr, size_t len)
    private static final MethodHandle VALUE_TEXT = downcall("libsql_text",
            FunctionDescriptor.of(VALUE_LAYOUT, ADDRESS, JAVA_LONG));
    // libsql_value_t libsql_blob(const uint8_t *ptr, size_t len)
    private static final MethodHandle VALUE_BLOB = downcall("libsql_blob",
            FunctionDescriptor.of(VALUE_LAYOUT, ADDRESS, JAVA_LONG));
    // libsql_value_t libsql_null()
    private static final MethodHandle VALUE_NULL = downcall("libsql_null",
            FunctionDescriptor.of(VALUE_LAYOUT));

    // Deinit functions
    private static final MethodHandle ERROR_DEINIT = downcall("libsql_error_deinit",
            FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle DATABASE_DEINIT = downcall("libsql_database_deinit",
            FunctionDescriptor.ofVoid(HANDLE_LAYOUT));
    private static final MethodHandle CONNECTION_DEINIT = downcall("libsql_connection_deinit",
            FunctionDescriptor.ofVoid(HANDLE_LAYOUT));
    private static final MethodHandle STATEMENT_DEINIT = downcall("libsql_statement_deinit",
            FunctionDescriptor.ofVoid(HANDLE_LAYOUT));
    private static final MethodHandle TRANSACTION_COMMIT = downcall("libsql_transaction_commit",
            FunctionDescriptor.ofVoid(HANDLE_LAYOUT));
    private static final MethodHandle TRANSACTION_ROLLBACK = downcall("libsql_transaction_rollback",
            FunctionDescriptor.ofVoid(HANDLE_LAYOUT));
    private static final MethodHandle ROWS_DEINIT = downcall("libsql_rows_deinit",
            FunctionDescriptor.ofVoid(HANDLE_LAYOUT));
    private static final MethodHandle ROW_DEINIT = downcall("libsql_row_deinit",
            FunctionDescriptor.ofVoid(HANDLE_LAYOUT));
    private static final MethodHandle SLICE_DEINIT = downcall("libsql_slice_deinit",
            FunctionDescriptor.ofVoid(SLICE_LAYOUT));

    // --- Type constants (from libsql_type_t enum) ---
    public static final int TYPE_INTEGER = 1;
    public static final int TYPE_REAL = 2;
    public static final int TYPE_TEXT = 3;
    public static final int TYPE_BLOB = 4;
    public static final int TYPE_NULL = 5;

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    /** Shared cleaner for leak detection. Null if disabled via {@code -Dlibsql.disableCleaner=true}. */
    static final Cleaner CLEANER;

    static {
        boolean enabled = !Boolean.getBoolean("libsql.disableCleaner");
        CLEANER = enabled ? Cleaner.create() : null;
    }

    private LibSql() {}

    // Auto-initialize on first class load
    static {
        setup();
    }

    // --- Public API ---

    /** Initialize the native library. Safe to call multiple times (idempotent). */
    public static void setup() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        try {
            var config = Arena.ofAuto().allocate(CONFIG_LAYOUT);
            config.set(ADDRESS, CONFIG_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("logger")), MemorySegment.NULL);
            config.set(ADDRESS, CONFIG_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("version")), MemorySegment.NULL);
            var err = (MemorySegment) SETUP.invokeExact(config);
            if (!err.equals(MemorySegment.NULL)) {
                INITIALIZED.set(false);
                throw new LibSqlException(readErrorMessage(err));
            }
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            INITIALIZED.set(false);
            throw new RuntimeException("libsql_setup failed", t);
        }
    }

    public static MemorySegment databaseInit(Arena arena, String path, String url, String authToken, long syncInterval) {
        try {
            var desc = arena.allocate(DATABASE_DESC_LAYOUT);
            desc.fill((byte) 0);
            if (url != null && !url.isEmpty()) {
                desc.set(ADDRESS, DATABASE_DESC_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("url")),
                        arena.allocateFrom(url));
            }
            if (path != null && !path.isEmpty()) {
                desc.set(ADDRESS, DATABASE_DESC_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("path")),
                        arena.allocateFrom(path));
            }
            if (authToken != null && !authToken.isEmpty()) {
                desc.set(ADDRESS, DATABASE_DESC_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("auth_token")),
                        arena.allocateFrom(authToken));
            }
            desc.set(JAVA_LONG, DATABASE_DESC_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("sync_interval")),
                    syncInterval);
            var result = (MemorySegment) DATABASE_INIT.invokeExact((SegmentAllocator) arena, desc);
            checkHandle(result);
            return result;
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_database_init failed", t);
        }
    }

    public static void databaseSync(MemorySegment db) {
        try {
            var result = (MemorySegment) DATABASE_SYNC.invokeExact((SegmentAllocator) Arena.ofAuto(), db);
            var errPtr = result.get(ADDRESS, SYNC_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("err")));
            if (!errPtr.equals(MemorySegment.NULL)) {
                throw new LibSqlException(readAndFreeError(errPtr));
            }
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_database_sync failed", t);
        }
    }

    public static MemorySegment databaseConnect(MemorySegment db) {
        try {
            var result = (MemorySegment) DATABASE_CONNECT.invokeExact((SegmentAllocator) Arena.ofAuto(), db);
            checkHandle(result);
            return result;
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_database_connect failed", t);
        }
    }

    public static MemorySegment connectionTransaction(MemorySegment conn) {
        try {
            var result = (MemorySegment) CONNECTION_TRANSACTION.invokeExact((SegmentAllocator) Arena.ofAuto(), conn);
            checkHandle(result);
            return result;
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_connection_transaction failed", t);
        }
    }

    public static void connectionBatch(Arena arena, MemorySegment conn, String sql) {
        try {
            var sqlSeg = arena.allocateFrom(sql);
            var result = (MemorySegment) CONNECTION_BATCH.invokeExact((SegmentAllocator) arena, conn, sqlSeg);
            checkErrOnly(result);
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_connection_batch failed", t);
        }
    }

    public static void transactionBatch(Arena arena, MemorySegment tx, String sql) {
        try {
            var sqlSeg = arena.allocateFrom(sql);
            var result = (MemorySegment) TRANSACTION_BATCH.invokeExact((SegmentAllocator) arena, tx, sqlSeg);
            checkErrOnly(result);
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_transaction_batch failed", t);
        }
    }

    public static MemorySegment connectionPrepare(Arena arena, MemorySegment conn, String sql) {
        try {
            var sqlSeg = arena.allocateFrom(sql);
            var result = (MemorySegment) CONNECTION_PREPARE.invokeExact((SegmentAllocator) arena, conn, sqlSeg);
            checkHandle(result);
            return result;
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_connection_prepare failed", t);
        }
    }

    public static MemorySegment transactionPrepare(Arena arena, MemorySegment tx, String sql) {
        try {
            var sqlSeg = arena.allocateFrom(sql);
            var result = (MemorySegment) TRANSACTION_PREPARE.invokeExact((SegmentAllocator) arena, tx, sqlSeg);
            checkHandle(result);
            return result;
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_transaction_prepare failed", t);
        }
    }

    public static long statementExecute(MemorySegment stmt) {
        try {
            var result = (MemorySegment) STATEMENT_EXECUTE.invokeExact((SegmentAllocator) Arena.ofAuto(), stmt);
            var errPtr = result.get(ADDRESS, EXECUTE_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("err")));
            if (!errPtr.equals(MemorySegment.NULL)) {
                throw new LibSqlException(readAndFreeError(errPtr));
            }
            return result.get(JAVA_LONG, EXECUTE_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("rows_changed")));
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_statement_execute failed", t);
        }
    }

    public static MemorySegment statementQuery(MemorySegment stmt) {
        try {
            var result = (MemorySegment) STATEMENT_QUERY.invokeExact((SegmentAllocator) Arena.ofAuto(), stmt);
            checkHandle(result);
            return result;
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_statement_query failed", t);
        }
    }

    public static void statementReset(MemorySegment stmt) {
        try {
            STATEMENT_RESET.invokeExact(stmt);
        } catch (Throwable t) {
            throw new RuntimeException("libsql_statement_reset failed", t);
        }
    }

    public static void bindValue(MemorySegment stmt, MemorySegment value) {
        try {
            var result = (MemorySegment) STATEMENT_BIND_VALUE.invokeExact((SegmentAllocator) Arena.ofAuto(), stmt, value);
            checkErrOnly(result);
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_statement_bind_value failed", t);
        }
    }

    public static void bindNamed(Arena arena, MemorySegment stmt, String name, MemorySegment value) {
        try {
            var nameSeg = arena.allocateFrom(name);
            var result = (MemorySegment) STATEMENT_BIND_NAMED.invokeExact((SegmentAllocator) arena, stmt, nameSeg, value);
            checkErrOnly(result);
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_statement_bind_named failed", t);
        }
    }

    // Value constructors

    public static MemorySegment integer(long v) {
        try {
            return (MemorySegment) VALUE_INTEGER.invokeExact((SegmentAllocator) Arena.ofAuto(), v);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static MemorySegment real(double v) {
        try {
            return (MemorySegment) VALUE_REAL.invokeExact((SegmentAllocator) Arena.ofAuto(), v);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static MemorySegment text(Arena arena, String v) {
        try {
            var seg = arena.allocateFrom(v); // null-terminated UTF-8
            long len = seg.byteSize() - 1;   // exclude null terminator
            return (MemorySegment) VALUE_TEXT.invokeExact((SegmentAllocator) arena, seg, len);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static MemorySegment blob(Arena arena, byte[] v) {
        try {
            var seg = arena.allocateFrom(JAVA_BYTE, v);
            return (MemorySegment) VALUE_BLOB.invokeExact((SegmentAllocator) arena, seg, (long) v.length);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static MemorySegment nullValue() {
        try {
            return (MemorySegment) VALUE_NULL.invokeExact((SegmentAllocator) Arena.ofAuto());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Row/Rows access

    public static MemorySegment rowsNext(MemorySegment rows) {
        try {
            var result = (MemorySegment) ROWS_NEXT.invokeExact((SegmentAllocator) Arena.ofAuto(), rows);
            // Don't check error — use rowEmpty() to detect end
            return result;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_rows_next failed", t);
        }
    }

    public static boolean rowEmpty(MemorySegment row) {
        try {
            return (boolean) ROW_EMPTY.invokeExact(row);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static int rowsColumnCount(MemorySegment rows) {
        try {
            return (int) ROWS_COLUMN_COUNT.invokeExact(rows);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static String rowsColumnName(MemorySegment rows, int index) {
        try {
            var slice = (MemorySegment) ROWS_COLUMN_NAME.invokeExact((SegmentAllocator) Arena.ofAuto(), rows, index);
            return readAndFreeSlice(slice);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static int rowLength(MemorySegment row) {
        try {
            return (int) ROW_LENGTH.invokeExact(row);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Get value at index from row. Returns the libsql_result_value_t segment.
     * Caller should use extractValue() to read the typed value.
     */
    public static MemorySegment rowValue(MemorySegment row, int index) {
        try {
            var result = (MemorySegment) ROW_VALUE.invokeExact((SegmentAllocator) Arena.ofAuto(), row, index);
            var errPtr = result.get(ADDRESS, RESULT_VALUE_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("err")));
            if (!errPtr.equals(MemorySegment.NULL)) {
                throw new LibSqlException(readAndFreeError(errPtr));
            }
            return result;
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("libsql_row_value failed", t);
        }
    }

    /**
     * Extract a Java object from a libsql_result_value_t segment.
     * Returns Long, Double, String, byte[], or null.
     */
    public static Object extractValue(MemorySegment resultValue) {
        long valueOffset = RESULT_VALUE_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("ok"));
        long typeOffset = valueOffset + VALUE_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("type"));
        long unionOffset = valueOffset + VALUE_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("value"));

        int type = resultValue.get(JAVA_INT, typeOffset);
        return switch (type) {
            case TYPE_INTEGER -> resultValue.get(JAVA_LONG, unionOffset);
            case TYPE_REAL -> resultValue.get(JAVA_DOUBLE, unionOffset);
            case TYPE_TEXT -> {
                var ptr = resultValue.get(ADDRESS, unionOffset);
                long len = resultValue.get(JAVA_LONG, unionOffset + 8);
                if (ptr.equals(MemorySegment.NULL) || len == 0) yield "";
                yield ptr.reinterpret(len).getString(0);
            }
            case TYPE_BLOB -> {
                var ptr = resultValue.get(ADDRESS, unionOffset);
                long len = resultValue.get(JAVA_LONG, unionOffset + 8);
                if (ptr.equals(MemorySegment.NULL) || len == 0) yield new byte[0];
                yield ptr.reinterpret(len).toArray(JAVA_BYTE);
            }
            case TYPE_NULL -> null;
            default -> throw new IllegalStateException("Unknown libsql type: " + type);
        };
    }

    // Deinit wrappers

    public static void databaseDeinit(MemorySegment db) {
        try { DATABASE_DEINIT.invokeExact(db); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void connectionDeinit(MemorySegment conn) {
        try { CONNECTION_DEINIT.invokeExact(conn); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void statementDeinit(MemorySegment stmt) {
        try { STATEMENT_DEINIT.invokeExact(stmt); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void transactionCommit(MemorySegment tx) {
        try { TRANSACTION_COMMIT.invokeExact(tx); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void transactionRollback(MemorySegment tx) {
        try { TRANSACTION_ROLLBACK.invokeExact(tx); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void rowsDeinit(MemorySegment rows) {
        try { ROWS_DEINIT.invokeExact(rows); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void rowDeinit(MemorySegment row) {
        try { ROW_DEINIT.invokeExact(row); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void sliceDeinit(MemorySegment slice) {
        try { SLICE_DEINIT.invokeExact(slice); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    // --- Internal helpers ---

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        var symbol = LIB.find(name)
                .orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static void checkHandle(MemorySegment handleResult) {
        var errPtr = handleResult.get(ADDRESS, HANDLE_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("err")));
        if (!errPtr.equals(MemorySegment.NULL)) {
            throw new LibSqlException(readAndFreeError(errPtr));
        }
    }

    private static void checkErrOnly(MemorySegment errResult) {
        var errPtr = errResult.get(ADDRESS, ERR_ONLY_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("err")));
        if (!errPtr.equals(MemorySegment.NULL)) {
            throw new LibSqlException(readAndFreeError(errPtr));
        }
    }

    /** Read the error message and free the error struct. */
    private static String readAndFreeError(MemorySegment errPtr) {
        try {
            var msg = readErrorMessage(errPtr);
            errorDeinit(errPtr);
            return msg;
        } catch (Throwable t) {
            return "Failed to read error message: " + t.getMessage();
        }
    }

    static String readErrorMessage(MemorySegment errPtr) {
        try {
            var msgPtr = (MemorySegment) ERROR_MESSAGE.invokeExact(errPtr);
            if (msgPtr.equals(MemorySegment.NULL)) return "Unknown error";
            return msgPtr.reinterpret(1024).getString(0);
        } catch (Throwable t) {
            return "Failed to read error message: " + t.getMessage();
        }
    }

    private static void errorDeinit(MemorySegment errPtr) {
        try { ERROR_DEINIT.invokeExact(errPtr); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** Read slice data and free the slice. */
    private static String readAndFreeSlice(MemorySegment slice) {
        var ptr = slice.get(ADDRESS, SLICE_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("ptr")));
        long len = slice.get(JAVA_LONG, SLICE_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("len")));
        String result;
        if (ptr.equals(MemorySegment.NULL) || len == 0) {
            result = "";
        } else {
            result = ptr.reinterpret(len).getString(0);
        }
        sliceDeinit(slice);
        return result;
    }
}
