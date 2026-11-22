package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ProjectChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface ProjectChatMessageRepository extends JpaRepository<ProjectChatMessage, Long> {

    List<ProjectChatMessage> findByProjectIdOrderByIdAsc(Long projectId);

    List<ProjectChatMessage> findByProjectIdInOrderByIdAsc(Collection<Long> projectIds);

    @Transactional
    void deleteByProjectId(Long projectId);
}
