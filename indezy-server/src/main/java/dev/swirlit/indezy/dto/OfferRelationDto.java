package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

/**
 * What the workspace has to do with an offer's publisher (see docs/features.md#freelance-offer-feed): a mission won with it, as
 * the client or the ESN, and, when it is the end client of a mission signed through an ESN whose non-solicitation clause
 * still runs, that ESN and the clause's last day ({@code clauseUntil} absent while that mission runs open-ended).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferRelationDto(boolean workedWith, String clauseEsn, LocalDate clauseUntil) {
}
