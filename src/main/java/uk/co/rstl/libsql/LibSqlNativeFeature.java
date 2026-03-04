package uk.co.rstl.libsql;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeForeignAccess;

import java.lang.foreign.*;

import static java.lang.foreign.ValueLayout.*;

/**
 * Registers all FFM downcall descriptors used by {@link LibSql} for GraalVM native image.
 * <p>
 * Struct layouts are duplicated from LibSql because LibSql is initialized at run-time
 * (native library load), but this Feature runs at build-time. Keep in sync with LibSql.
 */
public class LibSqlNativeFeature implements Feature {

    // Struct layouts (duplicated from LibSql — keep in sync)
    private static final StructLayout HANDLE = MemoryLayout.structLayout(
            ADDRESS.withName("err"), ADDRESS.withName("inner"));
    private static final StructLayout ERR_ONLY = MemoryLayout.structLayout(
            ADDRESS.withName("err"));
    private static final StructLayout SLICE = MemoryLayout.structLayout(
            ADDRESS.withName("ptr"), JAVA_LONG.withName("len"));
    private static final StructLayout VALUE_UNION = MemoryLayout.structLayout(
            JAVA_LONG.withName("first"), JAVA_LONG.withName("second"));
    private static final StructLayout VALUE = MemoryLayout.structLayout(
            VALUE_UNION.withName("value"), JAVA_INT.withName("type"),
            MemoryLayout.paddingLayout(4));
    private static final StructLayout RESULT_VALUE = MemoryLayout.structLayout(
            ADDRESS.withName("err"), VALUE.withName("ok"));
    private static final StructLayout EXECUTE = MemoryLayout.structLayout(
            ADDRESS.withName("err"), JAVA_LONG.withName("rows_changed"));
    private static final StructLayout SYNC = MemoryLayout.structLayout(
            ADDRESS.withName("err"), JAVA_LONG.withName("frame_no"),
            JAVA_LONG.withName("frames_synced"));
    private static final StructLayout CONN_INFO = MemoryLayout.structLayout(
            ADDRESS.withName("err"), JAVA_LONG.withName("last_inserted_rowid"),
            JAVA_LONG.withName("total_changes"));
    private static final StructLayout DB_DESC = MemoryLayout.structLayout(
            ADDRESS.withName("url"), ADDRESS.withName("path"),
            ADDRESS.withName("auth_token"), ADDRESS.withName("encryption_key"),
            JAVA_LONG.withName("sync_interval"), JAVA_INT.withName("cypher"),
            JAVA_BOOLEAN.withName("disable_read_your_writes"),
            JAVA_BOOLEAN.withName("webpki"), JAVA_BOOLEAN.withName("synced"),
            JAVA_BOOLEAN.withName("disable_safety_assert"),
            ADDRESS.withName("namespace"));
    private static final StructLayout CONFIG = MemoryLayout.structLayout(
            ADDRESS.withName("logger"), ADDRESS.withName("version"));

    @Override
    public void duringSetup(DuringSetupAccess access) {
        // setup / error
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(ADDRESS, CONFIG));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(ADDRESS, ADDRESS));
        // database
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(HANDLE, DB_DESC));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(SYNC, HANDLE));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(HANDLE, HANDLE));
        // connection
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(ERR_ONLY, HANDLE, ADDRESS));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(CONN_INFO, HANDLE));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(HANDLE, HANDLE, ADDRESS));
        // statement
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(EXECUTE, HANDLE));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.ofVoid(HANDLE));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(JAVA_LONG, HANDLE));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(ERR_ONLY, HANDLE, VALUE));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(ERR_ONLY, HANDLE, ADDRESS, VALUE));
        // rows / row
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(SLICE, HANDLE, JAVA_INT));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(JAVA_INT, HANDLE));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(RESULT_VALUE, HANDLE, JAVA_INT));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(JAVA_BOOLEAN, HANDLE));
        // value constructors
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(VALUE, JAVA_LONG));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(VALUE, JAVA_DOUBLE));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(VALUE, ADDRESS, JAVA_LONG));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(VALUE));
        // deinit
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.ofVoid(ADDRESS));
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.ofVoid(SLICE));
    }
}
