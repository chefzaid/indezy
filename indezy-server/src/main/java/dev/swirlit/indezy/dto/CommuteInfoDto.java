package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.TravelMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommuteInfoDto {
    private Long projectId;
    private String projectRole;
    private String clientName;
    private String origin;
    private String destination;
    private TravelMode travelMode;
    private Integer durationInSeconds;
    private String durationText;
    private Integer distanceInMeters;
    private String distanceText;
}
