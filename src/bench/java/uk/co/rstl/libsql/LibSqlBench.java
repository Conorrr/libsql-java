package uk.co.rstl.libsql;

import java.util.Arrays;

/**
 * Simple latency benchmark for libsql-java FFM bindings.
 * <p>
 * Measures:
 *   1. Per-insert latency: 10,000 rows via prepared statement
 *   2. Single SELECT latency on a small result set
 * <p>
 * Reports p50, p90, p99, p99.9 percentiles.
 */
public class LibSqlBench {

    private static final int INSERT_COUNT = 10_000;
    private static final int SELECT_WARMUP = 1_000;
    private static final int SELECT_ITERATIONS = 10_000;

    public static void main(String[] args) {
        System.out.println("libsql-java benchmark");
        System.out.println("=====================\n");

        benchInsert();
        System.out.println();
        benchSelect();
    }

    private static void benchInsert() {
        long[] latencies = new long[INSERT_COUNT];

        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE bench(id INTEGER PRIMARY KEY, value TEXT)");

            try (var stmt = conn.prepare("INSERT INTO bench VALUES (?, ?)")) {
                for (int i = 0; i < INSERT_COUNT; i++) {
                    long start = System.nanoTime();
                    stmt.bind((long) i).bind("row-" + i);
                    stmt.execute();
                    latencies[i] = System.nanoTime() - start;
                    stmt.reset();
                }
            }
        }

        System.out.println("INSERT benchmark (" + INSERT_COUNT + " rows)");
        printPercentiles(latencies);
    }

    private static void benchSelect() {
        long[] latencies = new long[SELECT_ITERATIONS];

        try (var db = Database.open(":memory:");
             var conn = db.connect()) {
            conn.batch("CREATE TABLE bench(id INTEGER PRIMARY KEY, name TEXT, score REAL)");
            conn.batch("INSERT INTO bench VALUES (1, 'Alice', 95.5), (2, 'Bob', 87.3), (3, 'Carol', 92.1)");

            // Warmup
            for (int i = 0; i < SELECT_WARMUP; i++) {
                try (var stmt = conn.prepare("SELECT id, name, score FROM bench WHERE id = ?")) {
                    stmt.bind(1L);
                    try (var rows = stmt.query()) {
                        var row = rows.next();
                        if (row != null) row.close();
                    }
                }
            }

            // Measured
            for (int i = 0; i < SELECT_ITERATIONS; i++) {
                long start = System.nanoTime();
                try (var stmt = conn.prepare("SELECT id, name, score FROM bench WHERE id = ?")) {
                    stmt.bind(1L);
                    try (var rows = stmt.query()) {
                        var row = rows.next();
                        if (row != null) row.close();
                    }
                }
                latencies[i] = System.nanoTime() - start;
            }
        }

        System.out.println("SELECT benchmark (" + SELECT_ITERATIONS + " iterations, 1-row result)");
        printPercentiles(latencies);
    }

    private static void printPercentiles(long[] latencies) {
        Arrays.sort(latencies);
        int n = latencies.length;

        long p50  = latencies[(int) (n * 0.50)];
        long p90  = latencies[(int) (n * 0.90)];
        long p99  = latencies[(int) (n * 0.99)];
        long p999 = latencies[Math.min((int) (n * 0.999), n - 1)];
        long min   = latencies[0];
        long max   = latencies[n - 1];

        System.out.printf("  %-8s %10s%n", "Metric", "Latency");
        System.out.printf("  %-8s %10s%n", "------", "-------");
        System.out.printf("  %-8s %10s%n", "min",    fmtNs(min));
        System.out.printf("  %-8s %10s%n", "p50",    fmtNs(p50));
        System.out.printf("  %-8s %10s%n", "p90",    fmtNs(p90));
        System.out.printf("  %-8s %10s%n", "p99",    fmtNs(p99));
        System.out.printf("  %-8s %10s%n", "p99.9",  fmtNs(p999));
        System.out.printf("  %-8s %10s%n", "max",    fmtNs(max));
    }

    private static String fmtNs(long ns) {
        if (ns < 1_000) return ns + " ns";
        if (ns < 1_000_000) return String.format("%.1f µs", ns / 1_000.0);
        return String.format("%.2f ms", ns / 1_000_000.0);
    }
}
