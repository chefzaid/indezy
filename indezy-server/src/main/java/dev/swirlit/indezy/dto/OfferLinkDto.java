package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.swirlit.indezy.model.enums.OfferLinkStatus;

import java.time.Instant;

/**
 * What the link of an offer gave. {@code offer} comes with {@link OfferLinkStatus#FOUND}, with the opportunity it would
 * make ({@code draft}), and with {@link OfferLinkStatus#IN_PIPELINE}, with the opportunity it made ({@code projectId});
 * a posting of a site the feed does not collect ({@link OfferLinkStatus#POSTING}) comes as its {@code draft} alone.
 * {@code retryAt} says when to look again while the site is being read, or when it can be read on request again.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferLinkDto(OfferLinkStatus status, String siteName, OfferDto offer, Long projectId, OfferDraftDto draft,
                           Instant retryAt) {
}
