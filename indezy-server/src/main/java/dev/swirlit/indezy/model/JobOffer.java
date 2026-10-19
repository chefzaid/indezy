package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A freelance mission published on a job board, normalized by the offer collection
 * ({@code dev.swirlit.indezy.scraping}). Offers are shared by every workspace; what a workspace does
 * with one (saved, hidden, seen, imported) lives in {@link JobOfferState}.
 */
@Entity
@Table(name = "job_offers",
    uniqueConstraints = @UniqueConstraint(name = "uk_job_offers_site_external_id", columnNames = {"site", "external_id"}),
    indexes = {
        @Index(name = "idx_job_offers_published_at", columnList = "published_at"),
        @Index(name = "idx_job_offers_department", columnList = "department_code"),
        @Index(name = "idx_job_offers_publisher_key", columnList = "publisher_key"),
        @Index(name = "idx_job_offers_opening_key", columnList = "opening_key"),
        @Index(name = "idx_job_offers_copy_group", columnList = "copy_group")
    })
@Getter
@Setter
public class JobOffer extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "site", nullable = false, length = 40)
    private OfferSite site;

    /** The offer's identifier on its site. */
    @Column(name = "external_id", nullable = false, length = 191)
    private String externalId;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    /** Who published the offer: the intermediary (ESN, agency) or the client. */
    @Column(name = "company", length = 200)
    private String company;

    /** Plain text: the site's HTML is never stored. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "rate_min")
    private Integer rateMin;

    @Column(name = "rate_max")
    private Integer rateMax;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "department_code", length = 3)
    private String departmentCode;

    @Column(name = "region_code", length = 2)
    private String regionCode;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", length = 20)
    private WorkMode workMode;

    @Column(name = "remote_days_per_week")
    private Integer remoteDaysPerWeek;

    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "start_asap")
    private Boolean startAsap;

    @Enumerated(EnumType.STRING)
    @Column(name = "seniority", length = 20)
    private Seniority seniority;

    @ElementCollection
    @CollectionTable(name = "job_offer_skills", joinColumns = @JoinColumn(name = "offer_id"))
    @OrderColumn(name = "position")
    @Column(name = "skill", nullable = false, length = 60)
    @BatchSize(size = 100)
    private List<String> skills = new ArrayList<>();

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_precision", nullable = false, length = 20)
    private OfferDatePrecision datePrecision;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Folded title, company, skills, place and description for keyword search. */
    @Column(name = "search_text", columnDefinition = "TEXT")
    private String searchText;

    /** Title and publisher, hashed: one of the two keys that tell copies on other sites (see OfferFingerprint). */
    @Column(name = "publisher_key", length = 64)
    private String publisherKey;

    /** Title and opening of the text, hashed: the other key; {@code null} when the text is too short. */
    @Column(name = "opening_key", length = 64)
    private String openingKey;

    /** The offers of other sites that publish the same mission share this group; {@code null} for an offer alone. */
    @Column(name = "copy_group")
    private Long copyGroup;

    /**
     * Takes what a new reading of the offer says. When Indezy first saw it is kept, and so is the
     * publication date when the site gives none (a first-seen date never moves).
     */
    public void updateFrom(JobOffer collected) {
        url = collected.url;
        title = collected.title;
        company = collected.company;
        description = collected.description;
        rateMin = collected.rateMin;
        rateMax = collected.rateMax;
        city = collected.city;
        postalCode = collected.postalCode;
        departmentCode = collected.departmentCode;
        regionCode = collected.regionCode;
        countryCode = collected.countryCode;
        latitude = collected.latitude;
        longitude = collected.longitude;
        workMode = collected.workMode;
        remoteDaysPerWeek = collected.remoteDaysPerWeek;
        durationMonths = collected.durationMonths;
        startDate = collected.startDate;
        startAsap = collected.startAsap;
        seniority = collected.seniority;
        // Changed in place, and only when it differs, so Hibernate does not rewrite every skill row.
        if (skills == null) {
            skills = new ArrayList<>();
        }
        if (!skills.equals(collected.skills)) {
            skills.clear();
            skills.addAll(collected.skills);
        }
        expiresAt = collected.expiresAt;
        searchText = collected.searchText;
        publisherKey = collected.publisherKey;
        openingKey = collected.openingKey;
        lastSeenAt = collected.lastSeenAt;
        if (collected.datePrecision != OfferDatePrecision.FIRST_SEEN) {
            publishedAt = collected.publishedAt;
            datePrecision = collected.datePrecision;
        }
    }

    public List<String> getSkills() {
        return skills != null ? new ArrayList<>(skills) : new ArrayList<>();
    }

    public void setSkills(List<String> skills) {
        this.skills = skills != null ? new ArrayList<>(skills) : new ArrayList<>();
    }
}
