package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ProfileCv;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileCvRepository extends JpaRepository<ProfileCv, Long> {

    Optional<ProfileCv> findByFreelanceId(Long freelanceId);
}
