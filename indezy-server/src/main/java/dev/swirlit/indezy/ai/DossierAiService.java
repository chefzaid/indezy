package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierLayout;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.service.ActivityReportService;
import dev.swirlit.indezy.service.DossierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The dossier de compétences tailored to an opportunity, a feed offer or a pasted job posting (see
 * docs/features.md#ai-features), none of them added to the pipeline for it: the same profile, skills,
 * certifications and won missions, with the summary rewritten for the opportunity, the skills it asks for first and each
 * mission's description bringing out what matters for it. The AI only rewords what the dossier already says: a skill it
 * names that the profile lacks is dropped, a mission without a description gets none, and nothing else changes.
 */
@Service
@RequiredArgsConstructor
public class DossierAiService {

    /** At most this many of the latest missions are rewritten; older ones stay as they are. */
    static final int MISSIONS_MAX = 15;
    static final int SUMMARY_MAX = 900;
    private static final int DESCRIPTION_MAX = 6_000;
    private static final long MAX_TOKENS = 8_000;
    private static final String SYSTEM = """
        You tailor a French freelance IT consultant's skills profile (dossier de compétences) to one mission they apply to.
        Reword only what the profile says: never add a skill, a tool, a figure, a client, a responsibility or a result that is
        not written in it. Bring out what matters for the mission, put first what it asks for, keep the facts. Write in the
        language asked, in the third person or without a subject as French dossiers do, plainly, without superlatives.""";

    private final AiService ai;
    private final ProjectRepository projects;
    private final JobOfferRepository offers;
    private final DossierService dossiers;

    /** What the dossier is tailored to: an opportunity, a feed offer or a pasted posting (its text as the description). */
    public record Target(String role, String stack, String description) {

        static Target of(Project project) {
            return new Target(project.getRole(), project.getTechStack(), project.getDescription());
        }

        static Target of(JobOffer offer) {
            return new Target(offer.getTitle(), String.join(", ", offer.getSkills()), offer.getDescription());
        }

        static Target posting(String text) {
            return new Target(null, null, text);
        }
    }

    /** The rewording the AI proposes. */
    public record Tailoring(
        @JsonPropertyDescription("The profile's summary rewritten for this mission, at most 600 characters, from the profile's facts only")
        String summary,
        @JsonPropertyDescription("The profile's skill names, the ones the mission asks for first, spelled exactly as given")
        List<String> skills,
        @JsonPropertyDescription("For each past mission given with a description, that description rewritten for this mission")
        List<MissionText> missions) {

        public Tailoring {
            skills = skills == null ? List.of() : List.copyOf(skills);
            missions = missions == null ? List.of() : List.copyOf(missions);
        }
    }

    /** A past mission's rewritten description, by its number in the profile. */
    public record MissionText(
        @JsonPropertyDescription("The mission's number as given") int number,
        @JsonPropertyDescription("Its description rewritten, at most 500 characters, from its own facts only") String description) {
    }

    @Transactional(readOnly = true)
    public ActivityReportService.PdfFile pdf(Long userId, Long freelanceId, Long projectId, DossierLayout layout, DocumentLanguage language) {
        Project project = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        return pdf(userId, freelanceId, Target.of(project), layout, language);
    }

    /** Tailored to an offer of the feed, without adding it to the pipeline. */
    @Transactional(readOnly = true)
    public ActivityReportService.PdfFile offerPdf(Long userId, Long freelanceId, Long offerId, DossierLayout layout, DocumentLanguage language) {
        JobOffer offer = offers.findById(offerId).orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
        return pdf(userId, freelanceId, Target.of(offer), layout, language);
    }

    /** Tailored to a job posting pasted as text or read from its PDF; nothing is kept. */
    @Transactional(readOnly = true)
    public ActivityReportService.PdfFile postingPdf(Long userId, Long freelanceId, String text, DossierLayout layout, DocumentLanguage language) {
        return pdf(userId, freelanceId, Target.posting(text), layout, language);
    }

    /** Tailored to an opportunity, block by block before and after, to review before downloading it. */
    @Transactional(readOnly = true)
    public CvRewriteAiService.CvRewrite reviewProject(Long userId, Long freelanceId, Long projectId, DocumentLanguage language) {
        Project project = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        return review(userId, freelanceId, Target.of(project), language);
    }

    /** Tailored to a feed offer, block by block, the offer left out of the pipeline. */
    @Transactional(readOnly = true)
    public CvRewriteAiService.CvRewrite reviewOffer(Long userId, Long freelanceId, Long offerId, DocumentLanguage language) {
        JobOffer offer = offers.findById(offerId).orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
        return review(userId, freelanceId, Target.of(offer), language);
    }

    /** Tailored to a pasted posting, block by block; nothing is kept. */
    @Transactional(readOnly = true)
    public CvRewriteAiService.CvRewrite reviewPosting(Long userId, Long freelanceId, String text, DocumentLanguage language) {
        return review(userId, freelanceId, Target.posting(text), language);
    }

    /**
     * The tailoring as the whole-CV rewrite shows it: the summary, the skill order and the missions before and after (the
     * headline and the past positions as they are), rendered as reviewed by {@link CvRewriteAiService#pdf}. Anonymized, as
     * what the AI reads is.
     */
    CvRewriteAiService.CvRewrite review(Long userId, Long freelanceId, Target target, DocumentLanguage language) {
        DossierDto dossier = dossiers.dossier(userId, freelanceId, DossierLayout.ANONYMOUS);
        Tailoring tailoring = ai.structured(userId, AiFeature.DOSSIER, SYSTEM, context(target, dossier, language), Tailoring.class,
            MAX_TOKENS, OutputConfig.Effort.LOW);
        return CvRewriteAiService.review(dossier, List.of(), new CvRewriteAiService.Rewriting(null, tailoring.summary(), tailoring.skills(),
            tailoring.missions().stream().filter(Objects::nonNull).map(text -> new CvRewriteAiService.Text(text.number(), text.description()))
                .toList(), List.of()));
    }

    private ActivityReportService.PdfFile pdf(Long userId, Long freelanceId, Target target, DossierLayout layout, DocumentLanguage language) {
        return dossiers.render(tailored(userId, freelanceId, target, layout, language), language);
    }

    DossierDto tailored(Long userId, Long freelanceId, Target target, DossierLayout layout, DocumentLanguage language) {
        DossierDto dossier = dossiers.dossier(userId, freelanceId, layout);
        Tailoring tailoring = ai.structured(userId, AiFeature.DOSSIER, SYSTEM, context(target, dossier, language), Tailoring.class,
            MAX_TOKENS, OutputConfig.Effort.LOW);
        return apply(dossier, tailoring);
    }

    /** What the AI reads: the mission, then the profile without its author's name or contact, nor its clients. */
    static String context(Target target, DossierDto dossier, DocumentLanguage language) {
        String description = Objects.toString(target.description(), "");
        StringBuilder context = new StringBuilder("Answer in ").append(language == DocumentLanguage.EN ? "English" : "French").append(".\n<mission>\n")
            .append("Role: ").append(Objects.toString(target.role(), "")).append('\n')
            .append("Stack: ").append(Objects.toString(target.stack(), "")).append('\n')
            .append("Description: ").append(description, 0, Math.min(description.length(), DESCRIPTION_MAX)).append("\n</mission>\n<profile>\n")
            .append("Position: ").append(Objects.toString(dossier.position(), "")).append('\n')
            .append("Summary: ").append(Objects.toString(dossier.summary(), "")).append('\n')
            .append("Skills:\n");
        dossier.skills().forEach(skill -> context.append("- ").append(skill.name())
            .append(skill.level() == null ? "" : ", " + skill.level().name().toLowerCase(Locale.ROOT))
            .append(skill.years() == null ? "" : ", " + skill.years() + " years").append('\n'));
        context.append("Languages: ").append(String.join(", ", dossier.languages())).append('\n')
            .append("Certifications: ").append(String.join(", ", dossier.certifications())).append('\n')
            .append("Past missions:\n");
        List<DossierDto.Mission> missions = dossier.missions();
        for (int index = 0; index < Math.min(missions.size(), MISSIONS_MAX); index++) {
            DossierDto.Mission mission = missions.get(index);
            context.append(index + 1).append(". ").append(Objects.toString(mission.role(), "")).append(" (")
                .append(Objects.toString(mission.startsOn(), "")).append(" to ").append(Objects.toString(mission.endsOn(), "now")).append(")")
                .append(mission.stack() == null ? "" : ", stack: " + mission.stack()).append('\n')
                .append("   Description: ").append(Objects.toString(mission.description(), "none")).append('\n');
        }
        return context.append("</profile>\n").toString();
    }

    /** The dossier with the AI's rewording, keeping only what it may change. */
    static DossierDto apply(DossierDto dossier, Tailoring tailoring) {
        String summary = clean(tailoring.summary());
        if (summary != null && summary.length() > SUMMARY_MAX) {
            int cut = summary.lastIndexOf(' ', SUMMARY_MAX);
            summary = summary.substring(0, cut > SUMMARY_MAX / 2 ? cut : SUMMARY_MAX).strip() + "…";
        }
        Map<String, DossierDto.Skill> byName = new LinkedHashMap<>();
        dossier.skills().forEach(skill -> byName.putIfAbsent(skill.name().toLowerCase(Locale.ROOT), skill));
        List<DossierDto.Skill> skills = new ArrayList<>();
        tailoring.skills().stream().filter(Objects::nonNull).map(name -> byName.remove(name.strip().toLowerCase(Locale.ROOT)))
            .filter(Objects::nonNull).forEach(skills::add);
        skills.addAll(byName.values());

        Map<Integer, String> texts = new LinkedHashMap<>();
        tailoring.missions().stream().filter(Objects::nonNull)
            .forEach(text -> texts.putIfAbsent(text.number(), DossierService.excerpt(text.description())));
        List<DossierDto.Mission> missions = new ArrayList<>();
        for (int index = 0; index < dossier.missions().size(); index++) {
            DossierDto.Mission mission = dossier.missions().get(index);
            String text = index < MISSIONS_MAX && mission.description() != null ? texts.get(index + 1) : null;
            missions.add(text == null ? mission :
                new DossierDto.Mission(mission.role(), mission.client(), mission.startsOn(), mission.endsOn(), mission.stack(), text));
        }
        // A profile with nothing in it gets no summary made up for it.
        boolean empty = dossier.summary() == null && dossier.skills().isEmpty() && dossier.missions().isEmpty();
        return new DossierDto(dossier.name(), dossier.position(), dossier.city(), dossier.contact(),
            summary == null || empty ? dossier.summary() : summary, skills, dossier.languages(), dossier.certifications(), missions,
            dossier.positions(), dossier.education());
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
