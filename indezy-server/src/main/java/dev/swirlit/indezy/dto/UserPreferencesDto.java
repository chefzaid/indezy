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
    private String dateFormat;
    private String timeFormat;
    private String currency;
    private String timezone;
    private String defaultView;
    private Integer itemsPerPage;
    private Boolean autoSave;
    @Min(1)
    @Max(365)
    @JsonDeserialize(using = WholeNumberDeserializer.class)
    private Integer inactivityDays;
    /** Days a message sent to a contact waits for an answer before the dashboard says to follow up. */
    @Min(1)
    @Max(60)
    @JsonDeserialize(using = WholeNumberDeserializer.class)
    private Integer followUpDays;
    /** Archive opportunities idle for {@code inactivityDays}, every night. */
    private Boolean autoArchive;
    /** The dashboard's blocks in the account's order, shown or hidden; left as they are when absent. */
    @Size(max = 20)
    private List<@Valid DashboardWidgetDto> dashboardWidgets;
}
