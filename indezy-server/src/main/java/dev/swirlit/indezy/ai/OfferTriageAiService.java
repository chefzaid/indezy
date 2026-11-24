package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferVerdict;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferVerdictRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Triages a feed offer for the workspace: one line on whether it fits the profile and its deal-breakers (on site every
 * day, a rate under the target, a permanent job posted as freelance, portage required, a start date passed...), kept so
 * an offer is paid once; asked again on request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfferTriageAiService {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final int DESCRIPTION_MAX = 6_000;
    private static final int VERDICT_MAX = 300;
    private static final int BREAKER_MAX = 200;
    private static final int BREAKERS = 5;
    private static final int CLIENT_MAX = 120;
    private static final long MAX_TOKENS = 2_000;
    private static final String SYSTEM = """
        You triage freelance IT mission offers for a French freelancer. Say in one short sentence whether the offer is worth
        applying to for this profile, and list its deal-breakers: on site every day, a daily rate under the target, a
        permanent position (CDI) posted as freelance, portage or an employment contract required, a start date already
        past, skills the profile lacks that the offer requires, or anything else plainly incompatible. Only what the offer
        states; an empty list when there is none. When an ESN, a recruiting firm or a platform posted it, say in
        likelyClient which end client it is likely for: the one it names, else a short description from its clues (the
        sector, the size, the place, the project, the tools), such as "a large French bank in Paris", never a company
        name the offer does not support; empty when the end client posted it itself or nothing hints at one. Write in the
        language asked.""";

    private final AiService ai;
    private final JobOfferRepository offers;
    private final OfferVerdictRepository verdicts;
    private final UserRepository users;
    private final Clock clock;

    /** The model's answer. */
    record Triage(
        @JsonPropertyDescription("Whether it is worth applying to") boolean goodFit,
        @JsonPropertyDescription("One short sentence on why, 200 characters at most") String verdict,
        @JsonPropertyDescription("Up to five deal-breakers, each a few words, or an empty list") List<String> dealBreakers,
        @JsonPropertyDescription("The end client it is likely for, named or described, 120 characters at most; empty when unknown") String likelyClient) {

        Triage {
            dealBreakers = dealBreakers == null ? List.of() : List.copyOf(dealBreakers);
        }
    }

    /** A verdict as the offer page shows it. */
    public record Verdict(Long offerId, boolean goodFit, String verdict, List<String> dealBreakers, LocalDateTime createdAt, String likelyClient) {

        public Verdict {
            dealBreakers = List.copyOf(dealBreakers);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Verdict> stored(Long freelanceId, Long offerId) {
        return verdicts.findByFreelanceIdAndOfferId(freelanceId, offerId).map(OfferTriageAiService::toDto);
    }

    /** Triages the offer for the workspace's profile, replacing any verdict kept. */
    @Transactional
    public Verdict triage(Long userId, Long freelanceId, Long offerId, DocumentLanguage language) {
        JobOffer offer = offers.findById(offerId).orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String description = Objects.toString(offer.getDescription(), "");
        String context = "Answer in " + (language == DocumentLanguage.EN ? "English" : "French") + ". Today is " +
            LocalDate.now(clock.withZone(PARIS)) + ".\n<offer>\n" +
            "Title: " + Objects.toString(offer.getTitle(), "") + '\n' +
            "Company: " + Objects.toString(offer.getCompany(), "") + '\n' +
            "Rate: " + range(offer.getRateMin(), offer.getRateMax()) + '\n' +
            "Place: " + Objects.toString(offer.getCity(), "") + ' ' + Objects.toString(offer.getPostalCode(), "") + '\n' +
            "Work mode: " + Objects.toString(offer.getWorkMode(), "unknown") +
            (offer.getRemoteDaysPerWeek() == null ? "" : ", " + offer.getRemoteDaysPerWeek() + " remote days a week") + '\n' +
            "Start: " + Objects.toString(offer.getStartDate(), "unknown") + ", duration: " +
            (offer.getDurationMonths() == null ? "unknown" : offer.getDurationMonths() + " months") + '\n' +
            "Skills: " + String.join(", ", offer.getSkills()) + '\n' +
            "Description: " + description.substring(0, Math.min(description.length(), DESCRIPTION_MAX)) + "\n</offer>\n<profile>\n" +
            "Skills: " + String.join(", ", user.getSkills()) + '\n' +
            "Target daily rate: " + (user.getTargetDailyRate() == null ? "not set" : user.getTargetDailyRate() + " EUR") + '\n' +
            "Lives in: " + Objects.toString(user.getCity(), "") + "\n</profile>";
        Triage triage = ai.structured(userId, AiFeature.TRIAGE, SYSTEM, context, Triage.class, MAX_TOKENS, OutputConfig.Effort.LOW);
        OfferVerdict verdict = verdicts.findByFreelanceIdAndOfferId(freelanceId, offerId).orElseGet(OfferVerdict::new);
        verdict.setFreelanceId(freelanceId);
        verdict.setOfferId(offerId);
        verdict.setGoodFit(triage.goodFit());
        verdict.setVerdict(cut(Objects.toString(triage.verdict(), "").strip(), VERDICT_MAX));
        verdict.setDealBreakers(String.join("\n", triage.dealBreakers().stream().filter(item -> item != null && !item.isBlank())
            .map(item -> cut(item.strip().replace('\n', ' '), BREAKER_MAX)).limit(BREAKERS).toList()));
        String client = Objects.toString(triage.likelyClient(), "").strip().replace('\n', ' ');
        verdict.setLikelyClient(client.isEmpty() ? null : cut(client, CLIENT_MAX));
        return toDto(verdicts.save(verdict));
    }

    /** Every night, the verdicts of offers the feed removed go with them. */
    @Scheduled(cron = "0 20 5 * * *", zone = "Europe/Paris")
    public void dropOrphans() {
        int dropped = verdicts.deleteOrphans();
        if (dropped > 0) {
            log.info("Dropped {} AI verdicts of removed offers", dropped);
        }
    }

    private static String range(Integer min, Integer max) {
        if (min == null && max == null) {
            return "unknown";
        }
        return Objects.equals(min, max) || max == null ? min + " EUR" : min == null ? max + " EUR" : min + "-" + max + " EUR";
    }

    private static String cut(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static Verdict toDto(OfferVerdict verdict) {
        List<String> breakers = verdict.getDealBreakers() == null || verdict.getDealBreakers().isBlank() ? List.of() :
            Arrays.stream(verdict.getDealBreakers().split("\n")).toList();
        return new Verdict(verdict.getOfferId(), verdict.isGoodFit(), verdict.getVerdict(), breakers, verdict.getCreatedAt(),
            verdict.getLikelyClient());
    }
}
