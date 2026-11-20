package dev.swirlit.indezy.model.enums;

/** Where a message request stands: a conversation opened with a member neither follows, who answers it. */
public enum NetworkMessageRequestStatus {
    /** Waiting for the recipient's answer; its sender writes one message meanwhile. */
    PENDING,
    /** Accepted: both write as members who follow each other do. */
    ACCEPTED,
    /** Declined: hidden from the recipient; its sender is not told, and writes no more. */
    DECLINED
}
