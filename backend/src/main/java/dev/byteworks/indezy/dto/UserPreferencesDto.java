package dev.byteworks.indezy.dto;

import lombok.Data;

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
}
