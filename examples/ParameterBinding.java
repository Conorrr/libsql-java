import uk.co.rstl.libsql.*;

/**
 * Demonstrates positional (?) and named (:param) parameter binding.
 */
public class ParameterBinding {
    public static void main(String[] args) {
        try (var db = Database.open(":memory:");
             var conn = db.connect()) {

            conn.batch("CREATE TABLE products(id INTEGER, name TEXT, price REAL, data BLOB)");

            // Positional binding with all value types
            try (var stmt = conn.prepare("INSERT INTO products VALUES (?, ?, ?, ?)")) {
                stmt.bind(1L).bind("Widget").bind(9.99).bind(new byte[]{0x01, 0x02});
                stmt.execute();
                stmt.reset();
                stmt.bind(2L).bind("Gadget").bind(19.99).bindNull();
                stmt.execute();
            }

            // Named binding
            try (var stmt = conn.prepare("INSERT INTO products VALUES (:id, :name, :price, :data)")) {
                stmt.bind(":id", 3L)
                    .bind(":name", "Doohickey")
                    .bind(":price", 29.99)
                    .bindNull(":data");
                stmt.execute();
            }

            // Query all
            try (var stmt = conn.prepare("SELECT id, name, price, data FROM products ORDER BY id");
                 var rows = stmt.query()) {
                for (var row : rows) {
                    System.out.printf("  %d: %s ($%.2f) data=%s%n",
                            row.getLong(0),
                            row.getString(1),
                            row.getDouble(2),
                            row.isNull(3) ? "null" : "byte[" + row.getBlob(3).length + "]");
                    row.close();
                }
            }
        }
    }
}
