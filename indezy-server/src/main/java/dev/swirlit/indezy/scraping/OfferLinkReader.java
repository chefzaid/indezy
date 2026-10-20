package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferLinkStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the offer a pasted link points to: among the offers collected from its site (tracking parameters, fragments
 * and trailing slashes aside), else read from its page alone when the site's reader can, else by asking for a reading
 * of the site, which finds the offers published since its last run.
 */
@Service
public class OfferLinkReader {

    /** When to look again for an offer whose site is being read. */
    static final Duration LOOK_AGAIN_AFTER = Duration.ofSeconds(5);
    private static final int MAX_LINK_LENGTH = 2_000;
    /** Parameters that tell where a visitor came from, never which offer. */
    private static final Set<String> TRACKING = Set.of("gclid", "fbclid", "msclkid", "mc_cid", "mc_eid", "trk", "ref", "referrer", "xtor");

    /** Where a link led: with {@link OfferLinkStatus#FOUND}, the offer found. */
    public record Lookup(OfferLinkStatus status, Long offerId, Instant retryAt) {
    }

    private final List<OfferReader> readers;
    private final OfferCollector collector;
    private final OfferSiteRefresher refresher;
    private final OfferSiteStatusRepository statuses;
    private final OfferProperties properties;
    private final JobOfferRepository offers;
    private final Clock clock;

    @Autowired
    public OfferLinkReader(List<OfferReader> readers, OfferCollector collector, OfferSiteRefresher refresher,
                           OfferSiteStatusRepository statuses, OfferProperties properties, JobOfferRepository offers) {
        this(readers, collector, refresher, statuses, properties, offers, Clock.systemUTC());
    }

    OfferLinkReader(List<OfferReader> readers, OfferCollector collector, OfferSiteRefresher refresher, OfferSiteStatusRepository statuses,
                    OfferProperties properties, JobOfferRepository offers, Clock clock) {
        this.readers = readers;
        this.collector = collector;
        this.refresher = refresher;
        this.statuses = statuses;
        this.properties = properties;
        this.offers = offers;
        this.clock = clock;
    }

    /**
     * The web address {@code link} names, as {@code https} without its fragment, tracking parameters and trailing
     * slashes; a link without a scheme is read as {@code https}.
     */
    public static URI parse(String link) {
        String text = link == null ? "" : link.strip();
        if (text.isEmpty() || text.length() > MAX_LINK_LENGTH) {
            throw new IllegalArgumentException(ErrorMessages.OFFER_LINK_INVALID);
        }
        try {
            URI uri = new URI(text.contains("://") ? text : "https://" + text);
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (!("https".equals(scheme) || "http".equals(scheme)) || uri.getHost() == null || uri.getRawUserInfo() != null ||
                port != -1 && port != 80 && port != 443) {
                throw new IllegalArgumentException(ErrorMessages.OFFER_LINK_INVALID);
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String query = withoutTracking(uri.getRawQuery());
            return new URI("https://" + uri.getHost().toLowerCase(Locale.ROOT) + path + (query.isEmpty() ? "" : "?" + query));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(ErrorMessages.OFFER_LINK_INVALID, e);
        }
    }

    /** The site the feed reads that {@code link} is on, subdomains included. */
    public Optional<OfferSite> siteOf(URI link) {
        String host = link.getHost();
        return readers.stream()
            .filter(reader -> host.equals(reader.domain()) || host.endsWith("." + reader.domain()))
            .map(OfferReader::site)
            .findFirst();
    }

    /** The names of the sites the feed reads, alphabetically. */
    public List<String> siteNames() {
        return readers.stream().map(reader -> reader.site().getDisplayName()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /**
     * The offer at {@code link} on {@code site}. An offer not collected yet is read from its page when the site's reader
     * can, after {@code beforeRead} (which refuses by throwing); otherwise a reading of the site is asked for.
     */
    public Lookup find(OfferSite site, URI link, Runnable beforeRead) {
        Optional<JobOffer> stored = stored(site, link);
        if (stored.isPresent()) {
            return new Lookup(OfferLinkStatus.FOUND, stored.get().getId(), null);
        }
        if (!properties.collectionEnabled()) {
            return new Lookup(OfferLinkStatus.NOT_COLLECTED, null, null);
        }
        if (collector.offerId(site, link.toString()).isEmpty()) {
            return requestReading(site);
        }
        beforeRead.run();
        OfferLinkStatus read = collector.readOne(site, link.toString());
        if (read != OfferLinkStatus.FOUND) {
            return new Lookup(read, null, null);
        }
        return stored(site, link).map(offer -> new Lookup(OfferLinkStatus.FOUND, offer.getId(), null))
            .orElseGet(() -> new Lookup(OfferLinkStatus.GONE, null, null));
    }

    /** The collected offer at {@code link}: the one of its identifier, else the newest whose address ends alike. */
    Optional<JobOffer> stored(OfferSite site, URI link) {
        Optional<String> externalId = collector.offerId(site, link.toString());
        if (externalId.isPresent()) {
            Optional<JobOffer> offer = offers.findBySiteAndExternalIdIn(site, List.of(externalId.get())).stream().findFirst();
            if (offer.isPresent()) {
                return offer;
            }
        }
        String raw = rawLastSegment(link.getRawPath());
        if (raw == null) {
            return Optional.empty();
        }
        String segment = decode(raw);
        Set<String> fragments = new LinkedHashSet<>(List.of("/" + raw, "/" + segment));
        String path = link.getRawPath();
        return fragments.stream()
            .flatMap(fragment -> offers.findTop20BySiteAndUrlContainingIgnoreCaseOrderByIdDesc(site, fragment).stream())
            .filter(offer -> segment.equals(lastSegment(offer.getUrl())))
            // A site naming its offers by a parameter names this one by the same.
            .filter(offer -> queryOf(offer.getUrl()).isEmpty() || queryOf(offer.getUrl()).equals(withoutTracking(link.getRawQuery())))
            // The same path first, then the newest.
            .min(Comparator.comparing((JobOffer offer) -> !Objects.equals(pathOf(offer.getUrl()), path))
                .thenComparing(JobOffer::getId, Comparator.reverseOrder()));
    }

    /** Asks for a reading of {@code site}, which finds the offers published since its last run, unless one is under way. */
    private Lookup requestReading(OfferSite site) {
        Instant now = clock.instant();
        try {
            refresher.refresh(site);
            return new Lookup(OfferLinkStatus.READING, null, now.plus(LOOK_AGAIN_AFTER));
        } catch (TooManyRequestsException e) {
            OfferSiteStatus status = statuses.findById(site).orElse(null);
            if (status != null && underWay(status, now)) {
                return new Lookup(OfferLinkStatus.READING, null, now.plus(LOOK_AGAIN_AFTER));
            }
            return new Lookup(OfferLinkStatus.NOT_COLLECTED, null, status == null ? null : OfferSiteRuns.refreshAvailableAt(status));
        }
    }

    /** Whether a run holds the site, or one asked for has not ended yet. */
    private boolean underWay(OfferSiteStatus status, Instant now) {
        Instant running = status.getRunningSince();
        if (running != null && running.isAfter(now.minus(properties.staleRunAfter()))) {
            return true;
        }
        Instant requested = status.getRefreshRequestedAt();
        return requested != null && (status.getLastRunAt() == null || requested.isAfter(status.getLastRunAt()));
    }

    /** The last segment of an address's path, decoded and in lower case; {@code null} when the path is empty. */
    static String lastSegment(String url) {
        String raw = rawLastSegment(pathOf(url));
        return raw == null ? null : decode(raw);
    }

    /** The path of an address, without its query, fragment and trailing slashes. */
    private static String pathOf(String url) {
        String rest = url;
        int end = indexOfEither(rest, '?', '#');
        if (end >= 0) {
            rest = rest.substring(0, end);
        }
        int scheme = rest.indexOf("://");
        int slash = rest.indexOf('/', scheme < 0 ? 0 : scheme + 3);
        String path = slash < 0 ? "" : rest.substring(slash);
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /** The parameters of an address that are not tracking ones (campaign tags, click identifiers, referrers). */
    private static String queryOf(String url) {
        int start = url.indexOf('?');
        if (start < 0) {
            return "";
        }
        int fragment = url.indexOf('#', start);
        return withoutTracking(url.substring(start + 1, fragment < 0 ? url.length() : fragment));
    }

    private static String withoutTracking(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (String parameter : query.split("&")) {
            String name = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
            if (!parameter.isEmpty() && !name.startsWith("utm_") && !TRACKING.contains(name)) {
                kept.append(kept.isEmpty() ? "" : "&").append(parameter);
            }
        }
        return kept.toString();
    }

    private static String rawLastSegment(String path) {
        String segment = path.substring(path.lastIndexOf('/') + 1);
        return segment.isEmpty() ? null : segment;
    }

    private static String decode(String segment) {
        try {
            return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return segment.toLowerCase(Locale.ROOT);
        }
    }

    private static int indexOfEither(String text, char first, char second) {
        int a = text.indexOf(first);
        int b = text.indexOf(second);
        return a < 0 ? b : b < 0 ? a : Math.min(a, b);
    }
}
