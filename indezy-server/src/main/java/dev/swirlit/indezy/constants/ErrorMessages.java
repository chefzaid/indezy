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

    public static final String ACTIVITY_REPORT_NOT_FOUND = "No activity report for project %d in %s";
    public static final String ACTIVITY_REPORT_MISSION_REQUIRED = "Activity reports can only be created for an accepted mission";
    public static final String ACTIVITY_REPORT_LOCKED = "The activity report is submitted or validated; reopen it to change it";
    public static final String ACTIVITY_REPORT_NOT_SUBMITTED = "The activity report is not waiting for validation";
    public static final String ACTIVITY_REPORT_DAY_OUTSIDE_MONTH = "Every day must belong to the report's month";
    public static final String ACTIVITY_REPORT_DUPLICATE_DAY = "A day can be recorded only once";
    public static final String ACTIVITY_REPORT_REJECTION_REASON_REQUIRED = "A rejection needs a reason";
    public static final String ACTIVITY_REPORT_LINK_INVALID = "This validation link is invalid or has expired";

    public static final String OFFER_NOT_FOUND = "Job offer not found with id: %d";
    public static final String OFFER_ALREADY_IMPORTED = "This offer is already in the pipeline as project %d";
    public static final String OFFER_DISTANCE_NEEDS_CITY = "Add your city to your profile to filter offers by distance";
    public static final String WORKSPACE_REQUIRED = "This action needs a workspace";

    public static final String INVALID_DATE_RANGE = "Start date cannot be after end date";

    public static final String USER_NOT_FOUND = "User not found with id: %d";
}
