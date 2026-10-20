package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.OfferApplication;
import dev.swirlit.indezy.model.enums.OfferSite;

import java.time.Instant;

/** An offer the workspace acted on, as the account data export lists it. */
public record OfferStateExportDto(OfferSite site, String title, String company, String url, boolean saved, boolean hidden,
                                  Instant seenAt, Long importedProjectId, String note, OfferApplication application) {
}
