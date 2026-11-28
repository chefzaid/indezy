package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.dto.CareerDtos;
import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierLayout;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.ActivityReportService;
import dev.swirlit.indezy.service.CareerService;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The whole CV rewritten for a positioning and a style, tied to no opportunity (see docs/features.md#ai-features): the
 * headline, the summary, the skill order, the won missions' and the past positions' descriptions, each shown before and
 * after for the freelancer to review; then the dossier de compétences as reviewed, and the approved wording written back
 * to the profile (headline, summary, past positions; a won mission's description stays the opportunity's). The AI only
 * rewords what the profile says: a skill it names that the profile lacks is dropped, a block without text gets none.
 */
@Service
@RequiredArgsConstructor
public class CvRewriteAiService {

    /** At most this many of the latest missions and positions are rewritten; older ones stay as they are. */
    static final int ITEMS_MAX = 15;
    static final int HEADLINE_MAX = 120;
    static final int POSITIONING_MAX = 120;
    private static final int EXPERIENCE_DESCRIPTION_MAX = 2_000;
    private static final long MAX_TOKENS = 10_000;
    /** A block refined is at most this long, as a past position's description is. */
    public static final int REFINE_TEXT_MAX = EXPERIENCE_DESCRIPTION_MAX;
    public static final int FOCUS_MAX = 80;
    public static final int LOCKED_MAX = 10;
    public static final int LOCKED_LENGTH = 200;
    /** The freelancer's answer to an impact question, woven into a block. */
    public static final int ANSWER_MAX = 500;
    public static final int QUESTIONS_MAX = 5;
    public static final int QUESTION_BLOCKS_MAX = 30;
    public static final int BLOCK_KEY_MAX = 40;
    private static final int QUESTION_MAX = 300;
    private static final String QUESTIONS_SYSTEM = """
        You read blocks of a French freelance IT consultant's CV (dossier de compétences) and find the vague ones: a claim
        without its scale, a result without its measure, a migration without what it changed. For at most five of them, ask
        the freelancer one short question each that would let them add a fact they know (how many users, services or
        people, what gain in time, cost or latency, what scope), only about what the block states. Never suggest a figure
        or an answer. Ask in the language asked, plainly. Skip blocks that are already precise.""";
    private static final long REFINE_TOKENS = 2_000;
    private static final String REFINE_SYSTEM = """
        You refine one block of a French freelance IT consultant's CV (dossier de compétences) as asked. Reword only what the
        block says: never add a skill, a tool, a figure, a client, a date, a responsibility or a result that is not written in
        it, or in the freelancer's answer when one is given. Keep every phrase given as locked exactly as written. Write in the language asked, in the third person or without a
        subject as French dossiers do, plainly, without superlatives. Answer with the block's new text only.""";
    private static final String SYSTEM = """
        You rewrite a French freelance IT consultant's whole CV (dossier de compétences) for the positioning and the style
        asked, tied to no mission in particular. Reword only what the CV says: never add a skill, a tool, a figure, a client,
        a date, a responsibility or a result that is not written in it. Write a headline of a few words for the positioning,
        rewrite the summary, order the skills with those the positioning calls for first, and rewrite each past mission's and
        each past position's description that is given, bringing out what matters for the positioning. Write in the language
        asked, in the third person or without a subject as French dossiers do, plainly, without superlatives.""";

    /** How the CV is written. */
    public enum Style {
        /** One or two pages: short sentences, the essentials. */
        CONCISE,
        /** Outcomes first, where the CV states them. */
        IMPACT,
        /** The technical depth: stacks, architecture, practices. */
        TECHNICAL,
        /** For a non-technical client or a manager: plain words, the business at stake. */
        EXECUTIVE
    }

    private final AiService ai;
    private final DossierService dossiers;
    private final CareerService career;
    private final UserRepository users;

    /** The rewording the AI proposes. */
    public record Rewriting(
        @JsonPropertyDescription("A headline of a few words for the positioning, at most 100 characters, from the CV's facts") String headline,
        @JsonPropertyDescription("The CV's summary rewritten, at most 600 characters, from its facts only") String summary,
        @JsonPropertyDescription("The CV's skill names, those the positioning calls for first, spelled exactly as given") List<String> skills,
        @JsonPropertyDescription("For each past mission given with a description, that description rewritten") List<Text> missions,
        @JsonPropertyDescription("For each past position given with a description, that description rewritten") List<Text> positions) {

        public Rewriting {
            skills = skills == null ? List.of() : List.copyOf(skills);
            missions = missions == null ? List.of() : List.copyOf(missions);
            positions = positions == null ? List.of() : List.copyOf(positions);
        }
    }

    /** A rewritten description, by its number in the CV. */
    public record Text(
        @JsonPropertyDescription("The item's number as given") int number,
        @JsonPropertyDescription("Its description rewritten, at most 500 characters, from its own facts only") String description) {
    }

    /** A block of the CV as it is and as rewritten; {@code after} equal to {@code before} when left as it was. */
    public record Block(String before, String after) {
    }

    /**
     * A mission or position, by its number (and, for a position, its id in the profile), as it is and as rewritten, with
     * its stack: what the rewording may name besides its own text.
     */
    public record Item(int number, Long id, String title, String before, String after, String stack) {
    }

    /**
     * What an opportunity, an offer or a posting asks for, against the profile: the skills the profile lists, the ones
     * only its missions or positions name, and the ones it does not hold at all (never written into the CV).
     */
    public record Coverage(List<String> covered, List<String> partial, List<String> gaps) {

        public Coverage {
            covered = List.copyOf(covered);
            partial = List.copyOf(partial);
            gaps = List.copyOf(gaps);
        }
    }

    /** The CV rewritten, block by block, to review; {@code coverage} only when tailored to what something asks for. */
    public record CvRewrite(Block headline, Block summary, List<String> skillsBefore, List<String> skills, List<Item> missions,
                            List<Item> positions, Coverage coverage) {

        public CvRewrite {
            skillsBefore = List.copyOf(skillsBefore);
            skills = List.copyOf(skills);
            missions = List.copyOf(missions);
            positions = List.copyOf(positions);
        }

        CvRewrite with(Coverage covered) {
            return new CvRewrite(headline, summary, skillsBefore, skills, missions, positions, covered);
        }
    }

    /** The wording the freelancer kept, to render: blocks left null stay as the profile says. */
    public record Reviewed(String headline, String summary, List<String> skills, List<Text> missions, List<Text> positions) {

        public Reviewed {
            skills = skills == null ? List.of() : List.copyOf(skills);
            missions = missions == null ? List.of() : List.copyOf(missions);
            positions = positions == null ? List.of() : List.copyOf(positions);
        }
    }

    /** The wording approved for the profile: a null block is left as it is. */
    public record Approved(String headline, String summary, List<PositionText> positions) {

        public Approved {
            positions = positions == null ? List.of() : List.copyOf(positions);
        }
    }

    /** A past position's approved description, by its id in the profile. */
    public record PositionText(Long id, String description) {
    }

    /** A one-click refinement of a single block. */
    public enum Refinement {
        SHORTER, TECHNICAL, PLAIN, TRANSLATE, HIGHLIGHT,
        /** Weaves in the freelancer's answer to an impact question. */
        WEAVE
    }

    /** A block of the CV as it reads, by its key in the review. */
    public record BlockText(String key, String text) {
    }

    /** An impact question on a block, by its key. */
    public record Question(@JsonPropertyDescription("The block's key as given") String key,
                           @JsonPropertyDescription("One short question to the freelancer, in the language asked") String question) {
    }

    /** The questions the AI asks. */
    public record Questions(@JsonPropertyDescription("At most five questions, one per vague block") List<Question> questions) {

        public Questions {
            questions = questions == null ? List.of() : List.copyOf(questions);
        }
    }

    /**
     * Questions on the vague blocks (at most {@value #QUESTIONS_MAX}, one per block given), for the freelancer to answer
     * with facts they know; the AI never suggests the answer.
     */
    public List<Question> questions(Long userId, List<BlockText> blocks, DocumentLanguage language) {
        List<BlockText> given = blocks.stream().filter(block -> block != null && block.key() != null && block.text() != null && !block.text().isBlank())
            .limit(QUESTION_BLOCKS_MAX).toList();
        if (given.isEmpty()) {
            return List.of();
        }
        StringBuilder context = new StringBuilder("Answer in ").append(language == DocumentLanguage.EN ? "English" : "French").append(".\n");
        given.forEach(block -> context.append("<block key=\"").append(block.key().strip()).append("\">\n").append(block.text().strip())
            .append("\n</block>\n"));
        Questions asked = ai.structured(userId, AiFeature.DOSSIER, QUESTIONS_SYSTEM, context.toString(), Questions.class, REFINE_TOKENS,
            OutputConfig.Effort.LOW);
        Set<String> keys = given.stream().map(block -> block.key().strip()).collect(Collectors.toSet());
        Map<String, Question> byKey = new LinkedHashMap<>();
        asked.questions().stream().filter(question -> question != null && question.key() != null && keys.contains(question.key().strip()))
            .map(question -> new Question(question.key().strip(), cut(clean(question.question()), QUESTION_MAX)))
            .filter(question -> question.question() != null).forEach(question -> byKey.putIfAbsent(question.key(), question));
        return byKey.values().stream().limit(QUESTIONS_MAX).toList();
    }

    /** The block refined. */
    public record Refined(@JsonPropertyDescription("The block's new text") String text) {
    }

    /**
     * One block refined as asked ({@code focus}: what to bring out, for {@link Refinement#HIGHLIGHT}), keeping the
     * {@code locked} phrases exactly; refused when the AI dropped one or wrote nothing.
     */
    public String refine(Long userId, String text, Refinement refinement, String focus, List<String> locked, DocumentLanguage language) {
        return refine(userId, text, refinement, focus, null, locked, language);
    }

    /** As above, {@code answer} the freelancer's answer to an impact question, woven in for {@link Refinement#WEAVE}. */
    public String refine(Long userId, String text, Refinement refinement, String focus, String answer, List<String> locked,
                         DocumentLanguage language) {
        List<String> kept = locked == null ? List.of() : locked.stream().filter(Objects::nonNull).map(String::strip)
            .filter(phrase -> !phrase.isEmpty() && phrase.length() <= LOCKED_LENGTH && text.contains(phrase)).distinct().limit(LOCKED_MAX).toList();
        String context = refineContext(text, refinement, focus, kept, language);
        String told = Objects.toString(answer, "").strip();
        if (refinement == Refinement.WEAVE && !told.isEmpty()) {
            context += "<answer>\n" + told.substring(0, Math.min(told.length(), ANSWER_MAX)) + "\n</answer>\n";
        }
        Refined refined = ai.structured(userId, AiFeature.DOSSIER, REFINE_SYSTEM, context,
            Refined.class, REFINE_TOKENS, OutputConfig.Effort.LOW);
        String result = cut(clean(refined.text()), REFINE_TEXT_MAX);
        if (result == null || kept.stream().anyMatch(phrase -> !result.contains(phrase))) {
            throw new InvalidStateException("The AI changed a locked phrase: refine the block again or edit it by hand");
        }
        return result;
    }

    static String refineContext(String text, Refinement refinement, String focus, List<String> locked, DocumentLanguage language) {
        String english = language == DocumentLanguage.EN ? "English" : "French";
        String target = Objects.toString(focus, "").strip();
        String asked = switch (refinement == null ? Refinement.SHORTER : refinement) {
            case SHORTER -> "Make it shorter, keeping the facts that matter most.";
            case TECHNICAL -> "Make it more technical: bring out the stacks, architecture and practices it names.";
            case PLAIN -> "Use less jargon: plain words a non-technical client understands.";
            case TRANSLATE -> "Translate it into " + english + ", keeping its meaning and its technical terms.";
            case WEAVE -> "Weave the freelancer's answer below into the block, plainly, adding nothing else.";
            case HIGHLIGHT -> "Bring out " + (target.isEmpty() ? "what matters most" : target.substring(0, Math.min(target.length(), FOCUS_MAX))) +
                ", only where the block states it.";
        };
        StringBuilder context = new StringBuilder("Answer in ").append(english).append(".\n").append(asked).append("\n<block>\n")
            .append(text.strip()).append("\n</block>\n");
        if (!locked.isEmpty()) {
            context.append("<locked>\n");
            locked.forEach(phrase -> context.append("- ").append(phrase).append('\n'));
            context.append("</locked>\n");
        }
        return context.toString();
    }

    /** The whole CV rewritten for {@code positioning} in {@code style}, to review block by block. */
    @Transactional(readOnly = true)
    public CvRewrite rewrite(Long userId, Long freelanceId, String positioning, Style style, DocumentLanguage language) {
        // Anonymized: the AI reads neither the author's name and contact, nor the clients and employers.
        DossierDto dossier = dossiers.dossier(userId, freelanceId, DossierLayout.ANONYMOUS);
        List<CareerDtos.Experience> experiences = career.experiences(freelanceId);
        Rewriting rewriting = ai.structured(userId, AiFeature.DOSSIER, SYSTEM, context(dossier, positioning, style, language), Rewriting.class,
            MAX_TOKENS, OutputConfig.Effort.LOW);
        return review(dossier, experiences, rewriting);
    }

    /** What the AI reads: the positioning and style, then the CV without its author's name or contact, nor its clients. */
    static String context(DossierDto dossier, String positioning, Style style, DocumentLanguage language) {
        String target = Objects.toString(positioning, "").strip();
        StringBuilder context = new StringBuilder("Answer in ").append(language == DocumentLanguage.EN ? "English" : "French").append(".\n")
            .append("<positioning>").append(target.isEmpty() ? "as the CV stands" : target.substring(0, Math.min(target.length(), POSITIONING_MAX)))
            .append("</positioning>\n<style>").append(styleText(style)).append("</style>\n<cv>\n")
            .append("Position: ").append(Objects.toString(dossier.position(), "")).append('\n')
            .append("Summary: ").append(Objects.toString(dossier.summary(), "")).append('\n')
            .append("Skills:\n");
        dossier.skills().forEach(skill -> context.append("- ").append(skill.name())
            .append(skill.level() == null ? "" : ", " + skill.level().name().toLowerCase(Locale.ROOT))
            .append(skill.years() == null ? "" : ", " + skill.years() + " years").append('\n'));
        context.append("Languages: ").append(String.join(", ", dossier.languages())).append('\n')
            .append("Certifications: ").append(String.join(", ", dossier.certifications())).append('\n');
        items(context, "Past missions", dossier.missions());
        items(context, "Past positions", dossier.positions());
        return context.append("</cv>\n").toString();
    }

    private static void items(StringBuilder context, String title, List<DossierDto.Mission> items) {
        context.append(title).append(":\n");
        for (int index = 0; index < Math.min(items.size(), ITEMS_MAX); index++) {
            DossierDto.Mission item = items.get(index);
            context.append(index + 1).append(". ").append(Objects.toString(item.role(), "")).append(" (")
                .append(Objects.toString(item.startsOn(), "")).append(" to ").append(Objects.toString(item.endsOn(), "now")).append(")")
                .append(item.stack() == null ? "" : ", stack: " + item.stack()).append('\n')
                .append("   Description: ").append(Objects.toString(item.description(), "none")).append('\n');
        }
    }

    private static String styleText(Style style) {
        return switch (style == null ? Style.IMPACT : style) {
            case CONCISE -> "concise, for one or two pages: short sentences, the essentials only";
            case IMPACT -> "impact: the outcomes first, only where the CV states them";
            case TECHNICAL -> "technical depth: the stacks, architecture and practices the CV names";
            case EXECUTIVE -> "for a non-technical client or manager: plain words, the business at stake, little jargon";
        };
    }

    /** The CV before and after the AI's rewording, keeping only what it may change. */
    static CvRewrite review(DossierDto dossier, List<CareerDtos.Experience> experiences, Rewriting rewriting) {
        boolean empty = dossier.summary() == null && dossier.skills().isEmpty() && dossier.missions().isEmpty() && dossier.positions().isEmpty();
        String headline = empty ? null : cut(clean(rewriting.headline()), HEADLINE_MAX);
        String summary = empty || dossier.summary() == null ? null : cut(clean(rewriting.summary()), DossierAiService.SUMMARY_MAX);
        List<String> before = dossier.skills().stream().map(DossierDto.Skill::name).toList();
        return new CvRewrite(new Block(dossier.position(), headline == null ? dossier.position() : headline),
            new Block(dossier.summary(), summary == null ? dossier.summary() : summary), before, order(before, rewriting.skills()),
            items(dossier.missions(), null, rewriting.missions()), items(dossier.positions(), experiences, rewriting.positions()), null);
    }

    /** The profile's skills in the order given, the ones it did not name after; a name the profile lacks is dropped. */
    static List<String> order(List<String> skills, List<String> asked) {
        Map<String, String> byName = new LinkedHashMap<>();
        skills.forEach(skill -> byName.putIfAbsent(skill.toLowerCase(Locale.ROOT), skill));
        List<String> ordered = new ArrayList<>();
        asked.stream().filter(Objects::nonNull).map(name -> byName.remove(name.strip().toLowerCase(Locale.ROOT))).filter(Objects::nonNull)
            .forEach(ordered::add);
        ordered.addAll(byName.values());
        return ordered;
    }

    private static List<Item> items(List<DossierDto.Mission> items, List<CareerDtos.Experience> experiences, List<Text> texts) {
        Map<Integer, String> byNumber = new LinkedHashMap<>();
        texts.stream().filter(Objects::nonNull).forEach(text -> byNumber.putIfAbsent(text.number(), DossierService.excerpt(text.description())));
        List<Item> reviewed = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            DossierDto.Mission item = items.get(index);
            String text = index < ITEMS_MAX && item.description() != null ? byNumber.get(index + 1) : null;
            Long id = experiences != null && index < experiences.size() ? experiences.get(index).id() : null;
            reviewed.add(new Item(index + 1, id, item.role(), item.description(), text == null ? item.description() : text, item.stack()));
        }
        return reviewed;
    }

    /** The dossier de compétences with the wording the freelancer kept, as a PDF; no AI is asked. */
    @Transactional(readOnly = true)
    public ActivityReportService.PdfFile pdf(Long userId, Long freelanceId, DossierLayout layout, DocumentLanguage language, Reviewed reviewed) {
        return dossiers.render(apply(dossiers.dossier(userId, freelanceId, layout), reviewed), language);
    }

    /** The dossier with the reviewed wording, keeping only what may change: the headline, the summary, the order and the texts. */
    static DossierDto apply(DossierDto dossier, Reviewed reviewed) {
        String headline = cut(clean(reviewed.headline()), HEADLINE_MAX);
        String summary = cut(clean(reviewed.summary()), DossierAiService.SUMMARY_MAX);
        Map<String, DossierDto.Skill> byName = new LinkedHashMap<>();
        dossier.skills().forEach(skill -> byName.putIfAbsent(skill.name().toLowerCase(Locale.ROOT), skill));
        List<DossierDto.Skill> skills = new ArrayList<>();
        reviewed.skills().stream().filter(Objects::nonNull).map(name -> byName.remove(name.strip().toLowerCase(Locale.ROOT)))
            .filter(Objects::nonNull).forEach(skills::add);
        skills.addAll(byName.values());
        return new DossierDto(dossier.name(), headline == null ? dossier.position() : headline, dossier.city(), dossier.contact(),
            summary == null ? dossier.summary() : summary, skills, dossier.languages(), dossier.certifications(),
            texts(dossier.missions(), reviewed.missions()), texts(dossier.positions(), reviewed.positions()), dossier.education());
    }

    private static List<DossierDto.Mission> texts(List<DossierDto.Mission> items, List<Text> texts) {
        Map<Integer, String> byNumber = new LinkedHashMap<>();
        texts.stream().filter(Objects::nonNull).forEach(text -> byNumber.putIfAbsent(text.number(), DossierService.excerpt(text.description())));
        List<DossierDto.Mission> written = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            DossierDto.Mission item = items.get(index);
            String text = byNumber.get(index + 1);
            written.add(text == null ? item : new DossierDto.Mission(item.role(), item.client(), item.startsOn(), item.endsOn(), item.stack(), text));
        }
        return written;
    }

    /** Writes the approved headline, summary and past positions' descriptions to the profile. */
    @Transactional
    public void approve(Long userId, Long freelanceId, Approved approved) {
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String headline = cut(clean(approved.headline()), HEADLINE_MAX);
        String summary = cut(clean(approved.summary()), DossierAiService.SUMMARY_MAX);
        if (headline != null) {
            user.setPosition(headline);
        }
        if (summary != null) {
            user.setBio(summary);
        }
        Map<Long, CareerDtos.Experience> byId = new LinkedHashMap<>();
        career.experiences(freelanceId).forEach(experience -> byId.put(experience.id(), experience));
        for (PositionText text : approved.positions()) {
            CareerDtos.Experience experience = text == null ? null : byId.get(text.id());
            String description = text == null ? null : cut(clean(text.description()), EXPERIENCE_DESCRIPTION_MAX);
            if (experience == null || description == null) {
                continue;
            }
            career.updateExperience(freelanceId, experience.id(), new CareerDtos.ExperienceDetails(experience.employer(), experience.title(),
                experience.startsOn(), experience.endsOn(), description, experience.stack(), experience.shownPublicly()));
        }
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static String cut(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        int cut = text.lastIndexOf(' ', max);
        return text.substring(0, cut > max / 2 ? cut : max).strip() + "…";
    }
}
