package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.InboxEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InboxEmailRepository extends JpaRepository<InboxEmail, Long> {

    boolean existsByEmailId(String emailId);

    /** The workspace's latest forwarded emails, newest first. */
    List<InboxEmail> findTop20ByFreelanceIdOrderByCreatedAtDescIdDesc(Long freelanceId);
}
