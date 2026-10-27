package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.BookingDtos;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.model.Booking;
import dev.swirlit.indezy.model.BookingPage;
import dev.swirlit.indezy.model.BookingWindow;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.model.enums.WebhookEvent;
import dev.swirlit.indezy.repository.BookingPageRepository;
import dev.swirlit.indezy.repository.BookingRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The workspace's booking link (see docs/features.md#booking-link): its settings, the free starts it shows a recruiter,
 * the calls booked through it, and a limit on bookings so the link cannot fill the calendar: five an hour from one
 * address, twenty a day for the workspace.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    static final int PER_ADDRESS_PER_HOUR = 5;
    static final int PER_DAY = 20;
    /** How long an interview step keeps the calendar busy: it has no end of its own. */
    static final Duration STEP_LENGTH = Duration.ofHours(1);
    private static final String SLUG_LETTERS = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final int SLUG_LENGTH = 16;
    private static final Set<StepStatus> NOT_BUSY = Set.of(StepStatus.CANCELED, StepStatus.FAILED);

    private final BookingPageRepository pages;
    private final BookingRepository bookings;
    private final InterviewStepRepository steps;
    private final FreelanceRepository freelances;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private WebhookPublisher webhooks = WebhookPublisher.NONE;
    private final RecentAttempts byAddress = new RecentAttempts(PER_ADDRESS_PER_HOUR, Duration.ofHours(1), 1_000);

    /** Tells the workspace's webhooks of the calls booked; none until Spring sets it (unit tests build the service by hand). */
    @Autowired
    void setWebhooks(WebhookPublisher webhooks) {
        this.webhooks = webhooks;
    }

    @Transactional(readOnly = true)
    public BookingDtos.Page page(Long freelanceId) {
        return pages.findByFreelanceId(freelanceId).map(BookingService::toDto)
            .orElseGet(() -> new BookingDtos.Page(null, new BookingDtos.Settings(false, "", 30, 15, 24, 21, List.of())));
    }

    @Transactional
    public BookingDtos.Page save(Long freelanceId, BookingDtos.Settings settings) {
        Set<DayOfWeek> days = new HashSet<>();
        for (BookingDtos.Window window : settings.windows()) {
            if (!window.start().isBefore(window.end())) {
                throw new FieldRejectedException("windows", "A day's hours end after they start");
            }
            if (!days.add(window.dayOfWeek())) {
                throw new FieldRejectedException("windows", "A day has one range of hours");
            }
        }
        BookingPage page = pages.findByFreelanceId(freelanceId).orElseGet(() -> {
            BookingPage created = new BookingPage();
            created.setFreelanceId(freelanceId);
            created.setSlug(newSlug());
            return created;
        });
        page.setEnabled(settings.enabled());
        page.setTitle(settings.title().strip());
        page.setDurationMinutes(settings.durationMinutes());
        page.setBufferMinutes(settings.bufferMinutes());
        page.setMinNoticeHours(settings.minNoticeHours());
        page.setMaxDaysAhead(settings.maxDaysAhead());
        page.setWindows(settings.windows().stream().map(window -> new BookingWindow(window.dayOfWeek(), window.start(), window.end()))
            .sorted(Comparator.comparing(BookingWindow::getDayOfWeek)).toList());
        return toDto(pages.save(page));
    }

    /** A new address for the link; the old one stops working. */
    @Transactional
    public BookingDtos.Page renewLink(Long freelanceId) {
        BookingPage page = pages.findByFreelanceId(freelanceId).orElseThrow(() -> new ResourceNotFoundException("Booking link not found"));
        page.setSlug(newSlug());
        return toDto(page);
    }

    /** The calls of the last 30 days and to come, the soonest first. */
    @Transactional(readOnly = true)
    public List<BookingDtos.Booking> list(Long freelanceId) {
        return bookings.findByFreelanceIdAndEndsAtAfterOrderByStartsAtAsc(freelanceId, clock.instant().minus(Duration.ofDays(30))).stream()
            .map(BookingService::toDto).toList();
    }

    @Transactional
    public BookingDtos.Booking cancel(Long freelanceId, Long id) {
        Booking booking = bookings.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getCancelledAt() == null) {
            booking.setCancelledAt(clock.instant());
        }
        return toDto(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.Booking> export(Long freelanceId) {
        return bookings.findByFreelanceIdOrderByStartsAtAsc(freelanceId).stream().map(BookingService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public BookingDtos.PublicPage publicPage(String slug) {
        BookingPage page = open(pages.findBySlug(slug));
        return new BookingDtos.PublicPage(freelancer(page.getFreelanceId()), page.getTitle(), page.getDurationMinutes(),
            BookingSlots.PARIS.getId(), free(page));
    }

    /** Books a free start, checked again under a lock on the page so two recruiters never get the same call. */
    @Transactional
    public BookingDtos.Confirmation book(String slug, BookingDtos.Request request, String address) {
        BookingPage page = open(pages.lockBySlug(slug));
        limit(page.getFreelanceId(), address);
        boolean free = free(page).stream().flatMap(day -> day.starts().stream()).anyMatch(request.start()::equals);
        if (!free) {
            throw new InvalidStateException("This time is no longer free: choose another one");
        }
        Booking booking = new Booking();
        booking.setFreelanceId(page.getFreelanceId());
        booking.setStartsAt(request.start());
        booking.setEndsAt(request.start().plus(Duration.ofMinutes(page.getDurationMinutes())));
        booking.setName(request.name().strip());
        booking.setEmail(request.email().strip());
        booking.setCompany(blankToNull(request.company()));
        booking.setMessage(blankToNull(request.message()));
        Booking saved = bookings.save(booking);
        log.info("Booking {} made through the link of workspace {}", saved.getId(), page.getFreelanceId());
        webhooks.publish(page.getFreelanceId(), WebhookEvent.CALL_BOOKED, WebhookPayloads.booking(saved));
        return new BookingDtos.Confirmation(saved.getStartsAt(), saved.getEndsAt(), freelancer(page.getFreelanceId()));
    }

    private List<BookingDtos.Day> free(BookingPage page) {
        Instant now = clock.instant();
        LocalDateTime from = LocalDateTime.ofInstant(now.minus(STEP_LENGTH), BookingSlots.PARIS);
        LocalDateTime to = LocalDateTime.ofInstant(now.plus(Duration.ofDays(page.getMaxDaysAhead() + 2L)), BookingSlots.PARIS);
        List<BookingSlots.Busy> busy = Stream.concat(
            bookings.findByFreelanceIdAndEndsAtAfterOrderByStartsAtAsc(page.getFreelanceId(), now).stream()
                .filter(booking -> booking.getCancelledAt() == null)
                .map(booking -> new BookingSlots.Busy(booking.getStartsAt(), booking.getEndsAt())),
            steps.findByFreelanceIdAndDateBetween(page.getFreelanceId(), from, to).stream()
                .filter(step -> step.getDate() != null && !NOT_BUSY.contains(step.getStatus()))
                .map(step -> {
                    Instant start = step.getDate().atZone(BookingSlots.PARIS).toInstant();
                    return new BookingSlots.Busy(start, start.plus(STEP_LENGTH));
                }))
            .toList();
        return BookingSlots.free(page, now, busy);
    }

    /** Five bookings an hour from one address, twenty a day for the workspace. */
    private void limit(Long freelanceId, String address) {
        Instant now = clock.instant();
        // Creation times are written in the server's zone.
        LocalDateTime dayAgo = LocalDateTime.ofInstant(now.minus(Duration.ofDays(1)), ZoneId.systemDefault());
        if (bookings.countByFreelanceIdAndCreatedAtAfter(freelanceId, dayAgo) >= PER_DAY) {
            throw new TooManyRequestsException("This link takes no more bookings today", Duration.ofHours(1));
        }
        if (!byAddress.tryAcquire(address, now)) {
            throw new TooManyRequestsException("Too many bookings from here: try again later", Duration.ofHours(1));
        }
    }

    private static BookingPage open(Optional<BookingPage> page) {
        return page.filter(BookingPage::isEnabled).orElseThrow(() -> new ResourceNotFoundException("Booking link not found"));
    }

    private String freelancer(Long freelanceId) {
        return freelances.findById(freelanceId).map(BookingService::name).orElse("");
    }

    private static String name(Freelance freelance) {
        return Stream.of(freelance.getFirstName(), freelance.getLastName()).filter(part -> part != null && !part.isBlank())
            .collect(Collectors.joining(" "));
    }

    private String newSlug() {
        StringBuilder slug = new StringBuilder(SLUG_LENGTH);
        for (int i = 0; i < SLUG_LENGTH; i++) {
            slug.append(SLUG_LETTERS.charAt(random.nextInt(SLUG_LETTERS.length())));
        }
        return slug.toString();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static BookingDtos.Page toDto(BookingPage page) {
        return new BookingDtos.Page(page.getSlug(), new BookingDtos.Settings(page.isEnabled(), page.getTitle(), page.getDurationMinutes(),
            page.getBufferMinutes(), page.getMinNoticeHours(), page.getMaxDaysAhead(), page.getWindows().stream()
            .map(window -> new BookingDtos.Window(window.getDayOfWeek(), window.getStart(), window.getEnd())).toList()));
    }

    private static BookingDtos.Booking toDto(Booking booking) {
        return new BookingDtos.Booking(booking.getId(), booking.getStartsAt(), booking.getEndsAt(), booking.getName(), booking.getEmail(),
            booking.getCompany(), booking.getMessage(), booking.getCancelledAt());
    }
}
