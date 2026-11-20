package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NetworkInvitationRepository extends JpaRepository<NetworkInvitation, Long> {

    Optional<NetworkInvitation> findByToken(String token);

    Optional<NetworkInvitation> findFirstByInviterIdAndEmailHash(Long inviterId, String emailHash);

    List<NetworkInvitation> findByEmailHashAndJoinedAtIsNull(String emailHash);

    long countByInviterIdAndCreatedAtAfter(Long inviterId, LocalDateTime since);
}
