package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.util.FrenchGeography;
import dev.swirlit.indezy.util.FrenchGeography.Department;
import dev.swirlit.indezy.util.TextFolding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Checks and completes what a reader found: drops offers closed to freelancers, without a title or a
 * web link, expired or older than the retention window, caps every text, reads rates and remote
 * policies written as text, and places the offer (department, region, coordinates). The result is a
 * detached {@link JobOffer} the store saves.
 */
@Component
@RequiredArgsConstructor
public class OfferNormalizer {

    static final int MAX_SKILLS = 30;
    static final int MAX_SKILL_LENGTH = 60;
    static final int MAX_DURATION_MONTHS = 60;
    private static final int SEARCHED_DESCRIPTION_LENGTH = 4_000;
    /** Where a mission's length is looked for when the site gives none. */
    private static final int DURATION_SEARCH_LENGTH = 2_000;
    private static final String PARIS = "75";

    private final GeoLocator geoLocator;
    private final OfferProperties properties;

    /** The offer ready to store, or empty when it must not enter the feed. */
    public Optional<JobOffer> normalize(OfferSite site, ScrapedOffer scraped, Instant now) {
        String title = cap(scraped.getTitle(), 300);
        String url = webLink(scraped.getUrl());
        String externalId = cap(scraped.getExternalId(), 191);
        if (!scraped.isFreelance() || OfferText.namesSalariedJob(title) || title == null || url == null || externalId == null ||
            isStale(scraped, now)) {
            return Optional.empty();
        }
        JobOffer offer = new JobOffer();
        offer.setSite(site);
        offer.setExternalId(externalId);
        offer.setUrl(url);
        offer.setTitle(title);
        offer.setCompany(cap(scraped.getCompany(), 200));
        String description = scraped.getDescription() == null ? null : scraped.getDescription().strip();
        offer.setDescription(description == null || description.isEmpty() ? null :
            description.substring(0, Math.min(description.length(), HtmlText.MAX_LENGTH)));
        applyRate(offer, scraped);
        applyRemote(offer, scraped);
        applyPlace(offer, scraped);
        Integer duration = durationOf(scraped, description);
        offer.setDurationMonths(duration != null && duration >= 1 && duration <= MAX_DURATION_MONTHS ? duration : null);
        offer.setStartDate(scraped.getStartDate());
        offer.setStartAsap(scraped.getStartAsap());
        offer.setSeniority(scraped.getSeniority());
        offer.setSkills(skills(scraped.getSkills()));
        applyDates(offer, scraped, now);
        offer.setSearchText(searchText(offer));
        offer.setPublisherKey(OfferFingerprint.publisherKey(site, offer.getTitle(), offer.getCompany()));
        offer.setOpeningKey(OfferFingerprint.openingKey(offer.getTitle(), offer.getDescription()));
        return Optional.of(offer);
    }

    private static void applyRate(JobOffer offer, ScrapedOffer scraped) {
        Integer min = validRate(scraped.getRateMin());
        Integer max = validRate(scraped.getRateMax());
        if (min == null && max == null) {
            OfferText.Rate parsed = OfferText.dailyRate(scraped.getRateText());
            min = parsed.min();
            max = parsed.max();
        }
        if (min != null && max != null && min > max) {
            Integer swap = min;
            min = max;
            max = swap;
        }
        offer.setRateMin(min);
        offer.setRateMax(max);
    }

    private static void applyRemote(JobOffer offer, ScrapedOffer scraped) {
        WorkMode mode = scraped.getWorkMode();
        Integer days = scraped.getRemoteDaysPerWeek();
        if (mode == null) {
            OfferText.Remote parsed = OfferText.remote(scraped.getRemoteText());
            mode = parsed.mode();
            days = days != null ? days : parsed.daysPerWeek();
        }
        if (days != null && (days < 0 || days > 5)) {
            days = null;
        }
        if (mode == WorkMode.REMOTE) {
            days = 5;
        } else if (mode == WorkMode.ONSITE) {
            days = 0;
        }
        offer.setWorkMode(mode);
        offer.setRemoteDaysPerWeek(days);
    }

    /**
     * Locates again a stored offer that names a place but has no coordinates (its lookup failed when it was read, or its
     * place name could not be read then); whether it has coordinates now. What it knows of its place is only completed.
     */
    public boolean relocate(JobOffer offer) {
        if (offer.getLatitude() != null || offer.getCity() == null && offer.getPostalCode() == null) {
            return false;
        }
        JobOffer located = new JobOffer();
        applyPlace(located, ScrapedOffer.builder()
            .city(offer.getCity())
            .postalCode(offer.getPostalCode())
            .countryCode(offer.getCountryCode())
            .departmentName(FrenchGeography.department(offer.getDepartmentCode()).map(Department::name).orElse(null))
            .regionName(FrenchGeography.region(offer.getRegionCode()).map(FrenchGeography.Region::name).orElse(null))
            .build());
        if (located.getLatitude() == null) {
            return false;
        }
        offer.setLatitude(located.getLatitude());
        offer.setLongitude(located.getLongitude());
        offer.setDepartmentCode(offer.getDepartmentCode() != null ? offer.getDepartmentCode() : located.getDepartmentCode());
        offer.setRegionCode(offer.getRegionCode() != null ? offer.getRegionCode() : located.getRegionCode());
        offer.setCountryCode(located.getCountryCode());
        offer.setSearchText(searchText(offer));
        return true;
    }

    private void applyPlace(JobOffer offer, ScrapedOffer scraped) {
        String postalCode = cap(scraped.getPostalCode(), 10);
        String city = cap(scraped.getCity(), 120);
        String country = scraped.getCountryCode() == null ? null : scraped.getCountryCode().strip().toUpperCase(Locale.ROOT);
        // Some sites put a department or a region where the city goes ("Hauts-de-Seine", "Île-de-France"); Paris is
        // both a department and a commune, so it is still located. Words around the commune are left aside first.
        String named = PlaceNames.commune(city);
        Optional<Department> cityDepartment = FrenchGeography.departmentNamed(named);
        Optional<FrenchGeography.Region> cityRegion = FrenchGeography.regionNamed(named);
        boolean cityIsArea = cityRegion.isPresent() || cityDepartment.filter(d -> !PARIS.equals(d.code())).isPresent();
        Optional<Department> department = FrenchGeography.departmentOfPostalCode(postalCode)
            .or(() -> FrenchGeography.departmentNamed(scraped.getDepartmentName()))
            .or(() -> cityDepartment);
        boolean french = "FR".equals(country) || country == null && (department.isPresent() || cityRegion.isPresent());
        String regionCode = department.map(Department::regionCode)
            .or(() -> FrenchGeography.regionNamed(scraped.getRegionName()).map(FrenchGeography.Region::code))
            .or(() -> cityRegion.map(FrenchGeography.Region::code))
            .orElse(null);
        Double latitude = coordinate(scraped.getLatitude(), 90);
        Double longitude = coordinate(scraped.getLongitude(), 180);
        String departmentCode = department.map(Department::code).orElse(null);
        boolean locatable = named != null && !cityIsArea || postalCode != null;
        if ((french || country == null) && (latitude == null || departmentCode == null) && locatable) {
            String commune = named == null || cityIsArea ? null : named;
            Optional<GeoLocator.Place> place = geoLocator.locate(commune, postalCode);
            if (place.isPresent()) {
                french = true;
                departmentCode = departmentCode != null ? departmentCode : place.get().departmentCode();
                regionCode = regionCode != null ? regionCode : place.get().regionCode();
                if (latitude == null || longitude == null) {
                    latitude = place.get().latitude();
                    longitude = place.get().longitude();
                }
            }
        }
        offer.setCity(city);
        offer.setPostalCode(postalCode);
        offer.setDepartmentCode(departmentCode);
        offer.setRegionCode(regionCode);
        offer.setCountryCode(country != null && country.length() == 2 ? country : french ? "FR" : null);
        offer.setLatitude(latitude != null && longitude != null ? latitude : null);
        offer.setLongitude(latitude != null && longitude != null ? longitude : null);
    }

    /** Published before the retention window, or already expired: the feed would remove it at once. */
    private boolean isStale(ScrapedOffer scraped, Instant now) {
        Instant published = scraped.getPublishedAt();
        Instant expires = scraped.getExpiresAt();
        return published != null && published.isBefore(now.minus(Duration.ofDays(properties.retentionDays()))) ||
            expires != null && !expires.isAfter(now);
    }

    /** The site's duration, else one written in the title, else in the description's beginning. */
    private static Integer durationOf(ScrapedOffer scraped, String description) {
        if (scraped.getDurationMonths() != null) {
            return scraped.getDurationMonths();
        }
        Integer inTitle = OfferText.durationInText(scraped.getTitle(), true);
        if (inTitle != null || description == null) {
            return inTitle;
        }
        return OfferText.durationInText(description.substring(0, Math.min(description.length(), DURATION_SEARCH_LENGTH)), false);
    }

    private static void applyDates(JobOffer offer, ScrapedOffer scraped, Instant now) {
        Instant published = scraped.getPublishedAt();
        if (published == null) {
            offer.setPublishedAt(now);
            offer.setDatePrecision(OfferDatePrecision.FIRST_SEEN);
        } else {
            offer.setPublishedAt(published.isAfter(now) ? now : published);
            offer.setDatePrecision(scraped.isPublishedDayOnly() ? OfferDatePrecision.DAY : OfferDatePrecision.EXACT);
        }
        offer.setFirstSeenAt(now);
        offer.setLastSeenAt(now);
        offer.setExpiresAt(scraped.getExpiresAt());
    }

    /** Distinct skills (case-insensitive), trimmed, at most {@link #MAX_SKILLS}. */
    static List<String> skills(List<String> raw) {
        Map<String, String> distinct = new LinkedHashMap<>();
        if (raw != null) {
            for (String skill : raw) {
                String value = cap(skill, MAX_SKILL_LENGTH);
                if (value != null) {
                    distinct.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
                }
            }
        }
        return new ArrayList<>(distinct.values()).subList(0, Math.min(MAX_SKILLS, distinct.size()));
    }

    static String searchText(JobOffer offer) {
        String description = offer.getDescription();
        if (description != null && description.length() > SEARCHED_DESCRIPTION_LENGTH) {
            description = description.substring(0, SEARCHED_DESCRIPTION_LENGTH);
        }
        String department = FrenchGeography.department(offer.getDepartmentCode()).map(Department::name).orElse(null);
        return Stream.concat(Stream.of(offer.getTitle(), offer.getCompany(), offer.getCity(), department),
                Stream.concat(offer.getSkills().stream(), Stream.of(description)))
            .filter(part -> part != null && !part.isBlank())
            .map(TextFolding::words)
            .collect(Collectors.joining(" "));
    }

    /** Trimmed text cut to {@code max} characters; blank is {@code null}. */
    static String cap(String text, int max) {
        if (text == null) {
            return null;
        }
        String value = text.strip().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > max ? value.substring(0, max).strip() : value;
    }

    /** An absolute http(s) link, or {@code null}. */
    static String webLink(String url) {
        if (url == null || url.length() > 1000) {
            return null;
        }
        try {
            URI uri = URI.create(url.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ("https".equals(scheme) || "http".equals(scheme)) && uri.getHost() != null ? uri.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer validRate(Integer rate) {
        return rate != null && rate >= OfferText.MIN_DAILY_RATE && rate <= OfferText.MAX_DAILY_RATE ? rate : null;
    }

    private static Double coordinate(Double value, double bound) {
        return value != null && !value.isNaN() && Math.abs(value) <= bound && value != 0 ? value : null;
    }
}
