package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ProfileExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileExperienceRepository extends JpaRepository<ProfileExperience, Long> {

    List<ProfileExperience> findByFreelanceId(Long freelanceId);

    Optional<ProfileExperience> findByIdAndFreelanceId(Long id, Long freelanceId);
}
