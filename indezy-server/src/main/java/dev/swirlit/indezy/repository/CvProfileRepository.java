package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.CvProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CvProfileRepository extends JpaRepository<CvProfile, Long> {

    List<CvProfile> findByFreelanceIdOrderByCreatedAtAscIdAsc(Long freelanceId);

    Optional<CvProfile> findByIdAndFreelanceId(Long id, Long freelanceId);

    Optional<CvProfile> findFirstByFreelanceIdAndDefaultProfileTrue(Long freelanceId);

    long countByFreelanceId(Long freelanceId);
}
