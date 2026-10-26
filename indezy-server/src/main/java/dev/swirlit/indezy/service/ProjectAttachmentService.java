package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.ProjectAttachmentDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.ProjectAttachment;
import dev.swirlit.indezy.repository.ProjectAttachmentRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.util.AttachmentTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * The files kept with an opportunity: up to {@value #MAX_PER_PROJECT} of 10 MB each, of the types
 * {@link AttachmentTypes} recognises from their content.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectAttachmentService {

    static final int MAX_PER_PROJECT = 20;

    private final ProjectAttachmentRepository attachments;
    private final ProjectRepository projects;

    @Transactional(readOnly = true)
    public List<ProjectAttachmentDto> list(Long projectId) {
        return attachments.findSummaries(projectId).stream().map(ProjectAttachmentService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectAttachmentDto> export(Long freelanceId) {
        return attachments.findSummariesByFreelanceId(freelanceId).stream().map(ProjectAttachmentService::toDto).toList();
    }

    public ProjectAttachmentDto upload(Long projectId, MultipartFile file) throws IOException {
        Project project = projects.findById(projectId).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, projectId)));
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessages.ATTACHMENT_EMPTY);
        }
        if (file.getSize() > ProjectAttachment.MAX_BYTES) {
            throw new IllegalArgumentException(ErrorMessages.ATTACHMENT_TOO_LARGE);
        }
        if (attachments.countByProjectId(projectId) >= MAX_PER_PROJECT) {
            throw new InvalidStateException(String.format(ErrorMessages.ATTACHMENTS_LIMIT, MAX_PER_PROJECT));
        }
        byte[] content = file.getBytes();
        String name = fileName(file.getOriginalFilename());
        AttachmentTypes.Type type = AttachmentTypes.detect(name, content)
            .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.ATTACHMENT_TYPE));
        ProjectAttachment attachment = new ProjectAttachment();
        attachment.setProject(project);
        attachment.setFileName(name);
        attachment.setContentType(type.contentType());
        attachment.setSizeBytes(content.length);
        attachment.setContent(content);
        ProjectAttachment saved = attachments.save(attachment);
        log.info("Attached a {} file of {} bytes to project {}", type.contentType(), content.length, projectId);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public ProjectAttachment get(Long id) {
        return attachments.findById(id).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.ATTACHMENT_NOT_FOUND, id)));
    }

    public void delete(Long id) {
        attachments.delete(get(id));
    }

    /** The name a browser sent, without any folder or control character, at most 255 characters. */
    static String fileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            return "attachment";
        }
        return name.length() > ProjectAttachment.NAME_MAX ? name.substring(0, ProjectAttachment.NAME_MAX) : name;
    }

    private static ProjectAttachmentDto toDto(ProjectAttachmentRepository.Summary summary) {
        return new ProjectAttachmentDto(summary.getId(), summary.getFileName(), summary.getContentType(), summary.getSizeBytes(),
            summary.getCreatedAt(), AttachmentTypes.isInline(summary.getContentType()));
    }

    private static ProjectAttachmentDto toDto(ProjectAttachment attachment) {
        return new ProjectAttachmentDto(attachment.getId(), attachment.getFileName(), attachment.getContentType(),
            attachment.getSizeBytes(), attachment.getCreatedAt(), AttachmentTypes.isInline(attachment.getContentType()));
    }
}
