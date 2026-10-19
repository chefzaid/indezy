package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A job offer as the feed shows it, with what the caller's workspace did with it. The list carries an
 * excerpt; {@code description} is filled only when one offer is asked for.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferDto(
    Long id,
    OfferSite site,
    String siteName,
    String url,
    String title,
    String company,
    String excerpt,
    String description,
    Integer rateMin,
    Integer rateMax,
    String city,
    String postalCode,
    String departmentCode,
    String departmentName,
    String regionName,
    String countryCode,
    Integer distanceKm,
    WorkMode workMode,
    Integer remoteDaysPerWeek,
    Integer durationMonths,
    LocalDate startDate,
    Boolean startAsap,
    Seniority seniority,
    List<String> skills,
    Instant publishedAt,
    OfferDatePrecision datePrecision,
    Instant expiresAt,
    boolean expired,
    boolean saved,
    boolean hidden,
    boolean seen,
    Long importedProjectId) {

    public OfferDto {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }
}
