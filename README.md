# libsql-java

Java FFM (Panama) bindings for [libsql-c](https://github.com/tursodatabase/libsql-c) — the C interface to [libSQL](https://github.com/tursodatabase/libsql).

## Requirements

- Java 22+ (FFM API is GA since JDK 22)
- `--enable-native-access=ALL-UNNAMED` JVM flag
- Native library for your platform (bundled in classifier JARs or built from source)

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("dev.libsql:libsql-java:0.1.0")
    // Platform-specific native library:
    runtimeOnly("dev.libsql:libsql-java:0.1.0:natives-darwin-aarch64")
}
```

### Maven

```xml
<dependency>
    <groupId>dev.libsql</groupId>
    <artifactId>libsql-java</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>dev.libsql</groupId>
    <artifactId>libsql-java</artifactId>
    <version>0.1.0</version>
    <classifier>natives-darwin-aarch64</classifier>
    <scope>runtime</scope>
</dependency>
```

## Quick Start

```java
import dev.libsql.LibSql;
import java.lang.foreign.Arena;

// Initialize once
LibSql.setup();

try (var arena = Arena.ofConfined()) {
    // Open in-memory database
    var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);
    var conn = LibSql.databaseConnect(db);

    // Create table and insert data
    LibSql.connectionBatch(arena, conn, "CREATE TABLE users(id INTEGER, name TEXT)");

    var stmt = LibSql.connectionPrepare(arena, conn, "INSERT INTO users VALUES (?, ?)");
    LibSql.bindValue(stmt, LibSql.integer(1));
    LibSql.bindValue(stmt, LibSql.text(arena, "Alice"));
    LibSql.statementExecute(stmt);
    LibSql.statementDeinit(stmt);

    // Query
    stmt = LibSql.connectionPrepare(arena, conn, "SELECT id, name FROM users");
    var rows = LibSql.statementQuery(stmt);
    var row = LibSql.rowsNext(rows);
    while (!LibSql.rowEmpty(row)) {
        Long id = (Long) LibSql.extractValue(LibSql.rowValue(row, 0));
        String name = (String) LibSql.extractValue(LibSql.rowValue(row, 1));
        System.out.println(id + ": " + name);
        LibSql.rowDeinit(row);
        row = LibSql.rowsNext(rows);
    }
    LibSql.rowsDeinit(rows);
    LibSql.statementDeinit(stmt);

    // Cleanup
    LibSql.connectionDeinit(conn);
    LibSql.databaseDeinit(db);
}
```

## Usage Guide

### Opening Databases

```java
// In-memory
var db = LibSql.databaseInit(arena, ":memory:", null, null, 0);

// Local file
var db = LibSql.databaseInit(arena, "/path/to/db.sqlite", null, null, 0);

// Remote (Turso)
var db = LibSql.databaseInit(arena, null, "libsql://your-db.turso.io", "your-auth-token", 0);

// Synced replica (local + remote)
var db = LibSql.databaseInit(arena, "/path/to/replica.db", "libsql://your-db.turso.io", "your-auth-token", 60);
LibSql.databaseSync(db); // Pull latest from remote
```

### Prepared Statements

```java
// Positional binding
var stmt = LibSql.connectionPrepare(arena, conn, "INSERT INTO t VALUES (?, ?)");
LibSql.bindValue(stmt, LibSql.integer(1));
LibSql.bindValue(stmt, LibSql.text(arena, "hello"));
long rowsChanged = LibSql.statementExecute(stmt);

// Named binding
var stmt = LibSql.connectionPrepare(arena, conn, "INSERT INTO t VALUES (:id, :name)");
LibSql.bindNamed(arena, stmt, ":id", LibSql.integer(1));
LibSql.bindNamed(arena, stmt, ":name", LibSql.text(arena, "hello"));

// Reset and re-execute
LibSql.statementReset(stmt);
LibSql.bindValue(stmt, LibSql.integer(2));
LibSql.statementExecute(stmt);
```

### Transactions

```java
var tx = LibSql.connectionTransaction(conn);
try {
    LibSql.transactionBatch(arena, tx, "INSERT INTO t VALUES (1)");
    var stmt = LibSql.transactionPrepare(arena, tx, "INSERT INTO t VALUES (?)");
    LibSql.bindValue(stmt, LibSql.integer(2));
    LibSql.statementExecute(stmt);
    LibSql.statementDeinit(stmt);
    LibSql.transactionCommit(tx);
} catch (Exception e) {
    LibSql.transactionRollback(tx);
    throw e;
}
```

### Value Types

| Method | Java Type | SQLite Type |
|--------|-----------|-------------|
| `LibSql.integer(long)` | `Long` | INTEGER |
| `LibSql.real(double)` | `Double` | REAL |
| `LibSql.text(arena, String)` | `String` | TEXT |
| `LibSql.blob(arena, byte[])` | `byte[]` | BLOB |
| `LibSql.nullValue()` | `null` | NULL |

`extractValue()` returns the corresponding Java type (or `null`).

### Batch Operations

```java
LibSql.connectionBatch(arena, conn,
    "CREATE TABLE t(v INTEGER); INSERT INTO t VALUES (1); INSERT INTO t VALUES (2)");
```

## GraalVM Native Image

`LibSqlNativeFeature` registers all FFM downcall descriptors at build time.

### Setup

Add to your native-image config:

```
--features=dev.libsql.LibSqlNativeFeature
--enable-native-access=ALL-UNNAMED
```

Or register via `META-INF/native-image/native-image.properties`:

```properties
Args = --features=dev.libsql.LibSqlNativeFeature --enable-native-access=ALL-UNNAMED
```

**Important**: The native library (`.dylib`/`.so`/`.dll`) must be co-located with the binary at runtime — it cannot be loaded from inside the JAR in native-image mode.

## Platform Support

| Platform | Status |
|----------|--------|
| darwin-aarch64 (macOS ARM) | ✅ CI tested |
| linux-amd64 (Linux x64) | ✅ CI tested |
| windows-amd64 (Windows x64) | ✅ CI tested |
| darwin-amd64 (macOS x64) | 🔨 Planned |
| linux-aarch64 (Linux ARM) | 🔨 Planned |
| linux-musl-amd64 | 🔨 Planned |
| linux-musl-aarch64 | 🔨 Planned |

## Building from Source

### Prerequisites

- JDK 22+
- Rust stable toolchain (`rustup`)
- Git

### Build

```bash
# Clone, build native lib, compile Java, run tests
./gradlew build

# Build native lib only
./gradlew buildLibsqlNative

# Run tests only (native lib must exist)
./gradlew test
```

The native library is built from [tursodatabase/libsql-c](https://github.com/tursodatabase/libsql-c) (cloned automatically) and placed in `native/{os}-{arch}/`.

## License

MIT
