package uk.co.rstl.libsql;

/** Unchecked exception thrown when a libsql native call fails. */
public class LibSqlException extends RuntimeException {

    /** Create an exception with the given error message. */
    public LibSqlException(String message) {
        super(message);
    }

    /** Create an exception with the given error message and cause. */
    public LibSqlException(String message, Throwable cause) {
        super(message, cause);
    }
}
