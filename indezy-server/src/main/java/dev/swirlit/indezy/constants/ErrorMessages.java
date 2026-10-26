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
    public static final String OFFER_PUBLISHER_NOT_FOUND = "No offer of this publisher is collected";
    public static final String OFFER_ALREADY_IMPORTED = "This offer is already in the pipeline as project %d";
    public static final String OFFER_ALREADY_APPLIED = "You already applied to this offer on %s";
    public static final String OFFER_APPLIED_LATER = "An application cannot be dated after today";
    public static final String OFFER_DISTANCE_NEEDS_CITY = "Add your city to your profile to filter offers by distance";
    public static final String OFFER_COLLECTION_OFF = "Offer collection is off on this server";
    public static final String OFFER_REFRESH_TOO_SOON = "%s is read on request at most once every 15 minutes, and not while a reading runs";
    public static final String OFFER_SITE_NOT_TO_VISIT = "%s is not one of the sites to visit";
    public static final String OFFER_LINK_INVALID = "This is not the address of a web page";
    public static final String OFFER_LINK_READS_LIMIT = "Offers are read from their link at most %d times an hour";
    public static final String OFFER_PUBLISHER_NAME_REQUIRED = "A publisher's name needs a letter or a digit";
    public static final String OFFER_PUBLISHER_BLOCKS_LIMIT = "A workspace blocks at most %d publishers";
    public static final String OFFER_PUBLISHER_BLOCK_NOT_FOUND = "Blocked publisher not found with id: %d";
    public static final String SAVED_SEARCH_NOT_FOUND = "Saved search not found with id: %d";
    public static final String SAVED_SEARCH_NAME_TAKEN = "A saved search is already named \"%s\"";
    public static final String SAVED_SEARCHES_LIMIT = "A workspace keeps at most %d saved searches";
    public static final String PIPELINE_TEMPLATE_NOT_FOUND = "Pipeline template not found with id: %d";
    public static final String PIPELINE_TEMPLATE_NAME_TAKEN = "A pipeline template is already named \"%s\"";
    public static final String PIPELINE_TEMPLATES_LIMIT = "A workspace keeps at most %d pipeline templates";
    public static final String PIPELINE_TEMPLATE_WITHOUT_STEPS = "The opportunity has no steps to save as a template";
    public static final String SAVED_SEARCH_EMAIL_LINK_INVALID = "This email link is no longer valid";
    public static final String WORKSPACE_REQUIRED = "This action needs a workspace";

    public static final String INVALID_DATE_RANGE = "Start date cannot be after end date";

    public static final String USER_NOT_FOUND = "User not found with id: %d";
}
