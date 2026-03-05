# libsql-java

> **Pre-release** — early/experimental build. Not on Maven Central. Available via [GitHub Releases](https://github.com/Conorrr/libsql-java/releases) or build from source.

Java FFM (Panama) bindings for [libsql-c](https://github.com/tursodatabase/libsql-c) — the C interface to [libSQL](https://github.com/tursodatabase/libsql).

## Requirements

- Java 25 (FFM API, required by toolchain)
- `--enable-native-access=ALL-UNNAMED` JVM flag
- Rust stable toolchain (to build the native library from source)

## Quick Start

```java
import uk.co.rstl.libsql.*;

try (var db = Database.builder(":memory:")
        .foreignKeys(true)
        .busyTimeout(5000)
        .build();
     var conn = db.connect()) {

    conn.batch("CREATE TABLE users(id INTEGER, name TEXT)");

    try (var stmt = conn.prepare("INSERT INTO users VALUES (?, ?)")) {
        stmt.bind(1L).bind("Alice");
        stmt.execute();
    }

    try (var stmt = conn.prepare("SELECT id, name FROM users");
         var rows = stmt.query()) {
        for (var row : rows) {
            System.out.println(row.getLong(0) + ": " + row.getString(1));
            row.close();
        }
    }
}
```

No manual `setup()` call needed — initialization is automatic.

## Usage Guide

### Opening Databases

Use `Database.builder()` to configure PRAGMAs and open a database:

```java
// In-memory
var db = Database.builder(":memory:")
        .foreignKeys(true)
        .busyTimeout(5000)
        .build();

// Local file
var db = Database.builder("/path/to/db.sqlite")
        .journalMode("WAL")
        .foreignKeys(true)
        .build();

// Remote (Turso)
var db = Database.builder()
        .url("libsql://your-db.turso.io")
        .authToken("your-auth-token")
        .build();

// Synced replica (local + remote)
var db = Database.builder("/path/to/replica.db")
        .url("libsql://your-db.turso.io")
        .authToken("your-auth-token")
        .syncInterval(60)
        .build();
db.sync(); // pull latest from remote
```

PRAGMAs are auto-applied to every connection created via `db.connect()`.

Shorthand methods without PRAGMAs are still available:

```java
var db = Database.open(":memory:");
var db = Database.openRemote(url, token);
var db = Database.openSynced(path, url, token, syncInterval);
```

### Prepared Statements

```java
// Positional binding
try (var stmt = conn.prepare("INSERT INTO t VALUES (?, ?)")) {
    stmt.bind(1L).bind("hello");
    long rowsChanged = stmt.execute();
}

// Named binding
try (var stmt = conn.prepare("INSERT INTO t VALUES (:id, :name)")) {
    stmt.bind(":id", 1L).bind(":name", "hello");
    stmt.execute();
}

// Reset and re-execute
try (var stmt = conn.prepare("INSERT INTO t VALUES (?)")) {
    stmt.bind(1L);
    stmt.execute();
    stmt.reset();
    stmt.bind(2L);
    stmt.execute();
}
```

### Querying

```java
try (var stmt = conn.prepare("SELECT id, name, score FROM t");
     var rows = stmt.query()) {

    // Column metadata
    int cols = rows.columnCount();
    String name = rows.columnName(0);

    // Iterate with typed getters
    for (var row : rows) {
        long id      = row.getLong(0);
        String n     = row.getString(1);
        double score = row.getDouble(2);
        byte[] data  = row.getBlob(3);   // null if SQL NULL
        boolean nil  = row.isNull(4);
        row.close();
    }
}
```

### Transactions

Uncommitted transactions auto-rollback on close:

```java
// Commit
try (var tx = conn.transaction()) {
    tx.batch("INSERT INTO t VALUES (1)");
    try (var stmt = tx.prepare("INSERT INTO t VALUES (?)")) {
        stmt.bind(2L);
        stmt.execute();
    }
    tx.commit();
}

// Auto-rollback (no commit called)
try (var tx = conn.transaction()) {
    tx.batch("INSERT INTO t VALUES (1)");
    // tx.close() rolls back automatically
}
```

### Value Types

Positional `bind()` overloads:

- `bind(long)` → INTEGER
- `bind(double)` → REAL
- `bind(String)` → TEXT (null-safe: binds NULL if null)
- `bind(byte[])` → BLOB (null-safe)
- `bindNull()` → NULL

Named `bind(name, ...)` overloads follow the same pattern.

### Batch Operations

```java
conn.batch("CREATE TABLE t(v INTEGER); INSERT INTO t VALUES (1); INSERT INTO t VALUES (2)");
```

## Examples

See [`examples/`](examples/) for runnable programs:

- [`InMemoryCrud.java`](examples/InMemoryCrud.java) — basic CRUD operations
- [`LocalFileDatabase.java`](examples/LocalFileDatabase.java) — file-backed database
- [`Transactions.java`](examples/Transactions.java) — commit, rollback, auto-rollback
- [`ParameterBinding.java`](examples/ParameterBinding.java) — positional and named parameters
- [`RemoteSynced.java`](examples/RemoteSynced.java) — Turso remote and synced replica

## Low-Level API (Advanced)

The `LibSql` class exposes raw FFM bindings as a low-level escape hatch. You manage `MemorySegment` handles, `Arena` lifetimes, and `deinit` calls manually. Prefer the high-level wrappers above unless you need direct control.

```java
LibSql.setup(); // idempotent, also called automatically

try (var arena = Arena.ofConfined()) {
    var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
    var conn = LibSql.databaseConnect(db);
    // ... use raw handles ...
    LibSql.connectionDeinit(conn);
    LibSql.databaseDeinit(db);
}
```

## Benchmarks

Run with `./gradlew bench`. Measures per-operation latency (p50/p90/p99/p99.9) for:

- **INSERT**: 10,000 rows via prepared statement into an in-memory DB
- **SELECT**: single-row query on a small table (comparison point: ~190 ns Rust/libsql baseline per Turso blog)

Results printed to stdout. The benchmark runs outside the normal test cycle.

## Why FFM (Panama) over JNI?

- **No JNI glue code** — bindings are pure Java. No header files, `javah`, or `javac -h` step.
- **Memory safety** — `MemorySegment` access is bounds-checked; no silent buffer overruns.
- **Deterministic lifetimes** — `Arena` scopes memory allocation and reclaims it on close.
- **Performance** — FFM downcalls have near-zero overhead for struct-passing calls; JNI adds frame push/pop and object pinning cost per call. See [benchmarks](#benchmarks) for concrete numbers.
- **Maintainability** — struct layouts are declared in Java alongside the API; updating to a new libsql-c version is a single-file change.

## libsql-c Compatibility

This project tracks [tursodatabase/libsql-c](https://github.com/tursodatabase/libsql-c) as a git submodule.

- **Pinned version**: `v0.3.4` (commit `02caaa2`)
- **Support policy**: only the pinned tag is guaranteed to work. Tag bumps are explicit commits to this repo.

## GraalVM Native Image

`LibSqlNativeFeature` registers all FFM downcall descriptors at build time.

### Setup

Add to your native-image config:

```
--features=uk.co.rstl.libsql.LibSqlNativeFeature
--enable-native-access=ALL-UNNAMED
```

Or register via `META-INF/native-image/native-image.properties`:

```properties
Args = --features=uk.co.rstl.libsql.LibSqlNativeFeature --enable-native-access=ALL-UNNAMED
```

**Important**: The native library (`.dylib`/`.so`/`.dll`) must be co-located with the binary at runtime — it cannot be loaded from inside the JAR in native-image mode.

## Platform Support

| Platform | Status |
|----------|--------|
| darwin-aarch64 (macOS ARM) | ✅ CI tested |
| linux-amd64 (Linux x64) | ✅ CI tested |
| windows-amd64 (Windows x64) | ✅ CI tested |

## Building from Source

### Prerequisites

- JDK 25
- Rust stable toolchain (`rustup`)
- Git

### Build

```bash
# Clone with submodule, build native lib, compile Java, run tests
git clone --recurse-submodules https://github.com/Conorrr/libsql-java.git
cd libsql-java
./gradlew build

# Build native lib only
./gradlew buildLibsqlNative

# Run tests only (native lib must exist)
./gradlew test

# Run benchmarks
./gradlew bench

# Clean native outputs
./gradlew cleanNative
```

The native library is built from the pinned [libsql-c](https://github.com/tursodatabase/libsql-c) submodule (`v0.3.4`) and placed in `native/{os}-{arch}/`.

## Installation

Published to [GitHub Packages](https://github.com/Conorrr/libsql-java/packages). Also available as JARs from [GitHub Releases](https://github.com/Conorrr/libsql-java/releases).

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven { url = uri("https://maven.pkg.github.com/Conorrr/libsql-java") }
}

dependencies {
    implementation("io.github.conorrr:libsql-java:0.2.0")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    maven { url 'https://maven.pkg.github.com/Conorrr/libsql-java' }
}

dependencies {
    implementation 'io.github.conorrr:libsql-java:0.2.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/Conorrr/libsql-java</url>
    </repository>
</repositories>

<dependency>
    <groupId>io.github.conorrr</groupId>
    <artifactId>libsql-java</artifactId>
    <version>0.2.0</version>
</dependency>
```

## License

MIT
