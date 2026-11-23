package dev.swirlit.indezy.service;

import dev.swirlit.indezy.ai.ProfileImportAiService;
import dev.swirlit.indezy.exception.FieldRejectedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Reads the archive LinkedIn's **Get a copy of your data** sends into the profile's fields (see
 * docs/features.md#profile-import), without AI: {@code Profile.csv} (name, headline, summary, place, websites),
 * {@code PhoneNumbers.csv}, {@code Positions.csv} (the current or latest title and company, and each skill's years from
 * the dated positions naming it), {@code Skills.csv}, {@code Languages.csv} and {@code Certifications.csv} (name, issuer,
 * when obtained and when it expires). Columns are found by their names, which
 * LinkedIn changes between exports; every other file of the archive is skipped unread.
 */
public final class LinkedInProfileImport {

    /** The archive LinkedIn sends, at most. */
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final int MAX_ENTRIES = 2_000;
    /** A file read from the archive, at most. */
    private static final int MAX_FILE = 5 * 1024 * 1024;
    private static final Set<String> READ = Set.of("profile.csv", "phonenumbers.csv", "positions.csv", "skills.csv", "languages.csv",
        "certifications.csv");
    private static final Pattern URL = Pattern.compile("https?://[^\\s,\\]]+");
    private static final Map<String, String> LANGUAGES = Map.ofEntries(
        Map.entry("french", "Français"), Map.entry("français", "Français"), Map.entry("english", "Anglais"), Map.entry("anglais", "Anglais"),
        Map.entry("spanish", "Espagnol"), Map.entry("espagnol", "Espagnol"), Map.entry("german", "Allemand"), Map.entry("allemand", "Allemand"),
        Map.entry("italian", "Italien"), Map.entry("italien", "Italien"), Map.entry("portuguese", "Portugais"),
        Map.entry("portugais", "Portugais"), Map.entry("chinese", "Chinois"), Map.entry("mandarin", "Chinois"),
        Map.entry("chinois", "Chinois"), Map.entry("japanese", "Japonais"), Map.entry("japonais", "Japonais"),
        Map.entry("arabic", "Arabe"), Map.entry("arabe", "Arabe"), Map.entry("russian", "Russe"), Map.entry("russe", "Russe"),
        Map.entry("dutch", "Néerlandais"), Map.entry("néerlandais", "Néerlandais"));
    private static final Map<String, Integer> MONTHS = months();

    private LinkedInProfileImport() {
    }

    /** A dated position, its end null while it runs. */
    record Position(String company, String title, String description, YearMonth start, YearMonth end) {
    }

    public static ProfileImportAiService.Reading read(byte[] archive, LocalDate today) {
        Map<String, List<Map<String, String>>> files = files(archive);
        if (files.isEmpty()) {
            throw new FieldRejectedException("file", "This is not LinkedIn's data archive: send the ZIP from Get a copy of your data");
        }
        Map<String, String> profile = files.getOrDefault("profile.csv", List.of()).stream().findFirst().orElse(Map.of());
        List<Position> positions = files.getOrDefault("positions.csv", List.of()).stream()
            .map(row -> new Position(column(row, "company name", "company"), column(row, "title"), column(row, "description"),
                month(column(row, "started on", "start date")), month(column(row, "finished on", "end date"))))
            .filter(position -> position.title() != null || position.company() != null).toList();
        Optional<Position> current = positions.stream().filter(position -> position.end() == null && position.start() != null)
            .max((one, other) -> one.start().compareTo(other.start()))
            .or(() -> positions.stream().filter(position -> position.end() != null).max((one, other) -> one.end().compareTo(other.end())))
            .or(() -> positions.stream().findFirst());
        List<String> websites = new ArrayList<>();
        Matcher urls = URL.matcher(Objects.toString(column(profile, "websites"), ""));
        while (urls.find()) {
            websites.add(urls.group());
        }
        String github = websites.stream().filter(url -> url.contains("github.com")).findFirst().orElse(null);
        String website = websites.stream().filter(url -> !url.contains("github.com") && !url.contains("linkedin.com")).findFirst().orElse(null);
        String linkedin = websites.stream().filter(url -> url.contains("linkedin.com")).findFirst().orElse(null);
        String summary = column(profile, "summary");
        String place = column(profile, "geo location", "location");
        YearMonth now = YearMonth.from(today);
        List<ProfileImportAiService.Skill> skills = files.getOrDefault("skills.csv", List.of()).stream().map(row -> column(row, "name", "skill"))
            .filter(Objects::nonNull).map(name -> new ProfileImportAiService.Skill(name, years(name, positions, now))).toList();
        List<String> languages = files.getOrDefault("languages.csv", List.of()).stream().map(row -> column(row, "name", "language"))
            .filter(Objects::nonNull).map(LinkedInProfileImport::language).toList();
        String phone = files.getOrDefault("phonenumbers.csv", List.of()).stream().map(row -> column(row, "number", "phone"))
            .filter(Objects::nonNull).findFirst().orElse(null);
        List<ProfileImportAiService.Certification> certifications = files.getOrDefault("certifications.csv", List.of()).stream()
            .map(row -> new ProfileImportAiService.Certification(column(row, "name"), column(row, "authority", "issuing organization"),
                day(column(row, "started on", "issue date")), day(column(row, "finished on", "expiration date"))))
            .toList();
        return ProfileImportAiService.clean(new ProfileImportAiService.Reading(column(profile, "first name"), column(profile, "last name"), phone,
            place == null ? null : place.split(",")[0], current.map(Position::title).orElse(column(profile, "headline")),
            current.map(Position::company).orElse(null), summary != null ? summary : column(profile, "headline"), website, linkedin, github,
            languages, skills, certifications));
    }

    /** A month as LinkedIn writes it, as its first day in {@code YYYY-MM-DD}. */
    private static String day(String text) {
        YearMonth month = month(text);
        return month == null ? null : month.atDay(1).toString();
    }

    /** A language under the profile's French name ("English" is Anglais), else as written. */
    static String language(String name) {
        return LANGUAGES.getOrDefault(name.strip().toLowerCase(Locale.ROOT), name.strip());
    }

    /** The years of the dated positions whose title or description names the skill, overlaps counted once; 0 when none. */
    static int years(String skill, List<Position> positions, YearMonth now) {
        Pattern named = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(skill.strip()) + "(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Set<YearMonth> months = new TreeSet<>();
        for (Position position : positions) {
            String text = Objects.toString(position.title(), "") + " " + Objects.toString(position.description(), "");
            if (position.start() == null || !named.matcher(text).find()) {
                continue;
            }
            YearMonth end = position.end() == null || position.end().isAfter(now) ? now : position.end();
            for (YearMonth month = position.start(); !month.isAfter(end); month = month.plusMonths(1)) {
                months.add(month);
            }
        }
        return months.size() / 12;
    }

    /** "Mar 2022", "mars 2022", "2022-03" or "2022"; null when it cannot be read. */
    static YearMonth month(String text) {
        if (text == null) {
            return null;
        }
        String value = text.strip().toLowerCase(Locale.ROOT).replace(".", "");
        Matcher iso = Pattern.compile("^(\\d{4})-(\\d{1,2})").matcher(value);
        if (iso.find()) {
            return YearMonth.of(Integer.parseInt(iso.group(1)), Math.clamp(Integer.parseInt(iso.group(2)), 1, 12));
        }
        Matcher named = Pattern.compile("^(\\p{L}+)\\s+(\\d{4})$").matcher(value);
        if (named.find() && MONTHS.containsKey(named.group(1))) {
            return YearMonth.of(Integer.parseInt(named.group(2)), MONTHS.get(named.group(1)));
        }
        return value.matches("\\d{4}") ? YearMonth.of(Integer.parseInt(value), 1) : null;
    }

    /** The first non-blank value among the columns named, case and spaces aside. */
    private static String column(Map<String, String> row, String... names) {
        for (String name : names) {
            String value = row.get(name);
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    /** The archive's CSV files that are read, by lower-case name, each as rows keyed by lower-case header. */
    private static Map<String, List<Map<String, String>>> files(byte[] archive) {
        Map<String, List<Map<String, String>>> files = new HashMap<>();
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new FieldRejectedException("file", "This archive holds too many files");
                }
                String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                if (entry.isDirectory() || !READ.contains(name)) {
                    continue;
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    if (bytes.size() + read > MAX_FILE) {
                        throw new FieldRejectedException("file", "A file of this archive is too large to be LinkedIn's");
                    }
                    bytes.write(buffer, 0, read);
                }
                files.put(name, rows(bytes.toString(StandardCharsets.UTF_8)));
            }
        } catch (ZipException e) {
            throw new FieldRejectedException("file", "This file is not a ZIP archive: send the one LinkedIn emailed you");
        } catch (IOException e) {
            throw new FieldRejectedException("file", "This archive could not be read");
        }
        return files;
    }

    /** CSV rows keyed by their lower-case headers; quoted values may hold commas, quotes and line breaks. */
    static List<Map<String, String>> rows(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        String text = csv.startsWith("﻿") ? csv.substring(1) : csv;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quoted) {
                if (character == '"' && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else if (character == '"') {
                    quoted = false;
                } else {
                    field.append(character);
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (character == '\n' || character == '\r') {
                if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                fields.add(field.toString());
                field.setLength(0);
                records.add(fields);
                fields = new ArrayList<>();
            } else {
                field.append(character);
            }
        }
        if (!field.isEmpty() || !fields.isEmpty()) {
            fields.add(field.toString());
            records.add(fields);
        }
        // LinkedIn puts notes above some files' headers: the header is the first line naming a column that is read.
        int header = 0;
        while (header < records.size() && records.get(header).stream().noneMatch(cell -> cell.strip().matches("(?i)(first name|name|number|" +
            "company name|title|skill|language|authority)"))) {
            header++;
        }
        if (header >= records.size()) {
            return List.of();
        }
        List<String> names = records.get(header).stream().map(cell -> cell.strip().toLowerCase(Locale.ROOT)).toList();
        List<Map<String, String>> rows = new ArrayList<>();
        for (List<String> record : records.subList(header + 1, records.size())) {
            if (record.stream().allMatch(String::isBlank)) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < names.size() && column < record.size(); column++) {
                row.put(names.get(column), record.get(column));
            }
            rows.add(row);
        }
        return rows;
    }

    private static Map<String, Integer> months() {
        Map<String, Integer> months = new HashMap<>();
        String[][] names = {{"jan", "janv", "january", "janvier"}, {"feb", "fév", "fev", "févr", "fevr", "february", "février"},
            {"mar", "mars", "march"}, {"apr", "avr", "april", "avril"}, {"may", "mai"}, {"jun", "juin", "june"},
            {"jul", "juil", "july", "juillet"}, {"aug", "août", "aout", "august"}, {"sep", "sept", "september", "septembre"},
            {"oct", "october", "octobre"}, {"nov", "november", "novembre"}, {"dec", "déc", "december", "décembre"}};
        for (int month = 0; month < names.length; month++) {
            for (String name : names[month]) {
                months.put(name, month + 1);
            }
        }
        return Map.copyOf(months);
    }
}
