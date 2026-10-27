package dev.swirlit.indezy.admin;

import dev.swirlit.indezy.config.FeatureAdoptionMetrics;
import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.AdminDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.AccountEventService;
import dev.swirlit.indezy.service.AccountSessionService;
import dev.swirlit.indezy.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The admin console: usage figures, accounts to look up and disable or enable again, and features to turn on or off.
 * Every call needs an administrator ({@link AdminProperties}); each change is logged with ids only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminService {

    static final int PAGE_SIZE = 20;

    private final UserRepository users;
    private final AdminProperties admins;
    private final FeatureAdoptionMetrics adoption;
    private final FeatureFlagService flags;
    private final AccountSessionService sessions;
    private final AccountEventService events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isAdmin(Long userId) {
        return users.findById(userId).map(user -> admins.isAdmin(user.getEmail())).orElse(false);
    }

    @Transactional(readOnly = true)
    public void requireAdmin(Long userId) {
        if (!isAdmin(userId)) {
            throw new AccessDeniedException("Administrators only");
        }
    }

    @Transactional(readOnly = true)
    public AdminDtos.Overview overview() {
        Map<String, Long> features = new LinkedHashMap<>(adoption.counts());
        long accounts = features.getOrDefault("accounts", 0L);
        features.remove("accounts");
        return new AdminDtos.Overview(accounts, users.countByDisabledAtIsNotNull(),
            users.countByCreatedAtAfter(LocalDateTime.now(clock).minusDays(30)), features);
    }

    @Transactional(readOnly = true)
    public AdminDtos.AccountPage accounts(String query, int page) {
        PageRequest request = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        String text = query == null ? "" : query.strip();
        Page<User> found = users.search(text, request);
        return new AdminDtos.AccountPage(found.map(this::toDto).getContent(), found.getTotalElements(), request.getPageNumber(), PAGE_SIZE);
    }

    /** Disables or enables again an account; an administrator's own account and other administrators' stay as they are. */
    public AdminDtos.Account setDisabled(Long adminId, Long accountId, boolean disabled) {
        User account = users.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, accountId)));
        if (admins.isAdmin(account.getEmail())) {
            throw new InvalidStateException("An administrator's account cannot be disabled here");
        }
        account.setDisabledAt(disabled ? LocalDateTime.now(clock) : null);
        users.save(account);
        if (disabled) {
            sessions.endAll(accountId);
        }
        events.record(accountId, disabled ? AccountEventType.ACCOUNT_DISABLED : AccountEventType.ACCOUNT_ENABLED);
        log.info("Administrator {} {} account {}", adminId, disabled ? "disabled" : "enabled", accountId);
        return toDto(account);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.FeatureState> features() {
        return flags.all().entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(entry -> new AdminDtos.FeatureState(entry.getKey(), entry.getValue())).toList();
    }

    public List<AdminDtos.FeatureState> setFeature(Long adminId, FeatureFlag flag, boolean enabled) {
        flags.set(flag, enabled);
        log.info("Administrator {} turned {} {}", adminId, flag, enabled ? "on" : "off");
        return features();
    }

    private AdminDtos.Account toDto(User user) {
        return new AdminDtos.Account(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getCreatedAt(),
            user.getDisabledAt(), user.getDeletedAt() != null, admins.isAdmin(user.getEmail()));
    }
}
