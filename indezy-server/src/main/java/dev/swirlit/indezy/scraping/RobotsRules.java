package dev.swirlit.indezy.scraping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The {@code robots.txt} rules that apply to Indezy (RFC 9309): the group naming its product token,
 * otherwise the {@code *} group. The longest matching path wins, and {@code Allow} wins a tie.
 */
public final class RobotsRules {

    private record Rule(boolean allow, String path, Pattern pattern) {
    }

    private static final RobotsRules ALLOW_ALL = new RobotsRules(List.of());
    private static final RobotsRules DISALLOW_ALL =
        new RobotsRules(List.of(new Rule(false, "/", Pattern.compile("/.*"))));

    private final List<Rule> rules;

    private RobotsRules(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** No robots.txt (4xx): everything may be fetched. */
    public static RobotsRules allowAll() {
        return ALLOW_ALL;
    }

    /** robots.txt unreachable (5xx, network): nothing may be fetched until it can be read. */
    public static RobotsRules disallowAll() {
        return DISALLOW_ALL;
    }

    /** The rules of {@code text} for the crawler named {@code productToken} ("IndezyBot"). */
    public static RobotsRules parse(String text, String productToken) {
        String token = productToken.toLowerCase(Locale.ROOT);
        List<Rule> named = new ArrayList<>();
        List<Rule> wildcard = new ArrayList<>();
        List<String> agents = new ArrayList<>();
        boolean readingAgents = false;
        for (String rawLine : text.split("\\R")) {
            String line = stripComment(rawLine);
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String field = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if ("user-agent".equals(field)) {
                if (!readingAgents) {
                    agents.clear();
                }
                agents.add(value.toLowerCase(Locale.ROOT));
                readingAgents = true;
            } else if ("allow".equals(field) || "disallow".equals(field)) {
                readingAgents = false;
                if (value.isEmpty()) {
                    continue;
                }
                Rule rule = new Rule("allow".equals(field), value, compile(value));
                if (agents.contains(token)) {
                    named.add(rule);
                } else if (agents.contains("*")) {
                    wildcard.add(rule);
                }
            } else {
                readingAgents = false;
            }
        }
        return new RobotsRules(named.isEmpty() ? wildcard : named);
    }

    /** Whether a path with its query ("/jobs?page=2") may be fetched. */
    public boolean allows(String pathAndQuery) {
        String path = pathAndQuery == null || pathAndQuery.isEmpty() ? "/" : pathAndQuery;
        Rule best = null;
        for (Rule rule : rules) {
            if (rule.pattern().matcher(path).matches() &&
                (best == null || rule.path().length() > best.path().length() ||
                    rule.path().length() == best.path().length() && rule.allow())) {
                best = rule;
            }
        }
        return best == null || best.allow();
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    private static Pattern compile(String path) {
        boolean anchored = path.endsWith("$");
        String body = anchored ? path.substring(0, path.length() - 1) : path;
        StringBuilder regex = new StringBuilder();
        for (String part : body.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        if (!anchored) {
            regex.append(".*");
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }
}
