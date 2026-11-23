package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.util.WholeNumberDeserializer;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@Data
public class UserPreferencesDto {
    private String theme;
    private String language;
    @Min(1)
    @Max(365)
    @JsonDeserialize(using = WholeNumberDeserializer.class)
    private Integer inactivityDays;
    /** Days a message sent to a contact waits for an answer before the dashboard says to follow up. */
    @Min(1)
    @Max(60)
    @JsonDeserialize(using = WholeNumberDeserializer.class)
    private Integer followUpDays;
    /** Years without exchange after which contacts are anonymized every night; 0 for never. */
    @Min(0)
    @Max(10)
    @JsonDeserialize(using = WholeNumberDeserializer.class)
    private Integer contactRetentionYears;
    /** Archive opportunities idle for {@code inactivityDays}, every night. */
    private Boolean autoArchive;
    /** Let the won missions' rates count, anonymized, in the network's rate benchmark. */
    private Boolean shareRates;
    /** Let the records of the ESNs dealt with count, anonymized, in the ESN scorecards across members; on by default. */
    private Boolean shareEsnRecords;
    /** Triage the new offers that fit overnight, from the AI allowance; off by default. */
    private Boolean autopilot;
    /** The dashboard's blocks in the account's order, shown or hidden; left as they are when absent. */
    @Size(max = 20)
    private List<@Valid DashboardWidgetDto> dashboardWidgets;
}
