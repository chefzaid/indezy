package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierLayout;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.util.TextFolding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks a CV profile's dossier before it goes out (see docs/features.md#cv-profiles), with no AI: for an anonymized one,
 * that no client, contact, employer, nor the freelancer's own name or contact details is named in its text; then weak
 * phrasing, missions without a figure, gaps between missions and positions, blocks written in the other language, the
 * freelance essentials missing (summary, skills, city, availability) and its length.
 */
@Service
@RequiredArgsConstructor
public class CvAuditService {

    /** A gap longer than this between missions and positions is pointed out. */
    static final int GAP_DAYS = 92;
    /** About this many characters of text fill an A4 page of the dossier. */
    static final int PAGE_CHARACTERS = 2_800;
    static final int PAGES_MAX = 3;
    private static final int FIGURE_MISSIONS = 5;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final List<String> WEAK = List.of("participe a", "participation a", "aide a", "contribue a", "travaille sur", "implique dans",
        "participated in", "helped", "worked on", "involved in", "contributed to", "responsible for");
    private static final Set<String> FRENCH = Set.of("le", "la", "les", "des", "du", "de", "et", "un", "une", "pour", "avec", "sur", "dans", "est", "au",
        "aux", "en");
    private static final Set<String> ENGLISH = Set.of("the", "and", "of", "for", "with", "on", "in", "to", "a", "an", "is", "was", "by");

    private final CvProfileService profiles;
    private final DossierService dossiers;
    private final ClientRepository clients;
    private final ContactRepository contacts;
    private final FreelanceRepository freelances;
    private final Clock clock;

    /** What is checked. */
    public enum Check {
        /** A client, contact, employer, or the freelancer's own name or contact detail in an anonymized dossier. */
        LEAK, WEAK_PHRASING, NO_FIGURE, DATE_GAP, OTHER_LANGUAGE, MISSING_SUMMARY, NO_SKILLS, MISSING_CITY, MISSING_AVAILABILITY, NO_DESCRIPTION, LONG
    }

    public enum Severity { ERROR, WARNING, INFO }

    /** One finding: what, how serious, in which block ("summary", a mission's role) and its detail (the name, the phrase, the dates). */
    public record Finding(Check check, Severity severity, String block, String detail) {
    }

    /** The audit: the dossier's estimated length in pages, and the findings, the most serious first. */
    public record Audit(int pages, List<Finding> findings) {

        public Audit {
            findings = List.copyOf(findings);
        }

        public boolean leaks() {
            return findings.stream().anyMatch(finding -> finding.check() == Check.LEAK);
        }
    }

    /** The profile's dossier checked, named or anonymized. */
    @Transactional(readOnly = true)
    public Audit audit(Long userId, Long freelanceId, Long profileId, DossierLayout layout) {
        DossierDto dossier = profiles.shapedDossier(userId, freelanceId, profileId, layout);
        DocumentLanguage language = profiles.list(freelanceId).stream().filter(profile -> profile.id().equals(profileId)).findFirst()
            .map(profile -> profile.language()).orElse(DocumentLanguage.FR);
        List<String> sensitive = layout == DossierLayout.ANONYMOUS ? sensitive(userId, freelanceId) : List.of();
        return audit(dossier, language, sensitive, freelances.findAvailabilityDate(freelanceId).orElse(null), LocalDate.now(clock.withZone(PARIS)));
    }

    /** What an anonymized dossier must not name: the workspace's clients and contacts, the employers, the freelancer's name and contact. */
    private List<String> sensitive(Long userId, Long freelanceId) {
        DossierDto named = dossiers.dossier(userId, freelanceId, DossierLayout.NAMED);
        Set<String> terms = new LinkedHashSet<>();
        clients.findByFreelanceId(freelanceId).stream().map(Client::getCompanyName).forEach(terms::add);
        for (Contact contact : contacts.findByFreelanceId(freelanceId)) {
            terms.add(Stream.of(contact.getFirstName(), contact.getLastName()).filter(Objects::nonNull).reduce((first, last) -> first + " " + last)
                .orElse(null));
            terms.add(contact.getLastName());
        }
        Stream.concat(named.missions().stream(), named.positions().stream()).map(DossierDto.Mission::client).forEach(terms::add);
        terms.add(named.name());
        List.of(named.name().split("\\s+")).forEach(terms::add);
        terms.addAll(named.contact());
        return terms.stream().filter(Objects::nonNull).map(String::strip).filter(term -> term.length() >= 3).toList();
    }

    /** The checks themselves, on a dossier as drawn. */
    static Audit audit(DossierDto dossier, DocumentLanguage language, List<String> sensitive, LocalDate available, LocalDate today) {
        List<Finding> findings = new ArrayList<>();
        List<Block> blocks = blocks(dossier);
        for (String term : sensitive) {
            Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(TextFolding.fold(term)) + "(?![\\p{L}\\p{N}])");
            blocks.stream().filter(block -> pattern.matcher(TextFolding.fold(block.text())).find()).findFirst()
                .ifPresent(block -> findings.add(new Finding(Check.LEAK, Severity.ERROR, block.label(), term)));
        }
        for (Block block : blocks) {
            if (!block.wording()) {
                continue;
            }
            String folded = " " + TextFolding.words(block.text()) + " ";
            WEAK.stream().filter(phrase -> folded.contains(" " + phrase + " ")).findFirst()
                .ifPresent(phrase -> findings.add(new Finding(Check.WEAK_PHRASING, Severity.WARNING, block.label(), phrase)));
            otherLanguage(block.text(), language).ifPresent(other ->
                findings.add(new Finding(Check.OTHER_LANGUAGE, Severity.WARNING, block.label(), other.name())));
        }
        dossier.missions().stream().limit(FIGURE_MISSIONS).filter(mission -> mission.description() != null && !mission.description().matches("(?s).*\\d.*"))
            .forEach(mission -> findings.add(new Finding(Check.NO_FIGURE, Severity.INFO, mission.role(), null)));
        Stream.concat(dossier.missions().stream(), dossier.positions().stream()).filter(mission -> mission.description() == null)
            .forEach(mission -> findings.add(new Finding(Check.NO_DESCRIPTION, Severity.INFO, mission.role(), null)));
        gaps(dossier, today).forEach(findings::add);
        if (dossier.summary() == null) {
            findings.add(new Finding(Check.MISSING_SUMMARY, Severity.WARNING, "summary", null));
        }
        if (dossier.skills().isEmpty()) {
            findings.add(new Finding(Check.NO_SKILLS, Severity.WARNING, "skills", null));
        }
        if (dossier.city() == null) {
            findings.add(new Finding(Check.MISSING_CITY, Severity.INFO, null, null));
        }
        if (available == null) {
            findings.add(new Finding(Check.MISSING_AVAILABILITY, Severity.INFO, null, null));
        }
        int characters = blocks.stream().mapToInt(block -> block.text().length()).sum();
        int pages = Math.max(1, (int) Math.ceil(characters / (double) PAGE_CHARACTERS));
        if (pages > PAGES_MAX) {
            findings.add(new Finding(Check.LONG, Severity.WARNING, null, String.valueOf(pages)));
        }
        findings.sort(Comparator.comparing(Finding::severity));
        return new Audit(pages, findings);
    }

    /** A block of the dossier's text, by its label; {@code wording}: written by the freelancer, rather than a name or a list. */
    private record Block(String label, String text, boolean wording) {
    }

    private static List<Block> blocks(DossierDto dossier) {
        List<Block> blocks = new ArrayList<>();
        add(blocks, "headline", dossier.position(), false);
        add(blocks, "summary", dossier.summary(), true);
        Stream.concat(dossier.missions().stream(), dossier.positions().stream()).forEach(mission -> {
            add(blocks, mission.role(), mission.role(), false);
            add(blocks, mission.role(), mission.description(), true);
            add(blocks, mission.role(), mission.stack(), false);
        });
        add(blocks, "certifications", String.join(", ", dossier.certifications()), false);
        return blocks;
    }

    private static void add(List<Block> blocks, String label, String text, boolean wording) {
        if (text != null && !text.isBlank()) {
            blocks.add(new Block(label == null ? "" : label, text, wording));
        }
    }

    /** The other language when a block of at least six words reads clearly in it rather than in the dossier's. */
    static java.util.Optional<DocumentLanguage> otherLanguage(String text, DocumentLanguage language) {
        List<String> words = List.of(TextFolding.words(text).split(" "));
        if (words.size() < 6) {
            return java.util.Optional.empty();
        }
        long french = words.stream().filter(FRENCH::contains).count();
        long english = words.stream().filter(ENGLISH::contains).count();
        if (language == DocumentLanguage.FR && english >= 3 && english > french * 2) {
            return java.util.Optional.of(DocumentLanguage.EN);
        }
        if (language == DocumentLanguage.EN && french >= 3 && french > english * 2) {
            return java.util.Optional.of(DocumentLanguage.FR);
        }
        return java.util.Optional.empty();
    }

    /** Gaps of more than {@value #GAP_DAYS} days between the missions and positions, their periods merged. */
    static List<Finding> gaps(DossierDto dossier, LocalDate today) {
        List<LocalDate[]> periods = Stream.concat(dossier.missions().stream(), dossier.positions().stream()).filter(mission -> mission.startsOn() != null)
            .map(mission -> new LocalDate[] {mission.startsOn(), mission.endsOn() == null ? today : mission.endsOn()})
            .sorted(Comparator.comparing((LocalDate[] period) -> period[0])).toList();
        List<Finding> gaps = new ArrayList<>();
        LocalDate covered = null;
        for (LocalDate[] period : periods) {
            if (covered != null && ChronoUnit.DAYS.between(covered, period[0]) > GAP_DAYS) {
                gaps.add(new Finding(Check.DATE_GAP, Severity.INFO, null, covered + " – " + period[0]));
            }
            covered = covered == null || period[1].isAfter(covered) ? period[1] : covered;
        }
        return gaps;
    }
}
