package dev.swirlit.indezy.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** What the client or ESN sees through a validation link: the mission, the consultant and the month's report. */
@Data
@NoArgsConstructor
public class PublicActivityReportDto {
    @ToString.Exclude
    private String consultantName;
    private String clientName;
    private String middlemanName;
    private String missionRole;
    /** Whether the client's template shows the on-site / remote split. */
    private boolean showsLocation;
    private ActivityReportDto report;
}
