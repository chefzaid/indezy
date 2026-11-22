package dev.swirlit.indezy.service;

import dev.swirlit.indezy.admin.AdminService;
import dev.swirlit.indezy.dto.SupportDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.mail.SupportAnswerEmailComposer;
import dev.swirlit.indezy.model.SupportRequest;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.SupportRequestRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * The members' requests to support (see docs/features.md#support-requests): sent from the app, at most
 * {@value #DAILY_MAX} a day, listed with their answers; the administrators answer them, which emails the member.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService {

    static final int DAILY_MAX = 10;
    private static final int ADMIN_PAGE = 200;

    private final SupportRequestRepository requests;
    private final UserRepository users;
    private final AdminService admin;
    private final ResendMailer mailer;
    private final SupportAnswerEmailComposer composer;
    private final Clock clock;

    @Transactional
    public SupportDtos.Request create(Long userId, SupportDtos.NewRequest request) {
        if (requests.countByUserIdAndCreatedAtAfter(userId, LocalDateTime.now(clock).minusDays(1)) >= DAILY_MAX) {
            throw new InvalidStateException("Up to " + DAILY_MAX + " requests a day: wait for an answer or add to an open one");
        }
        SupportRequest created = new SupportRequest();
        created.setUserId(userId);
        created.setKind(request.kind());
        created.setSubject(request.subject().strip());
        created.setDescription(request.description().strip());
        created.setContext(request.context() == null || request.context().isBlank() ? null : request.context().strip());
        SupportRequest saved = requests.save(created);
        log.info("Account {} sent support request {} ({})", userId, saved.getId(), saved.getKind());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.Request> mine(Long userId) {
        return requests.findByUserIdOrderByCreatedAtDesc(userId).stream().map(SupportService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.Received> received(Long adminId) {
        admin.requireAdmin(adminId);
        return requests.findForAdministrators(PageRequest.of(0, ADMIN_PAGE)).stream()
            .map(request -> new SupportDtos.Received(toDto(request), request.getUserId(),
                users.findById(request.getUserId()).map(User::getEmail).orElse(null)))
            .toList();
    }

    /** Answers a request, closing it on request, and tells its author by email when email can go. */
    @Transactional
    public SupportDtos.Request answer(Long adminId, Long requestId, SupportDtos.Answer answer) {
        admin.requireAdmin(adminId);
        SupportRequest request = requests.findById(requestId).orElseThrow(() -> new ResourceNotFoundException("Support request not found"));
        request.setAnswer(answer.answer().strip());
        request.setAnsweredAt(clock.instant());
        request.setStatus(answer.close() ? SupportRequest.Status.CLOSED : SupportRequest.Status.ANSWERED);
        users.findById(request.getUserId()).ifPresent(user -> tell(user, request));
        return toDto(request);
    }

    private void tell(User user, SupportRequest request) {
        if (!mailer.configured() || user.getEmail() == null) {
            return;
        }
        DocumentLanguage language = user.getLanguagePreference() != null && user.getLanguagePreference().toLowerCase(Locale.ROOT).startsWith("en") ?
            DocumentLanguage.EN : DocumentLanguage.FR;
        try {
            mailer.send(composer.compose(user.getEmail(), request.getSubject(), language, "support-answer/" + request.getId() + "/" +
                request.getAnsweredAt().toEpochMilli()));
        } catch (MailException e) {
            log.warn("The answer email of support request {} was not sent: {}", request.getId(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static SupportDtos.Request toDto(SupportRequest request) {
        return new SupportDtos.Request(request.getId(), request.getKind(), request.getSubject(), request.getDescription(), request.getContext(),
            request.getStatus(), request.getAnswer(), request.getCreatedAt(), request.getAnsweredAt());
    }
}
