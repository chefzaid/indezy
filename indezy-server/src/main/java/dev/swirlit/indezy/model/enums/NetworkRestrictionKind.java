package dev.swirlit.indezy.model.enums;

/** How a member keeps another at a distance. */
public enum NetworkRestrictionKind {
    /** Their posts are hidden from the member, who still follows them. */
    MUTE,
    /** No follow, no message, no post seen, both ways; the blocked member no longer finds the member's page. */
    BLOCK
}
