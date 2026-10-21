package dev.swirlit.indezy.model.enums;

/**
 * Job boards Indezy looked at and does not read, each for a reason the site health page explains (the frontend
 * translates it by name; see docs/features.md#site-health). Those a freelancer can still use independently are listed
 * on that page as sites to visit, with what they take.
 */
public enum OfferLeftOutSite {
    HITECHPROS("HiTechPros", "https://www.hitechpros.com", Kind.VISIT, Access.MEMBERSHIP),
    OPTEAMIS("Opteamis", "https://www.opteamis.com", Kind.VISIT, Access.SIGN_IN),
    LOOKINGFORTECHS("LookingForTechs", "https://www.sharebound.fr", Kind.VISIT, Access.SIGN_IN),
    CAPGEMINI_TALENTNET("Capgemini TalentNet", "https://fr.capgemini.talentnet.community", Kind.VISIT, Access.CRAWLERS),
    HELLOWORK("HelloWork", "https://www.hellowork.com", Kind.VISIT, Access.CRAWLERS),
    MALT("Malt", "https://www.malt.fr", Kind.MATCHING, Access.PROFILE),
    FREELANCE_COM("Freelance.com", "https://www.freelance.com", Kind.MATCHING, Access.PROFILE),
    WHEELOFWORK("WheelOfWork", "https://www.wheelofwork.com", Kind.MATCHING, Access.PROFILE),
    COMET("Comet", "https://www.comet.co", Kind.MATCHING, Access.PROFILE),
    AMAZING_DEV("Amazing.dev", "https://amazing.dev", Kind.MATCHING, Access.PROFILE),
    CREMEDELACREME("Crème de la Crème", "https://www.cremedelacreme.io", Kind.MATCHING, Access.PROFILE),
    LINKEDIN("LinkedIn", "https://www.linkedin.com", Kind.OTHER, null),
    MON_CONSULTANT_INDEPENDANT("Mon Consultant Indépendant", "https://www.mon-consultant-independant.com", Kind.OTHER, null),
    NEXORIS("Nexoris", "https://www.nexoris.com", Kind.OTHER, null),
    WELCOME_TO_THE_JUNGLE("Welcome to the Jungle", "https://www.welcometothejungle.com", Kind.OTHER, null),
    URBAN_LINKER("Urban Linker", "https://urbanlinker.com", Kind.OTHER, null),
    BLUECODERS("Bluecoders", "https://www.bluecoders.com", Kind.OTHER, null),
    UNLCK("UNLCK", "https://unlck.fr", Kind.OTHER, null),
    KONEKT("Konekt", "https://www.konekt.fr", Kind.OTHER, null);

    /** How a freelancer uses a site Indezy does not read. */
    public enum Kind {
        /** Missions are there to be seen in person: it takes an account, a membership or a referral, or crawlers are refused. */
        VISIT,
        /** A profile is created once and the service calls back when a mission matches. */
        MATCHING,
        /** Not worth a visit for freelance missions (none, salaried jobs only, or found elsewhere). */
        OTHER
    }

    /** What seeing a site's missions takes. */
    public enum Access {
        /** A paid membership. */
        MEMBERSHIP,
        /** An account. */
        SIGN_IN,
        /** Nothing for a person, but the site refuses crawlers. */
        CRAWLERS,
        /** A profile the service matches with missions. */
        PROFILE
    }

    private final String displayName;
    private final String homeUrl;
    private final Kind kind;
    private final Access access;

    OfferLeftOutSite(String displayName, String homeUrl, Kind kind, Access access) {
        this.displayName = displayName;
        this.homeUrl = homeUrl;
        this.kind = kind;
        this.access = access;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHomeUrl() {
        return homeUrl;
    }

    public Kind getKind() {
        return kind;
    }

    /** {@code null} for a site that is not worth a visit. */
    public Access getAccess() {
        return access;
    }
}
