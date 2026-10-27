package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.AccountSecurityDtos;
import dev.swirlit.indezy.dto.ApiTokenDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.service.AccountEventService;
import dev.swirlit.indezy.service.AccountSessionService;
import dev.swirlit.indezy.service.ApiTokenService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The signed-in account's open sessions, its activity log and its personal API tokens. */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Account security", description = "Open sessions, account activity and API tokens")
public class AccountSecurityController {

    private final AccountSessionService sessions;
    private final AccountEventService events;
    private final ApiTokenService apiTokens;

    @Operation(summary = "The account's open sessions, the current one first")
    @GetMapping("/sessions")
    public List<AccountSecurityDtos.Session> sessions() {
        return sessions.list(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentSessionId());
    }

    @Operation(summary = "Sign out: end the session of this request's token")
    @DeleteMapping("/sessions/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void signOut() {
        Long current = SecurityUtils.getCurrentSessionId();
        if (current == null) {
            throw new ResourceNotFoundException("Session not found");
        }
        sessions.end(SecurityUtils.getCurrentUserId(), current, current);
    }

    @Operation(summary = "End one of the account's sessions")
    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(@PathVariable Long id) {
        sessions.end(SecurityUtils.getCurrentUserId(), id, SecurityUtils.getCurrentSessionId());
    }

    @Operation(summary = "End every session of the account but this one")
    @PostMapping("/sessions/end-others")
    public AccountSecurityDtos.Revoked endOthers() {
        return new AccountSecurityDtos.Revoked(sessions.endOthers(SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentSessionId()));
    }

    @Operation(summary = "The account's activity, newest first, 20 a page")
    @GetMapping("/activity")
    public AccountSecurityDtos.EventPage activity(@RequestParam(defaultValue = "0") int page) {
        return events.page(SecurityUtils.getCurrentUserId(), page);
    }

    @Operation(summary = "The account's API tokens, newest first; the tokens themselves are never shown again")
    @GetMapping("/api-tokens")
    public List<ApiTokenDtos.Token> apiTokens() {
        return apiTokens.list(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Create a read-only API token, returned this once")
    @PostMapping("/api-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiTokenDtos.Created createApiToken(@Valid @RequestBody ApiTokenDtos.Create request) {
        return apiTokens.create(SecurityUtils.getCurrentUserId(), request);
    }

    @Operation(summary = "Revoke an API token")
    @DeleteMapping("/api-tokens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeApiToken(@PathVariable Long id) {
        apiTokens.revoke(SecurityUtils.getCurrentUserId(), id);
    }
}
