package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.CommuteUnavailable;
import dev.swirlit.indezy.model.enums.TravelMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A mission's commute from home (see docs/features.md#commute-time-sorting): one trip's time and distance, the days on
 * site a month by its work mode, and the time it costs a month, there and back; or why it has no time.
 */
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
    /** One trip at rush hour (a weekday at 8:30), when known: the monthly commute counts it. */
    private Integer rushDurationInSeconds;
    private Integer distanceInMeters;
    private String distanceText;
    /** A fully remote mission: no commute, nothing looked up. */
    private boolean remote;
    /** Days on site a month: as the mission states them, else 20 on site and 10 hybrid. */
    private Integer onsiteDaysPerMonth;
    /** The commute's time a month, there and back on each day on site, at rush hour when known; 0 for a remote mission. */
    private Integer monthlySeconds;
    /** Estimated from the straight line between the two addresses, without Google: no road was looked up. */
    private boolean estimated;
    /** Whether the trip at rush hour takes longer than the profile's longest for its mode; null without both. */
    private Boolean beyondMaximum;
    /** Why there is no time, when there is none. */
    private CommuteUnavailable unavailable;
}
