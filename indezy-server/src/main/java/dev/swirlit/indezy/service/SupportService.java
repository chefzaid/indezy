package dev.swirlit.indezy.service;

import dev.swirlit.indezy.admin.AdminService;
import dev.swirlit.indezy.dto.SupportDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.mail.SupportAnswerEmailComposer;
import dev.swirlit.indezy.media.MalwareScanner;
import dev.swirlit.indezy.media.NetworkDocuments;
import dev.swirlit.indezy.media.NetworkImages;
import dev.swirlit.indezy.model.SupportAttachment;
import dev.swirlit.indezy.model.SupportRequest;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.SupportAttachmentRepository;
import dev.swirlit.indezy.repository.SupportRequestRepository;
import dev.swirlit.indezy.util.AttachmentTypes;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The members' requests to support (see docs/features.md#support-requests): sent from the app, at most
 * {@value #DAILY_MAX} a day, listed with their answers; the administrators answer them, which emails the member. Up to
 * {@value SupportAttachment#PER_REQUEST} screenshots or PDFs go with a request, added within {@link #ATTACH_WITHIN} of
 * sending it: images drawn anew (which drops what else they carry), PDFs checked for malware.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService {

    static final int DAILY_MAX = 10;
    /** Files are added right after the request is sent, not to an old one. */
    static final Duration ATTACH_WITHIN = Duration.ofHours(1);
    private static final int ADMIN_PAGE = 200;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private final SupportRequestRepository requests;
    private final SupportAttachmentRepository attachments;
    private final MalwareScanner scanner;
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
        return toDto(saved, List.of());
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.Request> mine(Long userId) {
        List<SupportRequest> found = requests.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, List<SupportDtos.Attachment>> files = filesOf(found);
        return found.stream().map(request -> toDto(request, files.getOrDefault(request.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.Received> received(Long adminId) {
        admin.requireAdmin(adminId);
        List<SupportRequest> found = requests.findForAdministrators(PageRequest.of(0, ADMIN_PAGE));
        Map<Long, List<SupportDtos.Attachment>> files = filesOf(found);
        return found.stream()
            .map(request -> new SupportDtos.Received(toDto(request, files.getOrDefault(request.getId(), List.of())), request.getUserId(),
                users.findById(request.getUserId()).map(User::getEmail).orElse(null)))
            .toList();
    }

    /**
     * Adds a screenshot or a PDF to the member's request just sent: an image drawn anew, a PDF checked for malware;
     * anything else, one over {@value SupportAttachment#MAX_BYTES} bytes or beyond the request's limit is refused.
     */
    @Transactional
    public SupportDtos.Attachment attach(Long userId, Long requestId, String originalName, byte[] bytes) {
        SupportRequest request = requests.findById(requestId).filter(found -> found.getUserId().equals(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Support request not found"));
        if (request.getStatus() != SupportRequest.Status.OPEN ||
            request.getCreatedAt().isBefore(LocalDateTime.now(clock).minus(ATTACH_WITHIN))) {
            throw new InvalidStateException("Files go with a request when it is sent: send a new request to add more");
        }
        if (attachments.countByRequestId(requestId) >= SupportAttachment.PER_REQUEST) {
            throw new InvalidStateException("Up to " + SupportAttachment.PER_REQUEST + " files go with a request");
        }
        if (bytes.length == 0 || bytes.length > SupportAttachment.MAX_BYTES) {
            throw new IllegalArgumentException("A file holds 5 MB at most");
        }
        String name = fileName(originalName);
        SupportAttachment attachment = new SupportAttachment();
        attachment.setRequestId(requestId);
        if (AttachmentTypes.detect(name, bytes).filter(type -> IMAGE_TYPES.contains(type.contentType())).isPresent()) {
            NetworkImages.Image made = NetworkImages.process(bytes);
            attachment.setContentType(made.contentType());
            attachment.setFileName(renamed(name, "image/png".equals(made.contentType()) ? "png" : "jpg"));
            attachment.setContent(made.content());
        } else {
            NetworkDocuments.Type type;
            try {
                type = NetworkDocuments.detect(name, bytes);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Send a screenshot (PNG, JPEG) or a PDF", e);
            }
            if (!"pdf".equals(type.extension())) {
                throw new IllegalArgumentException("Send a screenshot (PNG, JPEG) or a PDF");
            }
            scanner.scan(bytes).ifPresent(signature -> {
                log.warn("Account {} added a document holding {} to support request {}: refused", userId, signature, requestId);
                throw new IllegalArgumentException(MalwareScanner.refusal(signature));
            });
            attachment.setContentType(type.contentType());
            attachment.setFileName(renamed(name, "pdf"));
            attachment.setContent(bytes);
        }
        attachment.setSizeBytes(attachment.getContent().length);
        SupportAttachment saved = attachments.save(attachment);
        log.info("Account {} added file {} to support request {}", userId, saved.getId(), requestId);
        return new SupportDtos.Attachment(saved.getId(), requestId, saved.getFileName(), saved.getContentType(), saved.getSizeBytes());
    }

    /** A request's file, for its author or an administrator; anyone else finds none. */
    @Transactional(readOnly = true)
    public SupportAttachment file(Long userId, Long requestId, Long attachmentId) {
        SupportRequest request = requests.findById(requestId).orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (!request.getUserId().equals(userId) && !admin.isAdmin(userId)) {
            throw new ResourceNotFoundException("File not found");
        }
        return attachments.findByIdAndRequestId(attachmentId, requestId).orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    private Map<Long, List<SupportDtos.Attachment>> filesOf(Collection<SupportRequest> found) {
        if (found.isEmpty()) {
            return Map.of();
        }
        return attachments.listFor(found.stream().map(SupportRequest::getId).toList()).stream()
            .collect(Collectors.groupingBy(SupportDtos.Attachment::requestId));
    }

    private static String fileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        return name.isEmpty() ? "file" : name;
    }

    private static String renamed(String name, String extension) {
        String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        return (base.isEmpty() ? "file" : base) + "." + extension;
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
        return toDto(request, filesOf(List.of(request)).getOrDefault(request.getId(), List.of()));
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

    static SupportDtos.Request toDto(SupportRequest request, List<SupportDtos.Attachment> files) {
        return new SupportDtos.Request(request.getId(), request.getKind(), request.getSubject(), request.getDescription(), request.getContext(),
            request.getStatus(), request.getAnswer(), request.getCreatedAt(), request.getAnsweredAt(), files);
    }
}
