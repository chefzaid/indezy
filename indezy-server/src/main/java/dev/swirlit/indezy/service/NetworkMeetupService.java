package dev.swirlit.indezy.service;

import dev.swirlit.indezy.admin.AdminService;
import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.dto.NetworkMeetupDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.model.NetworkMeetup;
import dev.swirlit.indezy.model.NetworkMeetupAttendee;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.NetworkMeetupAttendance;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.repository.NetworkMeetupAttendeeRepository;
import dev.swirlit.indezy.repository.NetworkMeetupRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The member network's events (see docs/features.md#member-network): meetups in a place and webinars online that a
 * member organizes, the others answering Going or Interested; those who answered are reminded the day before and told
 * when it is cancelled; each is added to a calendar as an .ics file, and listed on the pages of the tags it names.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkMeetupService {

    static final String NOT_FOUND = "No such event";
    static final int PAGE_SIZE = 20;
    /** How many events a member organizes a day at most. */
    static final int DAILY_MAX = 5;
    /** The zone the events' times are written in. */
    static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Pattern TAG = Pattern.compile("[\\p{L}\\p{N}_]{1,30}");
    private static final DateTimeFormatter ICS_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);

    /** Which events are listed: the coming ones, those the reader answered, or those they organize (past ones too). */
    public enum Scope { UPCOMING, ATTENDING, ORGANIZED }

    private final NetworkService network;
    private final NetworkMeetupRepository meetups;
    private final NetworkMeetupAttendeeRepository attendees;
    private final NetworkMemberRepository members;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkNotificationService notices;
    private final AdminService admin;
    private final Clock clock;

    /** A page of events: of a scope, those naming a tag, or whose words hold what was typed; the coming ones the soonest first. */
    @Transactional(readOnly = true)
    public NetworkMeetupDtos.Meetups list(Long userId, Scope scope, String tag, String typed, int page) {
        network.requireMember(userId);
        LocalDateTime now = now();
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(userId));
        Map<Long, NetworkMeetupAttendee> mine = attendees.findByUserId(userId).stream()
            .collect(Collectors.toMap(NetworkMeetupAttendee::getMeetupId, attendee -> attendee, (first, second) -> first));
        List<NetworkMeetup> candidates = scope == Scope.ORGANIZED ? meetups.findTop200ByOrganizerIdOrderByStartsAtDescIdDesc(userId) :
            meetups.findTop1000ByStartsAtAfterOrderByStartsAtAscIdAsc(now.minusHours(12));
        String wanted = tag == null || tag.isBlank() ? null : tag.strip().toLowerCase(Locale.ROOT);
        String query = Optional.ofNullable(NetworkPost.fold(typed)).orElse("").strip();
        List<NetworkMeetup> found = candidates.stream()
            .filter(meetup -> scope == Scope.ORGANIZED || !meetup.isCancelled() && meetup.endsOrDefault().isAfter(now))
            .filter(meetup -> scope != Scope.ATTENDING || mine.containsKey(meetup.getId()))
            .filter(meetup -> !blocked.contains(meetup.getOrganizerId()))
            .filter(meetup -> wanted == null || meetup.tagList().contains(wanted))
            .filter(meetup -> query.isEmpty() || NetworkPost.fold(meetup.getTitle() + " " + Optional.ofNullable(meetup.getDescription()).orElse("") +
                " " + Optional.ofNullable(meetup.getPlace()).orElse("") + " " + String.join(" ", meetup.tagList())).contains(query))
            .toList();
        Map<Long, Optional<NetworkDtos.MemberCard>> organizers = new HashMap<>();
        List<NetworkMeetup> live = found.stream().filter(meetup -> organizers.computeIfAbsent(meetup.getOrganizerId(), this::card).isPresent()).toList();
        int from = Math.min(Math.max(page, 0) * PAGE_SIZE, live.size());
        int to = Math.min(from + PAGE_SIZE, live.size());
        Map<Long, long[]> counts = counts();
        boolean administrator = admin.isAdmin(userId);
        List<NetworkMeetupDtos.Meetup> shown = live.subList(from, to).stream().map(meetup -> view(meetup, organizers.get(meetup.getOrganizerId()).orElse(null),
            counts.getOrDefault(meetup.getId(), new long[2]), Optional.ofNullable(mine.get(meetup.getId())).map(NetworkMeetupAttendee::getStatus).orElse(null),
            userId, administrator)).toList();
        return new NetworkMeetupDtos.Meetups(shown, Math.max(page, 0), to < live.size());
    }

    /** One event, the organizer's or another member's. */
    @Transactional(readOnly = true)
    public NetworkMeetupDtos.Meetup get(Long userId, Long id) {
        network.requireMember(userId);
        return view(userId, readable(userId, id));
    }

    /** Organizes an event, its organizer going; {@value #DAILY_MAX} a day at most. */
    @Transactional
    public NetworkMeetupDtos.Meetup create(Long userId, NetworkMeetupDtos.NewMeetup request) {
        network.requireMember(userId);
        if (meetups.countByOrganizerIdAndCreatedAtAfter(userId, LocalDateTime.now(clock).minusDays(1)) >= DAILY_MAX) {
            throw new TooManyRequestsException("A member organizes " + DAILY_MAX + " events a day at most", Duration.ofDays(1));
        }
        NetworkMeetup meetup = new NetworkMeetup();
        meetup.setOrganizerId(userId);
        fill(meetup, request);
        meetups.save(meetup);
        answer(meetup.getId(), userId, NetworkMeetupAttendance.GOING);
        log.info("Member {} organized event {}", userId, meetup.getId());
        return view(userId, meetup);
    }

    /** Changes an event's details: its organizer or the administrators, until it ends. */
    @Transactional
    public NetworkMeetupDtos.Meetup update(Long userId, Long id, NetworkMeetupDtos.NewMeetup request) {
        network.requireMember(userId);
        NetworkMeetup meetup = editable(userId, id);
        LocalDateTime startsAt = meetup.getStartsAt();
        fill(meetup, request);
        if (!startsAt.equals(meetup.getStartsAt())) {
            // A new time: the attendees are reminded again the day before.
            attendees.findByMeetupIdOrderByCreatedAtAsc(id).forEach(attendee -> attendee.setReminded(null));
        }
        meetups.save(meetup);
        return view(userId, meetup);
    }

    /** Cancels an event: its organizer or the administrators; those who answered are told. */
    @Transactional
    public NetworkMeetupDtos.Meetup cancel(Long userId, Long id) {
        network.requireMember(userId);
        NetworkMeetup meetup = editable(userId, id);
        meetup.setCancelled(true);
        meetups.save(meetup);
        attendees.findByMeetupIdOrderByCreatedAtAsc(id).stream().map(NetworkMeetupAttendee::getUserId).filter(attendee -> !attendee.equals(userId))
            .forEach(attendee -> notices.meetup(attendee, NetworkNotificationType.MEETUP_CANCELLED, id));
        log.info("Member {} cancelled event {}", userId, id);
        return view(userId, meetup);
    }

    /** Answers an event Going or Interested, or takes the answer back (none); not a cancelled or past one. */
    @Transactional
    public NetworkMeetupDtos.Meetup attend(Long userId, Long id, NetworkMeetupAttendance status) {
        network.requireMember(userId);
        NetworkMeetup meetup = readable(userId, id);
        if (status == null) {
            attendees.findByMeetupIdAndUserId(id, userId).ifPresent(attendees::delete);
            return view(userId, meetup);
        }
        if (meetup.isCancelled() || !meetup.endsOrDefault().isAfter(now())) {
            throw new InvalidStateException("A cancelled or past event takes no answer");
        }
        answer(id, userId, status);
        return view(userId, meetup);
    }

    /** Who answered an event: those going, then those interested, each in reach of the reader. */
    @Transactional(readOnly = true)
    public NetworkMeetupDtos.Attendees attendees(Long userId, Long id) {
        network.requireMember(userId);
        readable(userId, id);
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(userId));
        List<NetworkMeetupAttendee> all = attendees.findByMeetupIdOrderByCreatedAtAsc(id).stream()
            .filter(attendee -> !blocked.contains(attendee.getUserId())).toList();
        return new NetworkMeetupDtos.Attendees(cards(all, NetworkMeetupAttendance.GOING), cards(all, NetworkMeetupAttendance.INTERESTED));
    }

    /** The event as an iCalendar file, for any calendar; its times in UTC. */
    @Transactional(readOnly = true)
    public String ics(Long userId, Long id, String appUrl) {
        network.requireMember(userId);
        NetworkMeetup meetup = readable(userId, id);
        String page = appUrl + "/network/events/" + id;
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Indezy//Network events//FR");
        line(ics, "METHOD:PUBLISH");
        line(ics, "BEGIN:VEVENT");
        line(ics, "UID:network-event-" + id + "@indezy");
        line(ics, "DTSTAMP:" + utc(now()));
        line(ics, "DTSTART:" + utc(meetup.getStartsAt()));
        line(ics, "DTEND:" + utc(meetup.endsOrDefault()));
        line(ics, "SUMMARY:" + escape(meetup.getTitle()));
        String description = Optional.ofNullable(meetup.getDescription()).map(text -> text + "\n\n").orElse("") + page;
        line(ics, "DESCRIPTION:" + escape(description));
        String where = meetup.isOnline() ? meetup.getLink() : meetup.getPlace();
        if (where != null) {
            line(ics, "LOCATION:" + escape(where));
        }
        line(ics, "URL:" + (meetup.isOnline() && meetup.getLink() != null ? meetup.getLink() : page));
        line(ics, "STATUS:" + (meetup.isCancelled() ? "CANCELLED" : "CONFIRMED"));
        line(ics, "END:VEVENT");
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    /** Reminds those who answered the events starting within a day, once each; answers how many were reminded. */
    @Transactional
    public int remindDue() {
        LocalDateTime now = now();
        int reminded = 0;
        for (NetworkMeetup meetup : meetups.findByStartsAtBetween(now, now.plusDays(1))) {
            if (meetup.isCancelled()) {
                continue;
            }
            for (NetworkMeetupAttendee attendee : attendees.findByMeetupIdOrderByCreatedAtAsc(meetup.getId())) {
                if (!Boolean.TRUE.equals(attendee.getReminded())) {
                    attendee.setReminded(true);
                    notices.meetup(attendee.getUserId(), NetworkNotificationType.MEETUP_REMINDER, meetup.getId());
                    reminded++;
                }
            }
        }
        return reminded;
    }

    private void fill(NetworkMeetup meetup, NetworkMeetupDtos.NewMeetup request) {
        if (!request.startsAt().isAfter(now())) {
            throw new IllegalArgumentException("An event starts in the future");
        }
        if (request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw new IllegalArgumentException("An event ends after it starts");
        }
        String place = blankToNull(request.place());
        String link = blankToNull(request.link());
        if (link != null && !web(link)) {
            throw new IllegalArgumentException("An event's link is a web address");
        }
        if (request.online() ? link == null : place == null) {
            throw new IllegalArgumentException(request.online() ? "An online event needs its link" : "An event in a place needs its address");
        }
        Set<String> tags = new LinkedHashSet<>();
        for (String tag : request.tags()) {
            String clean = tag == null ? "" : tag.strip().replaceFirst("^#", "").toLowerCase(Locale.ROOT);
            if (TAG.matcher(clean).matches()) {
                tags.add(clean);
            }
        }
        meetup.setTitle(request.title().strip());
        meetup.setDescription(blankToNull(request.description()));
        meetup.setStartsAt(request.startsAt().withSecond(0).withNano(0));
        meetup.setEndsAt(request.endsAt() == null ? null : request.endsAt().withSecond(0).withNano(0));
        meetup.setOnline(request.online());
        meetup.setPlace(request.online() ? null : place);
        meetup.setLink(link);
        meetup.setTags(tags.isEmpty() ? null : String.join(",", tags));
    }

    private static boolean web(String link) {
        try {
            URI uri = URI.create(link);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void answer(Long meetupId, Long userId, NetworkMeetupAttendance status) {
        NetworkMeetupAttendee attendee = attendees.findByMeetupIdAndUserId(meetupId, userId).orElseGet(() -> {
            NetworkMeetupAttendee created = new NetworkMeetupAttendee();
            created.setMeetupId(meetupId);
            created.setUserId(userId);
            return created;
        });
        attendee.setStatus(status);
        attendees.save(attendee);
    }

    /** The event, if the reader may read it: not one whose organizer left or with whom either blocks the other. */
    private NetworkMeetup readable(Long userId, Long id) {
        NetworkMeetup meetup = meetups.findById(id).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (restrictions.blockedEitherWay(userId).contains(meetup.getOrganizerId()) || card(meetup.getOrganizerId()).isEmpty()) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return meetup;
    }

    private NetworkMeetup editable(Long userId, Long id) {
        NetworkMeetup meetup = readable(userId, id);
        if (!meetup.getOrganizerId().equals(userId) && !admin.isAdmin(userId)) {
            throw new AccessDeniedException("Only its organizer or the administrators change an event");
        }
        if (meetup.isCancelled() || !meetup.endsOrDefault().isAfter(now())) {
            throw new InvalidStateException("A cancelled or past event is not changed");
        }
        return meetup;
    }

    private Optional<NetworkDtos.MemberCard> card(Long userId) {
        return members.findByUserId(userId).filter(NetworkMember::isActive).flatMap(network::liveCard);
    }

    private List<NetworkDtos.MemberCard> cards(List<NetworkMeetupAttendee> all, NetworkMeetupAttendance status) {
        return all.stream().filter(attendee -> attendee.getStatus() == status).map(attendee -> card(attendee.getUserId())).flatMap(Optional::stream)
            .toList();
    }

    /** Each event's answers: those going, then those interested. */
    private Map<Long, long[]> counts() {
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : attendees.counts()) {
            long[] pair = counts.computeIfAbsent((Long) row[0], key -> new long[2]);
            pair[row[1] == NetworkMeetupAttendance.GOING ? 0 : 1] = ((Number) row[2]).longValue();
        }
        return counts;
    }

    private NetworkMeetupDtos.Meetup view(Long userId, NetworkMeetup meetup) {
        List<NetworkMeetupAttendee> all = attendees.findByMeetupIdOrderByCreatedAtAsc(meetup.getId());
        long going = all.stream().filter(attendee -> attendee.getStatus() == NetworkMeetupAttendance.GOING).count();
        NetworkMeetupAttendance mine = all.stream().filter(attendee -> attendee.getUserId().equals(userId)).map(NetworkMeetupAttendee::getStatus)
            .findFirst().orElse(null);
        return view(meetup, card(meetup.getOrganizerId()).orElse(null), new long[] {going, all.size() - going}, mine, userId, admin.isAdmin(userId));
    }

    private NetworkMeetupDtos.Meetup view(NetworkMeetup meetup, NetworkDtos.MemberCard organizer, long[] counts, NetworkMeetupAttendance mine,
                                          Long userId, boolean administrator) {
        boolean own = meetup.getOrganizerId().equals(userId);
        boolean open = !meetup.isCancelled() && meetup.endsOrDefault().isAfter(now());
        return new NetworkMeetupDtos.Meetup(meetup.getId(), meetup.getTitle(), meetup.getDescription(), meetup.getStartsAt(), meetup.getEndsAt(),
            meetup.isOnline(), meetup.getPlace(), meetup.getLink(), meetup.tagList(), organizer, counts[0], counts[1], mine, own,
            open && (own || administrator), meetup.isCancelled());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(PARIS));
    }

    private static String utc(LocalDateTime paris) {
        return paris.atZone(PARIS).withZoneSameInstant(ZoneOffset.UTC).format(ICS_TIME);
    }

    /** A content line, folded at 75 octets as RFC 5545 asks. */
    private static void line(StringBuilder ics, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        int start = 0;
        boolean first = true;
        while (start < bytes.length) {
            int room = first ? 75 : 74;
            int end = Math.min(start + room, bytes.length);
            // Never cut a UTF-8 character in two.
            while (end < bytes.length && (bytes[end] & 0xC0) == 0x80) {
                end--;
            }
            ics.append(first ? "" : " ").append(new String(bytes, start, end - start, StandardCharsets.UTF_8)).append("\r\n");
            start = end;
            first = false;
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n");
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
