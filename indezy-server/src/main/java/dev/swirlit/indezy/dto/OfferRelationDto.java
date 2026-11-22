package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

/**
 * What the workspace has to do with an offer's publisher (see docs/features.md#freelance-offer-feed): a mission won with it, as
 * the client or the ESN, and, when it is the end client of a mission signed through an ESN whose non-solicitation clause
 * still runs, that ESN and the clause's last day ({@code clauseUntil} absent while that mission runs open-ended); and
 * {@code unanswered}, the messages its contacts were sent and never answered, when they never answered any (at least
 * {@value dev.swirlit.indezy.service.ContactInteractionService#UNANSWERED_MIN}); and {@code endClient}, one of the
 * workspace's end clients the offer names, likely the one behind the ESN publishing it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferRelationDto(boolean workedWith, String clauseEsn, LocalDate clauseUntil, Integer unanswered, String endClient) {

    public OfferRelationDto(boolean workedWith, String clauseEsn, LocalDate clauseUntil) {
        this(workedWith, clauseEsn, clauseUntil, null, null);
    }

    public OfferRelationDto(boolean workedWith, String clauseEsn, LocalDate clauseUntil, Integer unanswered) {
        this(workedWith, clauseEsn, clauseUntil, unanswered, null);
    }

    /** The same relation, with the company's unanswered messages. */
    public OfferRelationDto unanswered(Integer count) {
        return new OfferRelationDto(workedWith, clauseEsn, clauseUntil, count, endClient);
    }

    /** The same relation, with the end client the offer likely hides. */
    public OfferRelationDto endClient(String name) {
        return new OfferRelationDto(workedWith, clauseEsn, clauseUntil, unanswered, name);
    }
}
