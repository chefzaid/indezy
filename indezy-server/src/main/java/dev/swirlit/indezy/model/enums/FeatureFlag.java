package dev.swirlit.indezy.model.enums;

import java.util.List;

/**
 * Features an administrator can turn off or on without redeploying, each with the API paths it answers on (below the
 * context path). Each is on until turned off, but the member network, off until turned on.
 */
public enum FeatureFlag {
    // The unsubscribe links of saved search emails stay open under /public, whatever the feed's state.
    OFFER_FEED("/offers"),
    PUBLIC_PROFILES("/public/profiles", "/users/public-profile"),
    DATA_IMPORT("/users/import"),
    YEARLY_REVIEW("/projects/review"),
    SOCIAL_NETWORK(false, "/network");

    private final boolean enabledByDefault;
    private final List<String> paths;

    FeatureFlag(String... paths) {
        this(true, paths);
    }

    FeatureFlag(boolean enabledByDefault, String... paths) {
        this.enabledByDefault = enabledByDefault;
        this.paths = List.of(paths);
    }

    /** Whether the feature is on before an administrator sets it. */
    public boolean enabledByDefault() {
        return enabledByDefault;
    }

    /** Whether the request path, below the context path, belongs to this feature. */
    public boolean covers(String path) {
        return paths.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
