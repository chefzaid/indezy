package dev.swirlit.indezy.ai;

/** What an AI request was for, as its usage is counted. */
public enum AiFeature {
    /** A job posting read into an opportunity's fields. */
    POSTING,
    /** A message to a contact drafted or polished. */
    MESSAGE,
    /** An opportunity weighed against the profile. */
    FIT,
    /** A conversation about an opportunity. */
    CHAT,
    /** A feed offer's verdict and deal-breakers. */
    TRIAGE
}
