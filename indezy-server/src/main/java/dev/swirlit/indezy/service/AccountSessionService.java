package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.AccountSecurityDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.AccountSession;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.model.enums.SignInMethod;
import dev.swirlit.indezy.repository.AccountEventRepository;
import dev.swirlit.indezy.repository.AccountSessionRepository;
import dev.swirlit.indezy.util.JwtUtil;
import dev.swirlit.indezy.util.RequestDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The account's sessions: each sign-in opens one, named in the token it issues, and the token works only while it is
 * open. The owner sees them and ends any of them; a password change or an administrator disabling the account ends them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountSessionService {

    /** How often a session's last activity is written: once every 5 minutes at most, not on every request. */
    static final Duration SEEN_EVERY = Duration.ofMinutes(5);
    /** Ended sessions are kept 30 days, events a year. */
    static final Duration SESSIONS_KEPT = Duration.ofDays(30);
    static final Duration EVENTS_KEPT = Duration.ofDays(365);
    private static final Long NONE = -1L;

    private final AccountSessionRepository sessions;
    private final AccountEventRepository eventRepository;
    private final AccountEventService events;
    private final JwtUtil jwtUtil;
    private final Clock clock;

    /** Opens a session for the account and answers its token. */
    @Transactional
    public String open(User user, SignInMethod method) {
        LocalDateTime now = LocalDateTime.now(clock);
        AccountSession session = new AccountSession();
        session.setUserId(user.getId());
        session.setSessionKey(UUID.randomUUID().toString());
        session.setMethod(method);
        session.setDevice(RequestDevice.current());
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plus(jwtUtil.lifetime()));
        sessions.save(session);
        events.record(user.getId(), AccountEventType.SIGNED_IN, method.name());
        return jwtUtil.generateToken(user.getEmail(), user.getId(), session.getSessionKey());
    }

    /**
     * Continues the account's open session {@code sessionId} with a new token when there is one, for the Keycloak exchange
     * the app repeats on every load; opens a session otherwise.
     */
    @Transactional
    public String resumeOrOpen(User user, SignInMethod method, Long sessionId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<AccountSession> open = sessionId == null ? Optional.empty() : sessions.findByIdAndUserId(sessionId, user.getId())
            .filter(session -> session.getRevokedAt() == null && session.getExpiresAt().isAfter(now));
        if (open.isEmpty()) {
            return open(user, method);
        }
        AccountSession session = open.get();
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plus(jwtUtil.lifetime()));
        return jwtUtil.generateToken(user.getEmail(), user.getId(), session.getSessionKey());
    }

    /** The id of the open session a token names, its last activity noted; empty when it is ended or its account is not active. */
    @Transactional
    public Optional<Long> authenticate(String sessionKey, Long userId) {
        if (sessionKey == null || userId == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return sessions.findOpen(sessionKey, userId, now).map(session -> {
            if (session.getLastSeenAt() == null || session.getLastSeenAt().isBefore(now.minus(SEEN_EVERY))) {
                sessions.touch(session.getId(), now);
            }
            return session.getId();
        });
    }

    /** The account's open sessions, the current one first, then the most recently used. */
    @Transactional(readOnly = true)
    public List<AccountSecurityDtos.Session> list(Long userId, Long currentId) {
        return sessions.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, LocalDateTime.now(clock)).stream()
            .map(session -> new AccountSecurityDtos.Session(session.getId(), session.getMethod(), session.getDevice(),
                session.getCreatedAt(), session.getLastSeenAt(), session.getExpiresAt(), session.getId().equals(currentId)))
            .sorted(Comparator.comparing(AccountSecurityDtos.Session::current).reversed()
                .thenComparing(AccountSecurityDtos.Session::lastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    /** Ends one of the account's sessions: signing out when it is the current one. */
    @Transactional
    public void end(Long userId, Long sessionId, Long currentId) {
        AccountSession session = sessions.findByIdAndUserId(sessionId, userId)
            .filter(found -> found.getRevokedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        session.setRevokedAt(LocalDateTime.now(clock));
        events.record(userId, Objects.equals(sessionId, currentId) ? AccountEventType.SIGNED_OUT : AccountEventType.SESSION_REVOKED,
            session.getDevice());
    }

    /** Ends every other session of the account, recording how many ended. */
    @Transactional
    public int endOthers(Long userId, Long currentId) {
        int ended = sessions.revokeAll(userId, currentId == null ? NONE : currentId, LocalDateTime.now(clock));
        if (ended > 0) {
            events.record(userId, AccountEventType.OTHER_SESSIONS_REVOKED, Integer.toString(ended));
        }
        return ended;
    }

    /** Ends every session of the account, for an administrator disabling it. */
    @Transactional
    public int endAll(Long userId) {
        return sessions.revokeAll(userId, NONE, LocalDateTime.now(clock));
    }

    /** Forgets sessions ended over 30 days ago and events over a year old, every night. */
    @Scheduled(cron = "0 5 4 * * *", zone = "Europe/Paris")
    @Transactional
    public void forgetOld() {
        LocalDateTime now = LocalDateTime.now(clock);
        int forgottenSessions = sessions.deleteEndedBefore(now.minus(SESSIONS_KEPT));
        int forgottenEvents = eventRepository.deleteOlderThan(now.minus(EVENTS_KEPT));
        log.info("Forgot {} ended sessions and {} account events", forgottenSessions, forgottenEvents);
    }
}
