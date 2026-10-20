package dev.swirlit.indezy.model.enums;

/**
 * Job boards Indezy looked at and does not read, each for a reason the site health page explains (the
 * frontend translates it by name; see docs/features.md#site-health).
 */
public enum OfferLeftOutSite {
    LINKEDIN("LinkedIn", "https://www.linkedin.com"),
    HITECHPROS("HiTechPros", "https://www.hitechpros.com"),
    OPTEAMIS("Opteamis", "https://www.opteamis.com"),
    COMET("Comet", "https://www.comet.co"),
    AMAZING_DEV("Amazing.dev", "https://amazing.dev"),
    LOOKINGFORTECHS("LookingForTechs", "https://www.sharebound.fr"),
    MON_CONSULTANT_INDEPENDANT("Mon Consultant Indépendant", "https://www.mon-consultant-independant.com"),
    NEXORIS("Nexoris", "https://www.nexoris.com"),
    ANYWR("Anywr", "https://anywr.frl");

    private final String displayName;
    private final String homeUrl;

    OfferLeftOutSite(String displayName, String homeUrl) {
        this.displayName = displayName;
        this.homeUrl = homeUrl;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHomeUrl() {
        return homeUrl;
    }
}
