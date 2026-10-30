package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ActivityReportLayout;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import dev.swirlit.indezy.util.BillingText;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClientDto {
    private Long id;

    @NotBlank(message = "Company name is required")
    private String companyName;

    private String address;

    @NotBlank(message = "City is required")
    private String city;

    private String domain;

    @NotNull(message = "Final client status is required")
    private Boolean isFinal;

    private String notes;

    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    private Boolean isBlacklisted;

    private String blacklistReason;

    // Billing: what the client's invoices state of it
    @Pattern(regexp = "\\d{9}", message = "A SIREN has 9 digits")
    private String siren;

    @Pattern(regexp = "[A-Z]{2}[0-9A-Z]{2,13}", message = "A VAT number starts with its country code")
    private String vatNumber;

    // Activity report PDF template; null fields use the defaults
    @Size(max = 120, message = "Activity report title must be at most 120 characters")
    private String activityReportTitle;

    private ActivityReportLayout activityReportLayout;

    private Boolean activityReportShowsLocation;

    private DocumentLanguage activityReportLanguage;

    @Size(max = 500, message = "Activity report footer must be at most 500 characters")
    private String activityReportFooter;

    // Related entities
    @NotNull(message = "Freelance ID is required")
    private Long freelanceId;

    // Computed fields
    private Integer totalProjects;
    private Integer totalContacts;
    private Double averageDailyRate;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Spaces dropped, as people write a SIREN in groups ("123 456 789"); blank means none. */
    public void setSiren(final String siren) {
        this.siren = BillingText.compact(siren, false);
    }

    /** Spaces dropped and upper case, as a VAT number is compared; blank means none. */
    public void setVatNumber(final String vatNumber) {
        this.vatNumber = BillingText.compact(vatNumber, true);
    }
}
