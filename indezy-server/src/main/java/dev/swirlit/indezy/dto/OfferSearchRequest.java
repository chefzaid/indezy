package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Filters, sort and page of the offer feed, from the query string ({@code GET /offers}). */
@Data
public class OfferSearchRequest {

    /** Which offers: the feed (not hidden, still open, from a site the workspace keeps), the saved ones or the hidden ones. */
    public enum View { FEED, SAVED, HIDDEN }

    public enum Sort { NEWEST, RATE, DISTANCE, MATCH }

    /** Words that must all appear in the title, company, skills, place or description. */
    @Size(max = 200)
    private String q;

    @Min(0)
    @Max(10_000)
    private Integer rateMin;

    @Min(0)
    @Max(10_000)
    private Integer rateMax;

    /** Leave out offers that state no daily rate. */
    private boolean rateKnown;

    @Size(max = 20)
    private List<@Pattern(regexp = "\\d{2}") String> regions = new ArrayList<>();

    @Size(max = 101)
    private List<@Pattern(regexp = "\\d{2}|97[1-6]|2[AB]") String> departments = new ArrayList<>();

    /** Distance from the profile's city, in kilometres. */
    @Min(1)
    @Max(1000)
    private Integer radiusKm;

    @Size(max = 3)
    private List<WorkMode> workModes = new ArrayList<>();

    @Min(0)
    @Max(5)
    private Integer minRemoteDays;

    @Min(0)
    @Max(60)
    private Integer durationMin;

    @Min(0)
    @Max(60)
    private Integer durationMax;

    /** Missions starting as soon as possible or by this day. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startBefore;

    @Min(1)
    @Max(365)
    private Integer publishedWithinDays;

    @Size(max = 50)
    private List<OfferSite> sites = new ArrayList<>();

    @Size(max = 4)
    private List<Seniority> seniorities = new ArrayList<>();

    @Size(max = 10)
    private List<@Size(max = 60) String> skills = new ArrayList<>();

    private View view = View.FEED;

    /** Leave out offers the workspace already opened. */
    private boolean unseenOnly;

    /** Keep the offers that match the profile (see {@code OfferMatch#GOOD_MATCH}); ignored when the profile names no skills. */
    private boolean matchesProfile;

    private Sort sort = Sort.NEWEST;

    @Min(0)
    @Max(10_000)
    private int page;

    @Min(1)
    @Max(100)
    private int size = 20;
}
