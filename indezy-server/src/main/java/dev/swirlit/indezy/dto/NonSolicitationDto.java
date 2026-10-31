package dev.swirlit.indezy.dto;

import java.time.LocalDate;

/**
 * An opportunity for the end client of a mission signed through an ESN, offered directly or by another ESN while that
 * ESN's non-solicitation clause still runs (see docs/features.md#non-solicitation-clauses); {@code endsOn} is absent while
 * the mission runs open-ended.
 */
public record NonSolicitationDto(Long projectId, Long missionId, String missionRole, String middlemanName, String clientName,
                                 int months, LocalDate endsOn) {
}
