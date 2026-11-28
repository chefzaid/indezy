package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.CvShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CvShareRepository extends JpaRepository<CvShare, Long> {

    List<CvShare> findByFreelanceIdOrderByCreatedAtDescIdDesc(Long freelanceId);

    Optional<CvShare> findByIdAndFreelanceId(Long id, Long freelanceId);

    Optional<CvShare> findByTokenHash(String tokenHash);

    void deleteByProfileId(Long profileId);
}
