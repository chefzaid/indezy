package dev.byteworks.indezy.constants;

/**
 * Constants for error messages used throughout the application.
 * This helps maintain consistency and makes internationalization easier.
 */
public final class ErrorMessages {

    private ErrorMessages() {
        // Utility class - prevent instantiation
    }

    // Freelance related error messages
    public static final String FREELANCE_NOT_FOUND = "Freelance not found with id: %d";
    public static final String FREELANCE_EMAIL_EXISTS = "Freelance already exists with email: %s";
    public static final String FREELANCE_EMAIL_NOT_FOUND = "Freelance not found with email: %s";

    // Client related error messages
    public static final String CLIENT_NOT_FOUND = "Client not found with id: %d";
    public static final String CLIENT_NAME_EXISTS = "Client already exists with name: %s";

    // Project related error messages
    public static final String PROJECT_NOT_FOUND = "Project not found with id: %d";

    // Source related error messages
    public static final String SOURCE_NOT_FOUND = "Source not found with id: %d";
    public static final String SOURCE_NAME_EXISTS = "Source already exists with name: %s";

    // Contact related error messages
    public static final String CONTACT_NOT_FOUND = "Contact not found with id: %d";

    // Interview Step related error messages
    public static final String INTERVIEW_STEP_NOT_FOUND = "Interview step not found with id: %d";

    // Validation error messages
    public static final String INVALID_EMAIL_FORMAT = "Invalid email format: %s";
    public static final String INVALID_PHONE_FORMAT = "Invalid phone format: %s";
    public static final String INVALID_DATE_RANGE = "Start date cannot be after end date";
    public static final String INVALID_RATING_RANGE = "Rating must be between 1 and 5";
    public static final String INVALID_DAILY_RATE = "Daily rate must be positive";

    // Security related error messages
    public static final String ACCESS_DENIED = "Access denied to resource";
    public static final String AUTHENTICATION_FAILED = "Authentication failed";
    public static final String INVALID_TOKEN = "Invalid or expired token";

    // File related error messages
    public static final String FILE_NOT_FOUND = "File not found: %s";
    public static final String FILE_UPLOAD_FAILED = "Failed to upload file: %s";
    public static final String INVALID_FILE_TYPE = "Invalid file type. Allowed types: %s";
    public static final String FILE_SIZE_EXCEEDED = "File size exceeds maximum allowed size of %d MB";
}
