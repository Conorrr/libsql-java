# libsql-java

Java FFM (Panama) bindings for libsql-c. Requires Java 25. libsql-c pinned as git submodule at `v0.3.4`.

## Build

```
git submodule update --init --recursive  # or clone with --recurse-submodules
./gradlew build                # compile + test
./gradlew buildLibsqlNative    # build native library via Cargo (requires Rust)
./gradlew test                 # run tests only
./gradlew bench                # run benchmarks (separate from tests)
./gradlew cleanNative          # remove native build outputs
```

Native lib output: `native/{os}-{arch}/liblibsql.{dylib|so|dll}`

## Test

Tests use JUnit Jupiter. Run with:

```
./gradlew test
```

JVM flag `--enable-native-access=ALL-UNNAMED` is applied automatically.

## Key source files

High-level API (prefer these):
- `src/main/java/uk/co/rstl/libsql/Database.java` — database open/close
- `src/main/java/uk/co/rstl/libsql/Connection.java` — batch/prepare/transaction
- `src/main/java/uk/co/rstl/libsql/Statement.java` — bind/execute/query
- `src/main/java/uk/co/rstl/libsql/Transaction.java` — commit/rollback, auto-rollback on close
- `src/main/java/uk/co/rstl/libsql/Rows.java` — iterable result set
- `src/main/java/uk/co/rstl/libsql/Row.java` — typed column getters

Low-level / infra:
- `src/main/java/uk/co/rstl/libsql/LibSql.java` — raw FFM bindings (low-level escape hatch)
- `src/main/java/uk/co/rstl/libsql/LibSqlLoader.java` — native library loader
- `src/main/java/uk/co/rstl/libsql/LibSqlNativeFeature.java` — GraalVM native image feature
- `src/main/java/uk/co/rstl/libsql/LibSqlException.java` — exception type

## Publishing

Publishes to GitHub Packages. Requires env vars:
- `GITHUB_ACTOR`
- `GITHUB_TOKEN`

```
./gradlew publish
```
