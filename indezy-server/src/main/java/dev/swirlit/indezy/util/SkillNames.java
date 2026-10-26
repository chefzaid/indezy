package dev.swirlit.indezy.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One name per skill, whatever its spelling: "Spring Boot", "SpringBoot" and "spring-boot" are one skill (same
 * {@link #key}), and the aliases below ("JS" for JavaScript, "K8s" for Kubernetes) name the skill they stand for.
 */
public final class SkillNames {

    /** Each skill's usual name, and the other names it goes by. */
    private static final Map<String, List<String>> ALIASES = skills(
        skill("JavaScript", "JS", "ECMAScript", "ES6"),
        skill("TypeScript", "TS"),
        skill("Node.js", "NodeJS", "Node"),
        skill("React", "ReactJS", "React.js"),
        skill("React Native", "RN"),
        skill("Vue.js", "Vue", "VueJS"),
        skill("Next.js", "NextJS"),
        skill("Angular", "Angular 2+"),
        skill("Spring Boot", "SpringBoot"),
        skill("Kubernetes", "K8s", "Kube"),
        skill("PostgreSQL", "Postgres", "PSQL", "Postgre"),
        skill("MongoDB", "Mongo"),
        skill("SQL Server", "MSSQL", "MS SQL", "Microsoft SQL Server"),
        skill("AWS", "Amazon Web Services"),
        skill("Google Cloud", "GCP", "Google Cloud Platform"),
        skill("Azure", "Microsoft Azure"),
        skill("C#", "CSharp", "C Sharp"),
        skill("C++", "CPP"),
        skill(".NET", "dotnet", "dot net", ".NET Core"),
        skill("Go", "Golang"),
        skill("Objective-C", "ObjC"),
        skill("Ruby on Rails", "Rails", "RoR"),
        skill("Machine Learning", "ML"),
        skill("Power BI", "PowerBI"),
        skill("Tailwind CSS", "Tailwind"),
        skill("Elasticsearch", "Elastic Search"),
        skill("CI/CD", "CICD"));

    private static final Map<String, String> CANONICAL_BY_KEY = new HashMap<>();

    static {
        ALIASES.forEach((canonical, aliases) -> {
            CANONICAL_BY_KEY.put(key(canonical), canonical);
            aliases.forEach(alias -> CANONICAL_BY_KEY.put(key(alias), canonical));
        });
    }

    private SkillNames() {
    }

    /** Folded without spaces, dots or dashes: equal for spellings of one skill ("springboot" for "Spring-Boot"). */
    public static String key(String skill) {
        String words = TextFolding.words(skill);
        return words == null ? "" : words.replace(" ", "").replace(".", "");
    }

    /** The skill's usual name when it is a known one or an alias of one, else the skill as written, trimmed. */
    public static String canonical(String skill) {
        String known = CANONICAL_BY_KEY.get(key(skill));
        return known != null ? known : skill.strip();
    }

    /** Whether two names are the same skill. */
    public static boolean same(String a, String b) {
        return key(canonical(a)).equals(key(canonical(b)));
    }

    /**
     * The folded words ({@link TextFolding#words}) an offer may use for the skill: its own, and, for a known skill, its
     * usual name's and every alias's, without repeats.
     */
    public static List<String> variants(String skill) {
        List<String> names = new ArrayList<>();
        names.add(skill);
        String known = CANONICAL_BY_KEY.get(key(skill));
        if (known != null) {
            names.add(known);
            names.addAll(ALIASES.get(known));
        }
        return names.stream().map(TextFolding::words).filter(words -> !words.isEmpty()).distinct().toList();
    }

    /** Every known skill with its aliases, in order, for the interface's suggestions. */
    public static Map<String, List<String>> aliases() {
        return new LinkedHashMap<>(ALIASES);
    }

    private static Map.Entry<String, List<String>> skill(String name, String... aliases) {
        return Map.entry(name, List.of(aliases));
    }

    @SafeVarargs
    private static Map<String, List<String>> skills(Map.Entry<String, List<String>>... skills) {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> skill : skills) {
            byName.put(skill.getKey(), skill.getValue());
        }
        return Collections.unmodifiableMap(byName);
    }
}
