package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * How a help article's readers answered **Was this helpful?** on a day: counts only, anonymous, whoever reads the help
 * (with an account or not).
 */
@Entity
@Table(name = "help_feedback_days", uniqueConstraints = @UniqueConstraint(name = "uk_help_feedback_day", columnNames = {"slug", "answered_on"}))
@Getter
@Setter
public class HelpFeedbackDay extends BaseEntity {

    @Column(name = "slug", nullable = false, length = 40)
    private String slug;

    @Column(name = "answered_on", nullable = false)
    private LocalDate day;

    @Column(name = "helpful", nullable = false)
    private int helpful;

    @Column(name = "unhelpful", nullable = false)
    private int unhelpful;
}
