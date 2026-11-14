package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.PublicProfileDto;
import dev.swirlit.indezy.dto.PublicProfileSettingsDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.SkillDetail;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A freelancer's public profile: turned on and named by its owner, shown to anyone with its link while it is on. The
 * same "not found" answers an unknown name and a profile turned off, so neither can be told from the other.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PublicProfileService {

    static final String NOT_FOUND = "No public profile at this address";
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final Clock clock;
    private TrainingService trainings;

    /** Optional: without the training record, the page shows no certification. */
    @Autowired(required = false)
    void setTrainings(TrainingService trainings) {
        this.trainings = trainings;
    }

    @Transactional(readOnly = true)
    public PublicProfileSettingsDto settings(Long userId) {
        User user = user(userId);
        return new PublicProfileSettingsDto(user.getPublicProfileEnabled(), user.getPublicProfileSlug());
    }

    /** Saves the settings; showing the profile needs an address name, and a name taken by another account is refused. */
    public PublicProfileSettingsDto update(Long userId, PublicProfileSettingsDto settings) {
        User user = user(userId);
        String slug = settings.slug();
        if (settings.enabled() && slug == null) {
            throw new IllegalArgumentException("Choose the address of the public profile before showing it");
        }
        if (slug != null && users.findByPublicProfileSlug(slug).filter(owner -> !owner.getId().equals(userId)).isPresent()) {
            throw new InvalidStateException("The address " + slug + " is already taken");
        }
        user.setPublicProfileSlug(slug);
        user.setPublicProfileEnabled(settings.enabled());
        users.save(user);
        return new PublicProfileSettingsDto(user.getPublicProfileEnabled(), slug);
    }

    @Transactional(readOnly = true)
    public PublicProfileDto view(String slug) {
        User user = users.findByPublicProfileSlug(slug.toLowerCase(Locale.ROOT))
            .filter(owner -> owner.getPublicProfileEnabled() && owner.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        Optional<Freelance> freelance = freelances.findByEmail(user.getEmail());
        LocalDate available = freelance.map(Freelance::getAvailabilityDate).orElse(null);
        Map<String, SkillDetail> details = user.getSkillDetails();
        return new PublicProfileDto(user.getFirstName(), user.getLastName(), user.getPosition(), user.getCity(), user.getBio(),
            user.getAvatarImage() != null ? user.getAvatarImage() : webLink(user.getAvatar()),
            user.getSkills().stream().map(skill -> {
                SkillDetail detail = details.get(skill);
                return new PublicProfileDto.Skill(skill, detail == null ? null : detail.getLevel(), detail == null ? null : detail.getYears());
            }).toList(),
            user.getLanguages(), webLink(user.getWebsite()), webLink(user.getLinkedin()), webLink(user.getGithub()),
            available, available != null && !available.isAfter(LocalDate.now(clock.withZone(PARIS))),
            certifications(freelance));
    }

    /** The training record's valid certifications; none without a workspace or a training record. */
    private List<String> certifications(Optional<Freelance> freelance) {
        TrainingService record = trainings;
        if (record == null || freelance.isEmpty()) {
            return List.of();
        }
        return record.validCertifications(freelance.get().getId());
    }

    /** A link shown on the page only when it is a web address, never a script or another scheme. */
    static String webLink(String link) {
        if (link == null) {
            return null;
        }
        String trimmed = link.strip();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") ? trimmed : null;
    }

    private User user(Long userId) {
        return users.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));
    }
}
