package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.InterviewStepDto;
import dev.swirlit.indezy.dto.PipelineTemplateDto;
import dev.swirlit.indezy.dto.PipelineTemplateRequest;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.InterviewStepMapper;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.PipelineTemplate;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.StepChecklistItem;
import dev.swirlit.indezy.model.TemplateStep;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.PipelineTemplateRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Recruitment processes saved from an opportunity's steps and applied to another one: the steps come back in order, to
 * plan, each with its preparation checklist unticked.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PipelineTemplateService {

    static final int MAX_TEMPLATES = 50;

    private final PipelineTemplateRepository templates;
    private final ProjectRepository projects;
    private final ClientRepository clients;
    private final InterviewStepRepository steps;
    private final InterviewStepMapper stepMapper;

    @Transactional(readOnly = true)
    public List<PipelineTemplateDto> list(Long freelanceId) {
        return templates.findByFreelanceIdOrderByNameAsc(freelanceId).stream().map(PipelineTemplateService::toDto).toList();
    }

    /** Saves the opportunity's steps, in the order of its timeline, as a template of its workspace. */
    public PipelineTemplateDto createFromProject(PipelineTemplateRequest request) {
        Project project = projects.findById(request.projectId()).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, request.projectId())));
        Long freelanceId = project.getFreelance().getId();
        String name = request.name().strip();
        if (templates.countByFreelanceId(freelanceId) >= MAX_TEMPLATES) {
            throw new InvalidStateException(String.format(ErrorMessages.PIPELINE_TEMPLATES_LIMIT, MAX_TEMPLATES));
        }
        if (templates.existsByFreelanceIdAndNameIgnoreCase(freelanceId, name)) {
            throw new InvalidStateException(String.format(ErrorMessages.PIPELINE_TEMPLATE_NAME_TAKEN, name));
        }
        List<TemplateStep> templateSteps = steps.findByProjectId(project.getId()).stream()
            .sorted(Comparator.comparing(InterviewStep::getDate, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
                .thenComparing(InterviewStep::getId))
            .map(step -> new TemplateStep(step.getTitle(), step.getChecklist().stream().map(StepChecklistItem::getLabel).toList()))
            .toList();
        if (templateSteps.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessages.PIPELINE_TEMPLATE_WITHOUT_STEPS);
        }
        PipelineTemplate template = new PipelineTemplate();
        template.setName(name);
        template.setFreelance(project.getFreelance());
        template.setClient(request.clientId() == null ? null : clients.findById(request.clientId()).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.CLIENT_NOT_FOUND, request.clientId()))));
        template.getSteps().addAll(templateSteps);
        return toDto(templates.save(template));
    }

    /** Adds the template's steps to the opportunity, to plan, after the ones it has. */
    public List<InterviewStepDto> apply(Long templateId, Long projectId) {
        PipelineTemplate template = find(templateId);
        Project project = projects.findById(projectId).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, projectId)));
        List<InterviewStep> created = template.getSteps().stream().map(templateStep -> {
            InterviewStep step = new InterviewStep();
            step.setTitle(templateStep.getTitle());
            step.setStatus(StepStatus.TO_PLAN);
            step.setProject(project);
            templateStep.checklistItems().forEach(label -> step.getChecklist().add(new StepChecklistItem(label, false)));
            return step;
        }).toList();
        return steps.saveAll(created).stream().map(stepMapper::toDto).toList();
    }

    public void delete(Long id) {
        templates.delete(find(id));
    }

    private PipelineTemplate find(Long id) {
        return templates.findById(id).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.PIPELINE_TEMPLATE_NOT_FOUND, id)));
    }

    private static PipelineTemplateDto toDto(PipelineTemplate template) {
        return new PipelineTemplateDto(template.getId(), template.getName(),
            template.getClient() == null ? null : template.getClient().getId(),
            template.getClient() == null ? null : template.getClient().getCompanyName(),
            template.getSteps().stream().map(step -> new PipelineTemplateDto.Step(step.getTitle(), step.checklistItems())).toList());
    }
}
