package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ProjectReferral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectReferralRepository extends JpaRepository<ProjectReferral, Long> {

    Optional<ProjectReferral> findByProjectId(Long projectId);

    List<ProjectReferral> findByProjectIdIn(Collection<Long> projectIds);
}
