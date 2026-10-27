package dev.swirlit.indezy.model.enums;

import java.util.List;

/**
 * Features an administrator can turn off without redeploying, each with the API paths it answers on (below the
 * context path). All are on until turned off.
 */
public enum FeatureFlag {
    // The unsubscribe links of saved search emails stay open under /public, whatever the feed's state.
    OFFER_FEED("/offers"),
    PUBLIC_PROFILES("/public/profiles", "/users/public-profile"),
    DATA_IMPORT("/users/import"),
    YEARLY_REVIEW("/projects/review");

    private final List<String> paths;

    FeatureFlag(String... paths) {
        this.paths = List.of(paths);
    }

    /** Whether the request path, below the context path, belongs to this feature. */
    public boolean covers(String path) {
        return paths.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
