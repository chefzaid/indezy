package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.swirlit.indezy.model.enums.OfferLinkStatus;

import java.time.Instant;
import java.util.List;

/**
 * What the link of an offer gave. {@code offer} comes with {@link OfferLinkStatus#FOUND}, with the opportunity it would
 * make ({@code draft}), and with {@link OfferLinkStatus#IN_PIPELINE}, with the opportunity it made ({@code projectId}).
 * {@code retryAt} says when to look again while the site is being read, or when it can be read on request again;
 * {@code sites} names the sites the feed reads when the link is on none of them.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferLinkDto(OfferLinkStatus status, String siteName, OfferDto offer, Long projectId, OfferDraftDto draft,
                           Instant retryAt, List<String> sites) {

    public OfferLinkDto {
        sites = sites == null ? null : List.copyOf(sites);
    }
}
