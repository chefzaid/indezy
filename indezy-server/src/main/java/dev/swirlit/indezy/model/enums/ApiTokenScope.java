package dev.swirlit.indezy.model.enums;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What an API token may read, by the API paths of each part of the workspace. A token without scopes reads all of it;
 * a path no scope names (the global search, the profile) needs such a token.
 */
public enum ApiTokenScope {
    /** A mission's activity reports and purchase orders, before the opportunity they belong to. */
    MISSIONS(Pattern.compile("^/projects/[^/]+/(activity-reports|purchase-orders)(/.*)?$"), "/purchase-orders"),
    OPPORTUNITIES(null, "/projects", "/interview-steps", "/seasons", "/pipeline-templates"),
    CONTACTS(null, "/clients", "/contacts", "/sources"),
    INVOICES(null, "/invoices"),
    DOCUMENTS(null, "/compliance-documents", "/compliance-packs"),
    OFFERS(null, "/offers");

    private final Pattern pattern;
    private final List<String> prefixes;

    ApiTokenScope(Pattern pattern, String... prefixes) {
        this.pattern = pattern;
        this.prefixes = List.of(prefixes);
    }

    /** The scope a path (below the context path) belongs to, the most specific first; none for a path of no scope. */
    public static Optional<ApiTokenScope> of(String path) {
        for (ApiTokenScope scope : values()) {
            if (scope.pattern != null && scope.pattern.matcher(path).matches() ||
                scope.prefixes.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"))) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }
}
