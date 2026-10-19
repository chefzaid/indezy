package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One offer as a reader found it on its site, before {@link OfferNormalizer} checks and completes it.
 * A reader fills what the site states; text fields ({@code rateText}, {@code remoteText}) are parsed
 * when the site gives no structured value.
 */
@Value
@Builder(toBuilder = true)
public class ScrapedOffer {

    String externalId;
    String url;
    String title;
    String company;
    /** Plain text (see {@link HtmlText}). */
    String description;

    Integer rateMin;
    Integer rateMax;
    /** The rate as the site writes it ("TJM HT max 600 €"), read when no number is given. */
    String rateText;

    String city;
    String postalCode;
    String departmentName;
    String regionName;
    String countryCode;
    Double latitude;
    Double longitude;

    WorkMode workMode;
    Integer remoteDaysPerWeek;
    /** The remote policy as the site writes it ("2 jours de télétravail"), read when no mode is given. */
    String remoteText;

    Integer durationMonths;
    LocalDate startDate;
    Boolean startAsap;
    Seniority seniority;
    @Singular
    List<String> skills;

    /** When the site published the offer; {@code null} dates it when first seen. */
    Instant publishedAt;
    /** The site gives the publication day only. */
    boolean publishedDayOnly;
    Instant expiresAt;

    /** Whether the offer is open to freelancers; permanent or fixed-term jobs only are dropped. */
    @Builder.Default
    boolean freelance = true;
}
