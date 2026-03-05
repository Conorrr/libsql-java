package uk.co.rstl.libsql;

import java.util.logging.Logger;

/**
 * Logs a warning when a native resource is reclaimed by the {@link java.lang.ref.Cleaner}
 * instead of being explicitly closed. Uses SLF4J if available, otherwise falls back to
 * {@code java.util.logging}.
 */
final class ResourceCleaner {

    private static final boolean SLF4J_AVAILABLE;
    private static final Object SLF4J_LOGGER;
    private static final Logger JUL_LOGGER;

    static {
        boolean available;
        Object logger = null;
        try {
            Class<?> factory = Class.forName("org.slf4j.LoggerFactory");
            logger = factory.getMethod("getLogger", Class.class).invoke(null, ResourceCleaner.class);
            available = true;
        } catch (Throwable t) {
            available = false;
        }
        SLF4J_AVAILABLE = available;
        SLF4J_LOGGER = logger;
        JUL_LOGGER = available ? null : Logger.getLogger(ResourceCleaner.class.getName());
    }

    private ResourceCleaner() {}

    /**
     * Log a warning that a resource was not explicitly closed.
     *
     * @param resourceType the simple class name of the leaked resource (e.g. "Connection")
     */
    static void warn(String resourceType) {
        String msg = "libsql-java: " + resourceType + " was not closed explicitly and was cleaned up by GC. " +
                "Use try-with-resources to avoid native memory leaks.";
        if (SLF4J_AVAILABLE) {
            try {
                SLF4J_LOGGER.getClass().getMethod("warn", String.class).invoke(SLF4J_LOGGER, msg);
            } catch (Throwable t) {
                // Should not happen, but fall through silently
            }
        } else {
            JUL_LOGGER.warning(msg);
        }
    }
}
