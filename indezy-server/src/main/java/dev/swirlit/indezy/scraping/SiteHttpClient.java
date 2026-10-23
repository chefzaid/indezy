package dev.swirlit.indezy.scraping;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * The only way the offer collection reaches the network. Each request stays on the reader's domain, or its site's
 * API's ({@link OfferReader#apiDomain()}), redirects included, is checked against the host's {@code robots.txt}, waits the configured delay
 * since the previous request to that host, and is refused when its body passes {@link #MAX_BODY_BYTES}.
 */
@Component
public class SiteHttpClient {

    /** Product token Indezy answers to in {@code robots.txt}. */
    public static final String ROBOTS_TOKEN = "IndezyBot";
    static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    private static final int MAX_REDIRECTS = 4;
    private static final Duration ROBOTS_TTL = Duration.ofHours(12);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern CHARSET = Pattern.compile("charset=\"?([^;\"\\s]+)", Pattern.CASE_INSENSITIVE);

    /** Pauses the collection thread; replaced in tests. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private record CachedRobots(RobotsRules rules, Instant fetchedAt) {
    }

    private final OfferProperties properties;
    private final HttpClient http;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Map<String, CachedRobots> robots = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastRequests = new ConcurrentHashMap<>();

    @Autowired
    public SiteHttpClient(OfferProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(),
            Clock.systemUTC(), duration -> Thread.sleep(duration.toMillis()));
    }

    SiteHttpClient(OfferProperties properties, HttpClient http, Clock clock, Sleeper sleeper) {
        this.properties = properties;
        this.http = http;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** A fetcher limited to {@code domain} and its subdomains. */
    public PageFetcher fetcherFor(String domain) {
        return fetcherFor(domain, host -> true);
    }

    /** A fetcher limited to a reader's domain and its API's ({@link OfferReader#apiDomain()}), and their subdomains. */
    public PageFetcher fetcherFor(OfferReader reader) {
        List<String> domains = new ArrayList<>();
        domains.add(reader.domain().toLowerCase(Locale.ROOT));
        reader.apiDomain().ifPresent(api -> domains.add(api.toLowerCase(Locale.ROOT)));
        return fetcher(new Target(List.copyOf(domains), host -> true));
    }

    /**
     * A fetcher limited to {@code domain} and its subdomains, and to the hosts {@code allowedHost} accepts, checked before
     * every request (each redirect and the {@code robots.txt} included): a page someone pasted the link of is read only
     * on the public internet (see {@link PublicHosts}).
     */
    public PageFetcher fetcherFor(String domain, Predicate<String> allowedHost) {
        return fetcher(new Target(List.of(domain.toLowerCase(Locale.ROOT)), allowedHost));
    }

    private PageFetcher fetcher(Target allowed) {
        return new PageFetcher() {
            @Override
            public String get(String url) throws IOException, InterruptedException {
                return send(allowed, URI.create(url), null);
            }

            @Override
            public String postJson(String url, String json) throws IOException, InterruptedException {
                return send(allowed, URI.create(url), json);
            }
        };
    }

    /** Where a fetcher may go: its domains, and the hosts its check accepts. */
    private record Target(List<String> domains, Predicate<String> allowedHost) {
    }

    private String send(Target target, URI start, String jsonBody) throws IOException, InterruptedException {
        URI uri = start;
        for (int redirects = 0; ; redirects++) {
            requireOnDomain(target, uri);
            if (!robotsFor(target, uri).allows(pathAndQuery(uri))) {
                throw new SiteResponseException(0, "robots.txt disallows " + uri.getPath());
            }
            HttpResponse<InputStream> response = exchange(uri, jsonBody);
            int status = response.statusCode();
            String location = response.headers().firstValue("Location").orElse(null);
            if (isRedirect(status) && location != null) {
                response.body().close();
                if (jsonBody != null || redirects >= MAX_REDIRECTS) {
                    throw new SiteResponseException(status, "Unexpected redirect from " + uri.getPath());
                }
                uri = uri.resolve(location);
                continue;
            }
            if (status < 200 || status >= 300) {
                response.body().close();
                throw new SiteResponseException(status, (jsonBody == null ? "GET " : "POST ") + uri.getPath() + " answered " + status);
            }
            return readBody(response);
        }
    }

    private RobotsRules robotsFor(Target target, URI uri) throws IOException, InterruptedException {
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        CachedRobots cached = robots.get(origin);
        Instant now = clock.instant();
        if (cached != null && cached.fetchedAt().plus(ROBOTS_TTL).isAfter(now)) {
            return cached.rules();
        }
        RobotsRules rules = fetchRobots(target, URI.create(origin + "/robots.txt"));
        robots.put(origin, new CachedRobots(rules, now));
        return rules;
    }

    private RobotsRules fetchRobots(Target target, URI robotsUri) throws InterruptedException {
        URI uri = robotsUri;
        try {
            for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
                requireOnDomain(target, uri);
                HttpResponse<InputStream> response = exchange(uri, null);
                int status = response.statusCode();
                String location = response.headers().firstValue("Location").orElse(null);
                if (isRedirect(status) && location != null) {
                    response.body().close();
                    uri = uri.resolve(location);
                    continue;
                }
                if (status >= 200 && status < 300) {
                    return RobotsRules.parse(readBody(response), ROBOTS_TOKEN);
                }
                response.body().close();
                // RFC 9309: no robots.txt (4xx) allows everything, an unavailable one (5xx) nothing.
                return status >= 400 && status < 500 ? RobotsRules.allowAll() : RobotsRules.disallowAll();
            }
        } catch (IOException e) {
            return RobotsRules.disallowAll();
        }
        return RobotsRules.disallowAll();
    }

    private HttpResponse<InputStream> exchange(URI uri, String jsonBody) throws IOException, InterruptedException {
        waitForTurn(uri.getHost());
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", properties.userAgent())
            .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
            .header("Accept-Encoding", "gzip");
        if (jsonBody == null) {
            request.GET().header("Accept", "text/html,application/json,application/xml;q=0.9,*/*;q=0.8");
        } else {
            request.POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        }
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } finally {
            lastRequests.put(uri.getHost(), clock.instant());
        }
    }

    private void waitForTurn(String host) throws InterruptedException {
        Instant previous = lastRequests.get(host);
        if (previous == null) {
            return;
        }
        Duration wait = Duration.between(clock.instant(), previous.plus(properties.requestDelay()));
        if (!wait.isNegative() && !wait.isZero()) {
            sleeper.sleep(wait);
        }
    }

    private static String readBody(HttpResponse<InputStream> response) throws IOException {
        boolean gzip = response.headers().firstValue("Content-Encoding")
            .map(encoding -> encoding.toLowerCase(Locale.ROOT).contains("gzip")).orElse(false);
        try (InputStream raw = response.body(); InputStream body = gzip ? new GZIPInputStream(raw) : raw) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            for (int read = body.read(buffer); read >= 0; read = body.read(buffer)) {
                if (bytes.size() + read > MAX_BODY_BYTES) {
                    throw new IOException("Response from " + response.uri().getHost() + " passes " + MAX_BODY_BYTES + " bytes");
                }
                bytes.write(buffer, 0, read);
            }
            return bytes.toString(charsetOf(response));
        }
    }

    private static Charset charsetOf(HttpResponse<?> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        Matcher matcher = CHARSET.matcher(contentType);
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static void requireOnDomain(Target target, URI uri) throws SiteResponseException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean onDomain = target.domains().stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
        if (!("https".equals(scheme) || "http".equals(scheme)) || !onDomain) {
            throw new SiteResponseException(0, "Refused to leave " + String.join(" and ", target.domains()) + " for " + host);
        }
        if (!target.allowedHost().test(host)) {
            throw new SiteResponseException(0, "Refused to read " + host);
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static String pathAndQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }
}
