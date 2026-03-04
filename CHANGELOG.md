# Changelog

## [0.1.0] - Unreleased

### Added
- Java FFM (Panama) bindings for libsql-c
- `LibSql` — full API: setup, database init/sync/connect, prepared statements, batch, transactions, rows, value types
- `LibSqlLoader` — native library loading (co-located, classpath, dev mode)
- `LibSqlNativeFeature` — GraalVM native-image support
- `LibSqlException` — error handling
- Unit tests for all core functionality
- CI/CD workflows for macOS ARM, Linux x64, Windows x64
- Gradle build with automatic native library compilation
