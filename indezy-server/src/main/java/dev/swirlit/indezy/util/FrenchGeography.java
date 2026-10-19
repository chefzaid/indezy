package dev.swirlit.indezy.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * French departments and regions (INSEE codes, 2016 regions), loaded from {@code geo/*.csv}. Turns the
 * postal codes and the department or region names job boards give into codes the offer filters use.
 */
public final class FrenchGeography {

    public record Region(String code, String name) {
    }

    public record Department(String code, String name, String regionCode) {
    }

    private static final Map<String, Region> REGIONS = load("geo/french-regions.csv",
        columns -> new Region(columns[0], columns[1]), Region::code);
    private static final Map<String, Department> DEPARTMENTS = load("geo/french-departments.csv",
        columns -> new Department(columns[0], columns[1], columns[2]), Department::code);
    private static final Map<String, Department> DEPARTMENTS_BY_NAME = DEPARTMENTS.values().stream()
        .collect(Collectors.toMap(department -> TextFolding.words(department.name()), Function.identity()));
    private static final Map<String, Region> REGIONS_BY_NAME = REGIONS.values().stream()
        .collect(Collectors.toMap(region -> TextFolding.words(region.name()), Function.identity()));

    private FrenchGeography() {
    }

    public static Optional<Department> department(String code) {
        return Optional.ofNullable(code).map(DEPARTMENTS::get);
    }

    public static Optional<Region> region(String code) {
        return Optional.ofNullable(code).map(REGIONS::get);
    }

    /** The regions, sorted by name. */
    public static List<Region> regions() {
        return REGIONS.values().stream().sorted(Comparator.comparing(region -> TextFolding.fold(region.name()))).toList();
    }

    /** The departments of a region, sorted by code. */
    public static List<Department> departmentsOf(String regionCode) {
        return DEPARTMENTS.values().stream()
            .filter(department -> department.regionCode().equals(regionCode))
            .sorted(Comparator.comparing(Department::code))
            .toList();
    }

    /**
     * The department of a French postal code: the first two digits, three for overseas departments, and
     * 2A or 2B for Corsica (20000-20199 is Corse-du-Sud).
     */
    public static Optional<Department> departmentOfPostalCode(String postalCode) {
        if (postalCode == null || !postalCode.trim().matches("\\d{5}")) {
            return Optional.empty();
        }
        String code = postalCode.trim();
        if (code.startsWith("97")) {
            return department(code.substring(0, 3));
        }
        if (code.startsWith("20")) {
            return department(Integer.parseInt(code) < 20_200 ? "2A" : "2B");
        }
        return department(code.substring(0, 2));
    }

    /** A department by its name, whatever the case, accents or hyphens ("hauts de seine"). */
    public static Optional<Department> departmentNamed(String name) {
        return Optional.ofNullable(TextFolding.words(name)).map(DEPARTMENTS_BY_NAME::get);
    }

    /** A region by its name, whatever the case, accents or hyphens ("ile de france"). */
    public static Optional<Region> regionNamed(String name) {
        return Optional.ofNullable(TextFolding.words(name)).map(REGIONS_BY_NAME::get);
    }

    private static <T> Map<String, T> load(String resource, Function<String[], T> parse, Function<T, String> key) {
        InputStream input = FrenchGeography.class.getClassLoader().getResourceAsStream(resource);
        if (input == null) {
            throw new IllegalStateException("Missing " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<T> rows = new ArrayList<>();
            reader.readLine();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank()) {
                    rows.add(parse.apply(line.split(",", -1)));
                }
            }
            Map<String, T> byKey = new LinkedHashMap<>();
            rows.forEach(row -> byKey.put(key.apply(row), row));
            return Map.copyOf(byKey);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
