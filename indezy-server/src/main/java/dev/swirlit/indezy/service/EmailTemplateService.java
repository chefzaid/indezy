package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.EmailTemplateDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.EmailTemplate;
import dev.swirlit.indezy.repository.EmailTemplateRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** A workspace's own email templates, up to {@value #MAX_TEMPLATES}, each name used once (case aside). */
@Service
@RequiredArgsConstructor
@Transactional
public class EmailTemplateService {

    static final int MAX_TEMPLATES = 50;

    private final EmailTemplateRepository templates;
    private final FreelanceRepository freelances;

    @Transactional(readOnly = true)
    public List<EmailTemplateDto> list(Long freelanceId) {
        return templates.findByFreelanceIdOrderByNameAsc(freelanceId).stream().map(EmailTemplateService::toDto).toList();
    }

    public EmailTemplateDto create(Long freelanceId, EmailTemplateDto request) {
        if (templates.countByFreelanceId(freelanceId) >= MAX_TEMPLATES) {
            throw new InvalidStateException(String.format(ErrorMessages.EMAIL_TEMPLATES_LIMIT, MAX_TEMPLATES));
        }
        EmailTemplate template = new EmailTemplate();
        template.setFreelance(freelances.findById(freelanceId).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.FREELANCE_NOT_FOUND, freelanceId))));
        return toDto(templates.save(fill(template, freelanceId, request)));
    }

    public EmailTemplateDto update(Long freelanceId, Long id, EmailTemplateDto request) {
        return toDto(fill(find(freelanceId, id), freelanceId, request));
    }

    public void delete(Long freelanceId, Long id) {
        templates.delete(find(freelanceId, id));
    }

    private EmailTemplate fill(EmailTemplate template, Long freelanceId, EmailTemplateDto request) {
        String name = request.name().strip();
        // A new template has no id yet: no saved template has -1.
        if (templates.existsByFreelanceIdAndNameIgnoreCaseAndIdNot(freelanceId, name, Objects.requireNonNullElse(template.getId(), -1L))) {
            throw new InvalidStateException(String.format(ErrorMessages.EMAIL_TEMPLATE_NAME_TAKEN, name));
        }
        template.setName(name);
        template.setSubject(request.subject().strip());
        template.setBody(request.body().strip());
        return template;
    }

    private EmailTemplate find(Long freelanceId, Long id) {
        return templates.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.EMAIL_TEMPLATE_NOT_FOUND, id)));
    }

    private static EmailTemplateDto toDto(EmailTemplate template) {
        return new EmailTemplateDto(template.getId(), template.getName(), template.getSubject(), template.getBody());
    }
}
