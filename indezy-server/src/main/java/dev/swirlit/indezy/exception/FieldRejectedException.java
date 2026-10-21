package dev.swirlit.indezy.exception;

/**
 * A request refused for one of its fields once checked against stored data, such as a wrong current password. It is
 * answered as a validation error on that field, whose name is what clients translate, and logged by that name only.
 */
public class FieldRejectedException extends RuntimeException {

    private final String field;

    public FieldRejectedException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
