package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.PublicProfileDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicProfileServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final PublicProfileService service = new PublicProfileService(users, freelances,
        Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC));
    private User user;

    @BeforeEach
    void aShownProfile() {
        user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");
        user.setFirstName("Alice");
        user.setAvatar("https://cdn.example/alice.png");
        user.setPublicProfileEnabled(true);
        user.setPublicProfileSlug("alice");
        when(users.findByPublicProfileSlug("alice")).thenReturn(Optional.of(user));
    }

    @Test
    void showsAFutureAvailabilityAndTheLinkedPhotoWhenNoneWasUploaded() {
        Freelance freelance = new Freelance();
        freelance.setAvailabilityDate(LocalDate.of(2026, 11, 2));
        when(freelances.findByEmail("alice@example.com")).thenReturn(Optional.of(freelance));

        PublicProfileDto profile = service.view("ALICE");

        assertThat(profile.availableFrom()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(profile.availableNow()).isFalse();
        assertThat(profile.avatar()).isEqualTo("https://cdn.example/alice.png");
        assertThat(profile.toString()).doesNotContain("Alice");
    }

    @Test
    void saysNothingAboutAHiddenOrDeletedProfile() {
        user.setPublicProfileEnabled(false);
        assertThatThrownBy(() -> service.view("alice")).isInstanceOf(ResourceNotFoundException.class);
        user.setPublicProfileEnabled(true);
        user.setDeletedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        assertThatThrownBy(() -> service.view("alice")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.view("nobody")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void keepsOnlyWebLinks() {
        assertThat(PublicProfileService.webLink(" https://alice.example ")).isEqualTo("https://alice.example");
        assertThat(PublicProfileService.webLink("HTTP://alice.example")).isEqualTo("HTTP://alice.example");
        assertThat(PublicProfileService.webLink("javascript:alert(1)")).isNull();
        assertThat(PublicProfileService.webLink("www.alice.example")).isNull();
        assertThat(PublicProfileService.webLink(null)).isNull();
    }
}
