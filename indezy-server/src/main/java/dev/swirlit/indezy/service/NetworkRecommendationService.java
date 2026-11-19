package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.TestimonialDtos;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A member's recommendations on their network page (see docs/features.md#member-network): the testimonials they approved
 * for their public page, which their authors agreed to sign, shown to members; a private card's to its followers only.
 */
@Service
@RequiredArgsConstructor
public class NetworkRecommendationService {

    private final NetworkService network;
    private final NetworkFollowService follows;
    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final TestimonialService testimonials;

    /** The member's approved testimonials, the latest first; none for a private card the viewer does not follow. */
    @Transactional(readOnly = true)
    public List<TestimonialDtos.Shown> recommendations(Long viewerId, String handle) {
        NetworkMember member = follows.visible(network.requireMember(viewerId), handle);
        if (follows.locked(viewerId).contains(member.getUserId())) {
            return List.of();
        }
        return users.findById(member.getUserId()).flatMap(user -> freelances.findByEmail(user.getEmail()))
            .map(freelance -> testimonials.approved(freelance.getId())).orElse(List.of());
    }
}
