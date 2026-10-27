package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.model.enums.SignInMethod;

import java.time.LocalDateTime;
import java.util.List;

/** The account's open sessions and its activity log. */
public final class AccountSecurityDtos {

    private AccountSecurityDtos() {
    }

    public record Session(Long id, SignInMethod method, String device, LocalDateTime createdAt, LocalDateTime lastSeenAt,
                          LocalDateTime expiresAt, boolean current) {
    }

    public record Event(Long id, AccountEventType type, String detail, String device, LocalDateTime occurredAt) {
    }

    public record EventPage(List<Event> events, long total, int page, int size) {
        public EventPage {
            events = List.copyOf(events);
        }
    }

    public record Revoked(int count) {
    }
}
