package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkInvitationOptOut;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkInvitationOptOutRepository extends JpaRepository<NetworkInvitationOptOut, Long> {

    boolean existsByEmailHash(String emailHash);
}
