package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ProspectionCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProspectionCampaignRepository extends JpaRepository<ProspectionCampaign, Long> {

    List<ProspectionCampaign> findTop20ByFreelanceIdOrderByCreatedAtDescIdDesc(Long freelanceId);

    List<ProspectionCampaign> findByFreelanceIdOrderByCreatedAtDescIdDesc(Long freelanceId);

    Optional<ProspectionCampaign> findByIdAndFreelanceId(Long id, Long freelanceId);
}
