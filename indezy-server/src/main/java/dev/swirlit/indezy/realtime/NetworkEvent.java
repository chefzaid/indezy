package dev.swirlit.indezy.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What a member's open pages hear about (see docs/features.md#member-network): only which conversation changed, or that a
 * notification came, never a message's text; the page then reads what changed as usual, with its own rights.
 *
 * @param type           {@code message} (sent, edited, deleted, reacted to, or the group changed), {@code read} (read by
 *                       one of its members), {@code typing} or {@code notice}
 * @param conversationId the conversation, for all but a notice
 * @param name           who is typing, by their first name, for {@code typing} alone
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NetworkEvent(String type, Long conversationId, String name) {

    public static final String MESSAGE = "message";
    public static final String READ = "read";
    public static final String TYPING = "typing";
    public static final String NOTICE = "notice";
}
