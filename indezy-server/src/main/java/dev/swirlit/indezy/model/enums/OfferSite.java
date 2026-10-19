package dev.swirlit.indezy.model.enums;

import java.time.Duration;

/**
 * The job boards whose freelance offers Indezy collects (see docs/features.md#freelance-offer-feed).
 * Each site has one reader in {@code dev.swirlit.indezy.scraping.reader}; the refresh interval is how
 * long the scheduler waits after a run before reading the site again.
 */
public enum OfferSite {
    FREE_WORK("Free-Work", "https://www.free-work.com", Duration.ofHours(1)),
    COLLECTIVE("Collective", "https://www.collective.work", Duration.ofHours(1)),
    CHERRY_PICK("Cherry Pick", "https://www.cherry-pick.io", Duration.ofHours(3)),
    // Its API only returns the 10 newest missions, so it is read more often to miss none.
    LEHIBOU("LeHibou", "https://www.lehibou.com", Duration.ofMinutes(30)),
    YEETS("Yeets", "https://www.yeets.fr", Duration.ofHours(6)),
    WORKDISPO("WorkDispo", "https://www.workdispo.com", Duration.ofHours(2)),
    NEXT_MONDAYS("Next Mondays", "https://nextmondays.com", Duration.ofHours(12)),
    MINDQUEST("Mindquest", "https://mindquest.io", Duration.ofHours(3)),
    FREELANCE_FACTORY("Freelance Factory", "https://freelance-factory.fr", Duration.ofHours(6)),
    LITTLE_BIG_CONNECTION("LittleBig Connection", "https://www.littlebigconnection.com", Duration.ofHours(6)),
    SILKHOM("Silkhom", "https://www.silkhom.com", Duration.ofHours(6)),
    LEGRATIN("LeGratin", "https://www.legratin.io", Duration.ofHours(3)),
    FREELANCE_INFORMATIQUE("Freelance-Informatique", "https://www.freelance-informatique.fr", Duration.ofHours(2)),
    ESPACE_FREELANCE("Espace Freelance", "https://www.espace-freelance.fr", Duration.ofHours(12)),
    BEYOND_SKILLS_IT("Beyond Skills IT", "https://www.beyondskillsit.com", Duration.ofHours(12)),
    KOINO("Koïno", "https://www.koino.fr", Duration.ofHours(12)),
    SIXIEME_HOMME("Sixième Homme", "https://sixiemehomme.io", Duration.ofHours(12));

    private final String displayName;
    private final String homeUrl;
    private final Duration refreshInterval;

    OfferSite(String displayName, String homeUrl, Duration refreshInterval) {
        this.displayName = displayName;
        this.homeUrl = homeUrl;
        this.refreshInterval = refreshInterval;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHomeUrl() {
        return homeUrl;
    }

    public Duration getRefreshInterval() {
        return refreshInterval;
    }
}
