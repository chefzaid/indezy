package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.InboxEmailAction;
import dev.swirlit.indezy.model.enums.InboxEmailKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An email the member forwarded to their inbox watcher address (see docs/features.md#inbox-watcher): who sent it, its
 * subject, what the AI read in it and what was done, so the member sees it and a webhook delivered twice counts once.
 * Its text is not kept.
 */
@Entity
@Table(name = "inbox_emails", indexes = @Index(name = "idx_inbox_emails_freelance", columnList = "freelance_id"))
@Getter
@Setter
public class InboxEmail extends BaseEntity {

    public static final int TEXT_MAX = 200;

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    /** Resend's id of the received email. */
    @Column(name = "email_id", nullable = false, unique = true, length = 100)
    private String emailId;

    @Column(name = "sender", length = TEXT_MAX)
    private String sender;

    @Column(name = "subject", length = TEXT_MAX)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private InboxEmailKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private InboxEmailAction action;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "project_id")
    private Long projectId;
}
