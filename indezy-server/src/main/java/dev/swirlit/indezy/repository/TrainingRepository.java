package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Training;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingRepository extends JpaRepository<Training, Long> {

    List<Training> findByFreelanceIdOrderByStartsOnDescIdDesc(Long freelanceId);

    Optional<Training> findByIdAndFreelanceId(Long id, Long freelanceId);

    void deleteByFreelanceId(Long freelanceId);
}
