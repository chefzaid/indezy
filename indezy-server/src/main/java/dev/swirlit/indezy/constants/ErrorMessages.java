package dev.swirlit.indezy.constants;

/** Error messages returned by the API, kept in one place for consistency. */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    public static final String FREELANCE_NOT_FOUND = "Freelance not found with id: %d";

    public static final String CLIENT_NOT_FOUND = "Client not found with id: %d";
    public static final String CLIENT_HAS_PROJECTS = "Client %d is still linked to projects";

    public static final String PROJECT_NOT_FOUND = "Project not found with id: %d";
    public static final String PROJECT_CLIENT_REQUIRED = "A project must be linked to a client";

    public static final String SOURCE_NOT_FOUND = "Source not found with id: %d";

    public static final String SEASON_NOT_FOUND = "Season not found with id: %d";

    public static final String CONTACT_NOT_FOUND = "Contact not found with id: %d";
    public static final String CONTACT_OWNER_REQUIRED = "A contact must be linked to a freelance and a client";

    public static final String PROJECT_NOTE_NOT_FOUND = "Project note not found with id: %d";

    public static final String INVALID_DATE_RANGE = "Start date cannot be after end date";

    public static final String USER_NOT_FOUND = "User not found with id: %d";
}
