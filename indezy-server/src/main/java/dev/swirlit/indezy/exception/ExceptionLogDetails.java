package dev.swirlit.indezy.exception;

import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Details of an exception that are safe to log. Exception and database messages can quote what the user sent, so only
 * where an exception was thrown, or the database constraint and SQL state involved, are described.
 */
public final class ExceptionLogDetails {

    static final String UNKNOWN = "unknown";

    private ExceptionLogDetails() {
    }

    /** The class, method and line that threw the exception. */
    public static String originOf(Throwable ex) {
        StackTraceElement[] trace = ex.getStackTrace();
        if (trace.length == 0) {
            return UNKNOWN;
        }
        return trace[0].getClassName() + "." + trace[0].getMethodName() + ":" + trace[0].getLineNumber();
    }

    /** The constraint name and SQL state, never the database message, which quotes the conflicting values. */
    public static String describe(DataIntegrityViolationException ex) {
        String constraint = ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException violation ?
            violation.getConstraintName() : null;
        String sqlState = ex.getMostSpecificCause() instanceof SQLException sqlException ? sqlException.getSQLState() : null;
        return "constraint " + Objects.requireNonNullElse(constraint, UNKNOWN) +
            ", SQL state " + Objects.requireNonNullElse(sqlState, UNKNOWN);
    }
}
