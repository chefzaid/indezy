package dev.swirlit.indezy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * A new validation link for an activity report. The token is returned only here, once: the server keeps its hash,
 * so a lost link is replaced by a new one.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityReportLinkDto {
    @ToString.Exclude
    private String token;
    private Instant expiresAt;
    private ActivityReportDto report;
}
