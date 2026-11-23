package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.AutopilotDraft;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferVerdict;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.AutopilotDraftRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferVerdictRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.ContactInteractionService;
import dev.swirlit.indezy.service.OfferSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The daily autopilot run (docs/features.md#daily-autopilot): every night, for the accounts that turned it on, the new
 * offers of the last day that fit the profile get their verdict and deal-breakers (as **Should I apply? (AI)** gives
 * them), {@value #OFFERS} at most, from the account's AI allowance and stopping when it is used up; the dashboard lists
 * them in the morning.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutopilotService {

    /** Offers triaged a night at most, per account. */
    static final int OFFERS = 10;
    /** Follow-ups drafted a night at most, per account. */
    static final int DRAFTS = 5;
    /** How long a draft is kept, and how long before a contact is drafted for again. */
    static final Duration DRAFT_KEPT = Duration.ofDays(7);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Duration DAY = Duration.ofDays(1);

    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final OfferSearchService search;
    private final OfferTriageAiService triage;
    private final OfferVerdictRepository verdicts;
    private final JobOfferRepository offers;
    private final AiService ai;
    private final ContactInteractionService interactions;
    private final MessageAiService messages;
    private final AutopilotDraftRepository drafts;
    private final ContactRepository contacts;
    private final Clock clock;

    /** An offer triaged, as the morning's list shows it. */
    public record Prepared(Long offerId, String title, String company, boolean goodFit, String verdict, List<String> dealBreakers,
                           LocalDateTime at) {

        public Prepared {
            dealBreakers = List.copyOf(dealBreakers);
        }
    }

    /** A follow-up drafted overnight: for whom, about which opportunity, its subject and text. */
    public record Drafted(Long id, Long contactId, String contact, String company, Long projectId, String subject, String body,
                          LocalDateTime at) {
    }

    /** Whether the run is on, the offers triaged in the last day, worth applying to first, and the follow-ups drafted this week. */
    public record Queue(boolean on, List<Prepared> offers, List<Drafted> drafts) {

        public Queue {
            offers = List.copyOf(offers);
            drafts = List.copyOf(drafts);
        }
    }

    @Scheduled(cron = "0 10 4 * * *", zone = "Europe/Paris")
    public void runNightly() {
        for (User user : users.findByAutopilotTrue()) {
            try {
                int triaged = run(user);
                int drafted = draft(user);
                if (triaged + drafted > 0) {
                    log.info("Autopilot triaged {} offer(s) and drafted {} follow-up(s) for account {}", triaged, drafted, user.getId());
                }
            } catch (RuntimeException e) {
                log.warn("Autopilot run failed for account {}: {}", user.getId(), e.getMessage());
            }
        }
    }

    /** Triages the account's new offers that fit and have no verdict yet; how many were. */
    int run(User user) {
        if (user.getDeletedAt() != null || !Boolean.TRUE.equals(user.getAutopilot())) {
            return 0;
        }
        Optional<Freelance> freelance = freelances.findByEmail(user.getEmail());
        if (freelance.isEmpty() || !ai.status(user.getId()).available()) {
            return 0;
        }
        Long freelanceId = freelance.get().getId();
        OfferSearchRequest request = new OfferSearchRequest();
        request.setMatchesProfile(true);
        request.setUnseenOnly(true);
        request.setSort(OfferSearchRequest.Sort.MATCH);
        Instant now = clock.instant();
        List<OfferDto> found = search.foundBetween(freelanceId, request, now.minus(DAY), now, OFFERS * 3).content();
        DocumentLanguage language = "fr".equalsIgnoreCase(user.getLanguagePreference()) ? DocumentLanguage.FR : DocumentLanguage.EN;
        int triaged = 0;
        for (OfferDto offer : found) {
            if (triaged >= OFFERS) {
                break;
            }
            if (verdicts.findByFreelanceIdAndOfferId(freelanceId, offer.id()).isPresent()) {
                continue;
            }
            try {
                triage.triage(user.getId(), freelanceId, offer.id(), language);
                triaged++;
            } catch (InvalidStateException e) {
                // The allowance is used up: the rest waits for the member, or for next month.
                break;
            }
        }
        return triaged;
    }

    /** Every night, the drafts older than a week go. */
    @Scheduled(cron = "0 40 5 * * *", zone = "Europe/Paris")
    public void dropOld() {
        drafts.deleteOlderThan(LocalDateTime.now(clock.withZone(PARIS)).minus(DRAFT_KEPT));
    }

    /**
     * Drafts a follow-up for the account's contacts waiting for an answer about an opportunity, the longest waiting
     * first, {@value #DRAFTS} at most, none for a contact drafted for this week; how many were.
     */
    int draft(User user) {
        if (user.getDeletedAt() != null || !Boolean.TRUE.equals(user.getAutopilot())) {
            return 0;
        }
        Optional<Freelance> freelance = freelances.findByEmail(user.getEmail());
        if (freelance.isEmpty() || !ai.status(user.getId()).available()) {
            return 0;
        }
        Long freelanceId = freelance.get().getId();
        LocalDateTime now = LocalDateTime.now(clock.withZone(PARIS));
        DocumentLanguage language = "fr".equalsIgnoreCase(user.getLanguagePreference()) ? DocumentLanguage.FR : DocumentLanguage.EN;
        int drafted = 0;
        for (DashboardStatsDto.FollowUpDue due : interactions.followUpsDue(freelanceId, user.getFollowUpDays(), now.toLocalDate())) {
            if (drafted >= DRAFTS) {
                break;
            }
            if (due.getProjectId() == null || drafts.existsByFreelanceIdAndContactIdAndCreatedAtAfter(freelanceId, due.getContactId(), now.minus(DRAFT_KEPT))) {
                continue;
            }
            MessageAiService.Draft written;
            try {
                written = messages.draft(user.getId(), due.getProjectId(), MessageAiService.Kind.FOLLOW_UP, language, null);
            } catch (InvalidStateException e) {
                break;
            }
            AutopilotDraft draft = new AutopilotDraft();
            draft.setFreelanceId(freelanceId);
            draft.setContactId(due.getContactId());
            draft.setProjectId(due.getProjectId());
            draft.setSubject(cut(written.subject(), AutopilotDraft.SUBJECT_MAX));
            draft.setBody(cut(written.body(), AutopilotDraft.BODY_MAX));
            drafts.save(draft);
            drafted++;
        }
        return drafted;
    }

    /** Sets a draft aside: the member sent it their own way, or does not want it. */
    @Transactional
    public void dismiss(Long freelanceId, Long draftId) {
        drafts.delete(drafts.findByIdAndFreelanceId(draftId, freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Draft not found")));
    }

    private static String cut(String text, int max) {
        String stripped = text == null ? "" : text.strip();
        return stripped.length() > max ? stripped.substring(0, max) : stripped;
    }

    /** The offers triaged for the workspace in the last day, those worth applying to first, then the newest. */
    @Transactional(readOnly = true)
    public Queue queue(Long userId, Long freelanceId) {
        boolean on = users.findById(userId).map(User::getAutopilot).orElse(false);
        LocalDateTime since = LocalDateTime.now(clock.withZone(PARIS)).minus(DAY);
        List<OfferVerdict> recent = verdicts.findByFreelanceIdAndCreatedAtAfterOrderByCreatedAtDesc(freelanceId, since);
        Map<Long, JobOffer> byId = offers.findAllById(recent.stream().map(OfferVerdict::getOfferId).toList()).stream()
            .collect(Collectors.toMap(JobOffer::getId, Function.identity()));
        List<Prepared> prepared = recent.stream().filter(verdict -> byId.containsKey(verdict.getOfferId()))
            .map(verdict -> new Prepared(verdict.getOfferId(), byId.get(verdict.getOfferId()).getTitle(),
                byId.get(verdict.getOfferId()).getCompany(), verdict.isGoodFit(), verdict.getVerdict(), breakers(verdict.getDealBreakers()),
                verdict.getCreatedAt()))
            .sorted((one, other) -> Boolean.compare(other.goodFit(), one.goodFit()))
            .toList();
        List<AutopilotDraft> written = drafts.findByFreelanceIdAndCreatedAtAfterOrderByCreatedAtDesc(freelanceId,
            LocalDateTime.now(clock.withZone(PARIS)).minus(DRAFT_KEPT));
        Map<Long, Contact> people = contacts.findAllById(written.stream().map(AutopilotDraft::getContactId).toList()).stream()
            .collect(Collectors.toMap(Contact::getId, Function.identity()));
        List<Drafted> drafted = written.stream().filter(draft -> people.containsKey(draft.getContactId()))
            .map(draft -> {
                Contact contact = people.get(draft.getContactId());
                return new Drafted(draft.getId(), contact.getId(), contact.getFullName(),
                    contact.getClient() == null ? null : contact.getClient().getCompanyName(), draft.getProjectId(), draft.getSubject(),
                    draft.getBody(), draft.getCreatedAt());
            }).toList();
        return new Queue(on, prepared, drafted);
    }

    private static List<String> breakers(String stored) {
        return stored == null || stored.isBlank() ? List.of() : Arrays.stream(stored.split("\n")).filter(line -> !line.isBlank()).toList();
    }
}
