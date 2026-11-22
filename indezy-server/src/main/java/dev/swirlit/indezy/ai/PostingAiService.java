package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.model.enums.WorkMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Reads a job posting, pasted as text, into the fields of a new opportunity, inventing nothing it does not state. */
@Service
@RequiredArgsConstructor
public class PostingAiService {

    static final int TEXT_MAX = 20_000;
    private static final long MAX_TOKENS = 4_000;
    private static final int STACK_MAX = 15;
    private static final String SYSTEM = """
        You read job postings for freelance IT missions, in French or English, and fill in the fields of an opportunity.
        State only what the posting says. When it does not say something, leave the text empty, the number at 0 and the
        list empty: never guess a rate, a date or a place. Write the role and the summary in the posting's language.""";

    private final AiService ai;

    /** What the model fills in; unknown values empty or zero. */
    record Reading(
        @JsonPropertyDescription("The role, as the posting names it, e.g. 'Développeur Java Senior'") String role,
        @JsonPropertyDescription("The company that posts it (an ESN or the end client), or empty") String company,
        @JsonPropertyDescription("Whether the company posting it is an ESN, consulting firm or recruiter rather than the end client")
        boolean companyIsIntermediary,
        @JsonPropertyDescription("The end client when the posting names it apart from the company posting it, or empty") String endClient,
        @JsonPropertyDescription("The daily rate offered in euros excluding VAT, the top of a range, or 0") int dailyRate,
        @JsonPropertyDescription("The technologies and skills asked, each short (e.g. 'Java', 'Spring Boot', 'Kafka'), most important first")
        List<String> techStack,
        @JsonPropertyDescription("Where the work is done: the town, with its postal code when given, or empty") String place,
        @JsonPropertyDescription("ONSITE, HYBRID or REMOTE, as the posting says; ONSITE when it says nothing of remote work") WorkMode workMode,
        @JsonPropertyDescription("Remote days a week when it gives them, else 0") int remoteDaysPerWeek,
        @JsonPropertyDescription("The start date as YYYY-MM-DD, the first of the month when only the month is given, or empty") String startDate,
        @JsonPropertyDescription("The duration in months, rounded up, or 0") int durationInMonths,
        @JsonPropertyDescription("Three sentences at most on the mission, its context and team") String summary) {
    }

    /** The posting read: null where it says nothing. */
    public record Posting(String role, String company, boolean companyIsIntermediary, String endClient, Integer dailyRate,
                          List<String> techStack, String place, WorkMode workMode, Integer remoteDaysPerMonth, LocalDate startDate,
                          Integer durationInMonths, String summary) {

        public Posting {
            techStack = List.copyOf(techStack);
        }
    }

    public Posting read(Long userId, String text) {
        String posting = text.strip();
        if (posting.length() > TEXT_MAX) {
            throw new IllegalArgumentException("A posting of " + TEXT_MAX + " characters at most");
        }
        Reading reading = ai.structured(userId, AiFeature.POSTING, SYSTEM, "<posting>\n" + posting + "\n</posting>", Reading.class,
            MAX_TOKENS, OutputConfig.Effort.LOW);
        List<String> stack = reading.techStack() == null ? List.of() :
            reading.techStack().stream().map(String::strip).filter(skill -> !skill.isEmpty()).distinct().limit(STACK_MAX).toList();
        return new Posting(blank(reading.role()), blank(reading.company()), reading.companyIsIntermediary(), blank(reading.endClient()),
            positive(reading.dailyRate()), stack, blank(reading.place()), reading.workMode(),
            // The opportunity counts remote days a month: about four weeks.
            reading.remoteDaysPerWeek() > 0 ? Math.min(reading.remoteDaysPerWeek(), 5) * 4 : null, date(reading.startDate()),
            positive(reading.durationInMonths()), blank(reading.summary()));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Integer positive(int value) {
        return value > 0 ? value : null;
    }

    private static LocalDate date(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
