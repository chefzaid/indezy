package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ContractCheckDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.ProjectMapper;
import dev.swirlit.indezy.model.ContractCheck;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * A mission's contract checklist (see docs/features.md#contract-checklist): the points of the contract checked before
 * signing, each with what the contract says, replaced whole.
 */
@Service
@RequiredArgsConstructor
public class ContractCheckService {

    private final ProjectRepository projects;
    private final ProjectMapper mapper;

    @Transactional
    public ProjectDto replace(Long projectId, List<ContractCheckDto> checks) {
        if (checks.stream().map(ContractCheckDto::point).distinct().count() != checks.size()) {
            throw new IllegalArgumentException("Each contract point at most once");
        }
        Project project = projects.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
        project.setContractChecks(checks.stream()
            .sorted(Comparator.comparing(ContractCheckDto::point))
            .map(check -> new ContractCheck(check.point(), check.checked(),
                check.note() == null || check.note().isBlank() ? null : check.note().strip()))
            .toList());
        return mapper.toDto(projects.save(project));
    }
}
