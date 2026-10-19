package dev.swirlit.indezy.model.enums;

/** What a day of an activity report records: work on the client's premises, remote work, or an absence. */
public enum ActivityDayType {
    ONSITE,
    REMOTE,
    ABSENCE;

    public boolean isWorked() {
        return this != ABSENCE;
    }
}
