package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import dev.swirlit.indezy.model.enums.OfferSite;

import java.net.URI;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The website a posting was read on, as the workspace's source names it: a job board Indezy knows by its own name and
 * home ({@code Welcome to the Jungle}), any other site by its host without {@code www}.
 *
 * @param name the source's name
 * @param homeUrl the source's link
 */
record PostingSite(String name, String homeUrl) {

    static PostingSite of(URI page) {
        String host = bare(page.getHost());
        return Stream.concat(
                Stream.of(OfferSite.values()).map(site -> new PostingSite(site.getDisplayName(), site.getHomeUrl())),
                Stream.of(OfferLeftOutSite.values()).map(site -> new PostingSite(site.getDisplayName(), site.getHomeUrl())))
            .filter(site -> {
                String home = bare(URI.create(site.homeUrl()).getHost());
                return host.equals(home) || host.endsWith("." + home);
            })
            .findFirst()
            .orElseGet(() -> new PostingSite(host, "https://" + page.getHost()));
    }

    private static String bare(String host) {
        return host == null ? "" : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    }
}
