package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.AccountSecurityDtos;
import dev.swirlit.indezy.model.AccountEvent;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.repository.AccountEventRepository;
import dev.swirlit.indezy.util.RequestDevice;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The account's activity log: what happened to it, from which browser, kept a year. */
@Service
@RequiredArgsConstructor
public class AccountEventService {

    static final int PAGE_SIZE = 20;

    private final AccountEventRepository events;

    /** Records the event on its own, so a refused sign-in is kept although the sign-in fails. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, AccountEventType type, String detail) {
        AccountEvent event = new AccountEvent();
        event.setUserId(userId);
        event.setType(type);
        event.setDetail(detail);
        event.setDevice(RequestDevice.current());
        events.save(event);
    }

    public void record(Long userId, AccountEventType type) {
        record(userId, type, null);
    }

    /** Every event kept for the account, newest first, for its data export. */
    @Transactional(readOnly = true)
    public List<AccountSecurityDtos.Event> export(Long userId) {
        return events.findByUserIdOrderByCreatedAtDescIdDesc(userId, Pageable.unpaged()).map(AccountEventService::toDto).getContent();
    }

    @Transactional(readOnly = true)
    public AccountSecurityDtos.EventPage page(Long userId, int page) {
        Page<AccountEvent> found = events.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        return new AccountSecurityDtos.EventPage(found.map(AccountEventService::toDto).getContent(), found.getTotalElements(),
            found.getNumber(), PAGE_SIZE);
    }

    private static AccountSecurityDtos.Event toDto(AccountEvent event) {
        return new AccountSecurityDtos.Event(event.getId(), event.getType(), event.getDetail(), event.getDevice(), event.getCreatedAt());
    }
}
