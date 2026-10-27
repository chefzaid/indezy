package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ApiTokenDtos;
import dev.swirlit.indezy.model.ApiToken;
import dev.swirlit.indezy.repository.ApiTokenRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiTokenServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 12, 0);

    private final ApiTokenRepository tokens = mock(ApiTokenRepository.class);
    private final ApiTokenService service = new ApiTokenService(tokens, mock(AccountEventService.class),
        Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @Test
    void readsOnlyTokensOfItsOwnKindAndNotesTheirUseEveryFiveMinutesAtMost() {
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("eyJhbGciOiJIUzI1NiJ9.e30.x")).isEmpty();
        verify(tokens, never()).findUsable(any(), any());

        ApiToken token = new ApiToken();
        token.setId(3L);
        token.setUserId(7L);
        token.setLastUsedAt(NOW.minusMinutes(1));
        when(tokens.findUsable(ApiTokenService.hash("idz_abc"), NOW)).thenReturn(Optional.of(token));
        assertThat(service.authenticate("idz_abc")).map(ApiTokenDtos.Authenticated::userId).contains(7L);
        verify(tokens, never()).touch(anyLong(), any());

        token.setLastUsedAt(null);
        assertThat(service.authenticate("idz_abc")).map(ApiTokenDtos.Authenticated::userId).contains(7L);
        verify(tokens).touch(3L, NOW);
    }

    @Test
    void createsATokenThatDoesNotExpireWhenAskedSo() {
        ApiTokenDtos.Created created = service.create(7L, new ApiTokenDtos.Create("Backup script", null, null));

        assertThat(created.secret()).startsWith("idz_");
        assertThat(created.token().expiresAt()).isNull();
        assertThat(created.toString()).doesNotContain(created.secret());
        assertThat(ApiTokenService.hash(created.secret())).hasSize(64).isNotEqualTo(created.secret());
    }
}
