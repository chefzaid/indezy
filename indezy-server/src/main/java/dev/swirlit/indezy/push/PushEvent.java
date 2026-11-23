package dev.swirlit.indezy.push;

/** What web push tells, each turned off or on by the account (Profile → Notifications). */
public enum PushEvent {
    /** The network's notifications: a reaction, a comment, a follower, a mention. */
    NETWORK,
    /** A new message in a conversation. */
    MESSAGES,
    /** An interview starting within the hour. */
    INTERVIEWS,
    /** The follow-ups due, each weekday morning. */
    FOLLOW_UPS,
    /** New offers for the saved searches. */
    OFFERS
}
