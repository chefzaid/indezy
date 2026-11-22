package dev.swirlit.indezy.model;

import dev.swirlit.indezy.ai.AiFeature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** What an account's AI requests used in a month, by feature and by whose key paid: tokens, requests and the cost. */
@Entity
@Table(name = "ai_usage", uniqueConstraints = @UniqueConstraint(name = "uk_ai_usage", columnNames = {"user_id", "usage_month", "feature", "own_key"}))
@Getter
@Setter
public class AiUsage extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** "2026-11". */
    @Column(name = "usage_month", nullable = false, length = 7)
    private String month;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 20)
    private AiFeature feature;

    /** Paid by the member's own key, which no limit applies to. */
    @Column(name = "own_key", nullable = false)
    private boolean ownKey;

    @Column(name = "requests", nullable = false)
    private long requests;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    /** In millionths of a US dollar. */
    @Column(name = "cost_micros", nullable = false)
    private long costMicros;
}
