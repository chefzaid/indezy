package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A message a freelancer writes again and again (a follow-up, a rate proposal, an availability announcement), with
 * variables such as {@code {{firstName}}} that the interface fills from the opportunity and the recipient.
 */
@Entity
@Table(name = "email_templates")
@Getter
@Setter
public class EmailTemplate extends BaseEntity {

    public static final int NAME_MAX = 100;
    public static final int SUBJECT_MAX = 200;
    public static final int BODY_MAX = 5000;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @Column(name = "subject", nullable = false, length = SUBJECT_MAX)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;
}
