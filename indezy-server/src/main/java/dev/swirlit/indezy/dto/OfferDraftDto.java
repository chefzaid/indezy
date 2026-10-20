package dev.swirlit.indezy.dto;

import lombok.Value;

/**
 * The opportunity an offer would make, not created yet: {@code project}'s client and source are set when the workspace
 * already has the offer's publisher and site, which {@code clientName} and {@code sourceName} name in any case.
 */
@Value
public class OfferDraftDto {
    ProjectDto project;
    String clientName;
    String sourceName;
}
