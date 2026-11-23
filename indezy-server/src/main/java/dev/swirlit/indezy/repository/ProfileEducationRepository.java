package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ProfileEducation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileEducationRepository extends JpaRepository<ProfileEducation, Long> {

    List<ProfileEducation> findByFreelanceId(Long freelanceId);

    Optional<ProfileEducation> findByIdAndFreelanceId(Long id, Long freelanceId);
}
