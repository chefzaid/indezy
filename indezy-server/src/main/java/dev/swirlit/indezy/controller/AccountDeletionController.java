package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.service.account.AccountDeletionService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/users/me/deletion")
@RequiredArgsConstructor
@Tag(name = "Account deletion", description = "Delete one's own account, after 30 days on hold")
public class AccountDeletionController {

    private final AccountDeletionService deletion;

    /** The account's email, typed to confirm. */
    public record Request(String confirmation) {

        @Override
        public String toString() {
            return "Request[]";
        }
    }

    /** The day the account will be deleted for good, unless restored by signing in before then. */
    public record Hold(LocalDate purgeOn) {
    }

    @Operation(summary = "Delete one's own account: on hold at once, deleted for good after 30 days",
        description = "Ends every session; signing in within 30 days offers to restore it. 400 when the email typed is not the account's")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Hold request(@RequestBody Request request) {
        return new Hold(deletion.request(SecurityUtils.getCurrentUserId(), request.confirmation()));
    }
}
