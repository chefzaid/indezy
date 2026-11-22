package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.dto.OfferRateMarketDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.OfferRateBenchmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * Prepares the application to a feed offer (see docs/features.md#ai-features): the message in the channel's own format
 * (an ESN email, a Malt proposal, a LinkedIn message of 300 characters), answers to the usual screening questions
 * (availability, notice, rate, remote) and a rate proposal with its reasons, from the offer, the profile and the feed's
 * median for its main skill. Nothing is sent or kept.
 */
@Service
@RequiredArgsConstructor
public class ApplicationPackAiService {

    /** A LinkedIn connection message holds this many characters. */
    static final int LINKEDIN_MAX = 300;
    private static final int DESCRIPTION_MAX = 5_000;
    private static final int REASONS_MAX = 3;
    private static final long MAX_TOKENS = 3_000;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final String SYSTEM = """
        You prepare a French freelance IT consultant's application to a mission offer: the message for the channel asked,
        the answers to the usual screening questions (availability, notice period, daily rate, remote work) and a daily
        rate to propose with its reasons. Use only the profile's facts: never invent experience, clients, figures or
        availability. Write in the language asked, in the first person, plainly and warmly.""";

    private final AiService ai;
    private final JobOfferRepository offers;
    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final OfferRateBenchmarkService market;
    private final Clock clock;

    /** Where the application goes. */
    public enum Channel {
        /** An email to the ESN's recruiter, with a subject. */
        ESN_EMAIL,
        /** A proposal on Malt. */
        MALT,
        /** A LinkedIn message of 300 characters at most. */
        LINKEDIN
    }

    /** A screening question and its answer. */
    public record Answer(@JsonPropertyDescription("The question, short") String question,
                         @JsonPropertyDescription("The answer, one or two sentences") String answer) {
    }

    /** What the AI answers. */
    public record Draft(
        @JsonPropertyDescription("The email subject for an ESN email, else empty") String subject,
        @JsonPropertyDescription("The message for the channel") String message,
        @JsonPropertyDescription("Answers to the screening questions: availability, notice period, daily rate, remote work") List<Answer> screening,
        @JsonPropertyDescription("The daily rate to propose, in euros") int rate,
        @JsonPropertyDescription("Up to three short reasons for the rate") List<String> rateReasons) {

        public Draft {
            screening = screening == null ? List.of() : List.copyOf(screening.stream().filter(Objects::nonNull).toList());
            rateReasons = rateReasons == null ? List.of() : List.copyOf(rateReasons.stream().filter(Objects::nonNull).toList());
        }
    }

    @Transactional(readOnly = true)
    public Draft pack(Long userId, Long freelanceId, Long offerId, Channel channel, DocumentLanguage language) {
        JobOffer offer = offers.findById(offerId).orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        LocalDate available = freelances.findAvailabilityDate(freelanceId).orElse(null);
        List<String> skills = offer.getSkills();
        OfferRateMarketDto median = skills.isEmpty() ? null : market.compare(1, 1, offer.getRegionCode(), skills.getFirst());
        Draft draft = ai.structured(userId, AiFeature.MESSAGE, SYSTEM, context(offer, user, available, median, channel, language,
            LocalDate.now(clock.withZone(PARIS))), Draft.class, MAX_TOKENS, OutputConfig.Effort.LOW);
        String message = Objects.toString(draft.message(), "").strip();
        if (channel == Channel.LINKEDIN && message.length() > LINKEDIN_MAX) {
            int cut = message.lastIndexOf(' ', LINKEDIN_MAX - 1);
            message = message.substring(0, cut > LINKEDIN_MAX / 2 ? cut : LINKEDIN_MAX - 1).strip() + "…";
        }
        return new Draft(channel == Channel.ESN_EMAIL ? Objects.toString(draft.subject(), "").strip() : "", message,
            draft.screening().stream().filter(answer -> answer.question() != null && !answer.question().isBlank())
                .map(answer -> new Answer(answer.question().strip(), Objects.toString(answer.answer(), "").strip())).toList(),
            Math.max(0, draft.rate()),
            draft.rateReasons().stream().filter(reason -> !reason.isBlank()).map(String::strip).limit(REASONS_MAX).toList());
    }

    static String context(JobOffer offer, User user, LocalDate available, OfferRateMarketDto median, Channel channel, DocumentLanguage language,
                          LocalDate today) {
        String description = Objects.toString(offer.getDescription(), "");
        String bio = Objects.toString(user.getBio(), "");
        return "Answer in " + (language == DocumentLanguage.EN ? "English" : "French") + ". Today is " + today + ".\n" +
            "Channel: " + channel(channel) + "\n<offer>\n" +
            "Title: " + Objects.toString(offer.getTitle(), "") + '\n' +
            "Company: " + Objects.toString(offer.getCompany(), "") + '\n' +
            "Rate: " + (offer.getRateMin() == null && offer.getRateMax() == null ? "not stated" :
                Objects.toString(offer.getRateMin(), "?") + " to " + Objects.toString(offer.getRateMax(), "?") + " EUR") + '\n' +
            "Work mode: " + Objects.toString(offer.getWorkMode(), "unknown") +
            (offer.getRemoteDaysPerWeek() == null ? "" : ", " + offer.getRemoteDaysPerWeek() + " remote days a week") + '\n' +
            "Start: " + Objects.toString(offer.getStartDate(), "unknown") + '\n' +
            "Skills: " + String.join(", ", offer.getSkills()) + '\n' +
            "Description: " + description.substring(0, Math.min(description.length(), DESCRIPTION_MAX)) + "\n</offer>\n<profile>\n" +
            "Name: " + Objects.toString(user.getFirstName(), "") + '\n' +
            "Position: " + Objects.toString(user.getPosition(), "") + '\n' +
            "Skills: " + String.join(", ", user.getSkills()) + '\n' +
            "Target daily rate: " + (user.getTargetDailyRate() == null ? "not set" : user.getTargetDailyRate() + " EUR") + '\n' +
            "Available from: " + (available == null ? "not set" : available) + '\n' +
            "Lives in: " + Objects.toString(user.getCity(), "") + '\n' +
            "About: " + bio.substring(0, Math.min(bio.length(), DESCRIPTION_MAX)) + "\n</profile>\n" +
            (median == null ? "" : "Market: the median daily rate of open offers naming " + median.skill() + " first is " + median.median() +
                " EUR over " + median.offers() + " offers.\n");
    }

    private static String channel(Channel channel) {
        return switch (channel) {
            case ESN_EMAIL -> "an email to the ESN's recruiter, with a subject, under 180 words";
            case MALT -> "a proposal on Malt, under 150 words, no subject";
            case LINKEDIN -> "a LinkedIn message of 300 characters at most, no subject";
        };
    }
}
