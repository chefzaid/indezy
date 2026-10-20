package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ApplicationChannel;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * The workspace applied to an offer on {@code appliedOn} (today at the latest, Paris time) through {@code via}; with
 * {@code followUpInDays}, a follow-up is planned that many days later. The pipeline's steps are titled in
 * {@code language} (English when absent).
 */
public record OfferApplicationRequest(
    @NotNull LocalDate appliedOn,
    @NotNull ApplicationChannel via,
    @Min(1) @Max(OfferApplicationRequest.MAX_FOLLOW_UP_DAYS) Integer followUpInDays,
    DocumentLanguage language) {

    public static final int MAX_FOLLOW_UP_DAYS = 60;
}
