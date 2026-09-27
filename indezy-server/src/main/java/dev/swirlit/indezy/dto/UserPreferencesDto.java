package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.util.WholeNumberDeserializer;
import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import tools.jackson.databind.annotation.JsonDeserialize;

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
}
