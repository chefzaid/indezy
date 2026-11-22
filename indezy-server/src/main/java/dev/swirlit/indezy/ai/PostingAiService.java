package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.model.enums.WorkMode;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/** Reads a job posting, pasted as text, into the fields of a new opportunity, inventing nothing it does not state. */
@Service
@RequiredArgsConstructor
public class PostingAiService {

    static final int TEXT_MAX = 20_000;
    static final int PDF_MAX_BYTES = 5 * 1024 * 1024;
    private static final int PDF_PAGES = 10;
    private static final Pattern LINE_SPACES = Pattern.compile("[ \\t\\x0B\\f\\u00A0]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");
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

    /** The text of a posting saved as a PDF, to read once checked: its first pages, lines kept, cut at {@link #TEXT_MAX}. */
    public static PdfText pdfText(byte[] content) {
        if (content.length == 0 || content.length > PDF_MAX_BYTES) {
            throw new FieldRejectedException("file", "A PDF of 5 MB at most");
        }
        String text;
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new FieldRejectedException("file", "This PDF is protected: save it without a password");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(PDF_PAGES);
            text = stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new FieldRejectedException("file", "This PDF is protected: save it without a password");
        } catch (IOException e) {
            throw new FieldRejectedException("file", "This file is not a PDF that can be read");
        }
        text = BLANK_LINES.matcher(LINE_SPACES.matcher(text.replace("\r", "")).replaceAll(" ").replace(" \n", "\n")).replaceAll("\n\n").strip();
        if (text.isEmpty()) {
            throw new FieldRejectedException("file", "This PDF has no text to read: it may be a scanned page");
        }
        boolean cut = text.length() > TEXT_MAX;
        return new PdfText(cut ? text.substring(0, TEXT_MAX) : text, cut);
    }

    /** A PDF's text, and whether it was cut to fit. */
    public record PdfText(String text, boolean cut) {
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
