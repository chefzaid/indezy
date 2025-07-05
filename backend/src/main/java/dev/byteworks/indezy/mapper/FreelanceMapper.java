package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.FreelanceDto;
import dev.byteworks.indezy.model.Freelance;
import org.springframework.stereotype.Component;

@Component
public class FreelanceMapper {

    public FreelanceDto toDto(Freelance freelance) {
        if (freelance == null) {
            return null;
        }

        FreelanceDto dto = new FreelanceDto();
        dto.setId(freelance.getId());
        dto.setFirstName(freelance.getFirstName());
        dto.setLastName(freelance.getLastName());
        dto.setEmail(freelance.getEmail());
        dto.setPhone(freelance.getPhone());
        dto.setBirthDate(freelance.getBirthDate());
        dto.setAddress(freelance.getAddress());
        dto.setCity(freelance.getCity());
        dto.setStatus(freelance.getStatus());
        dto.setNoticePeriodInDays(freelance.getNoticePeriodInDays());
        dto.setAvailabilityDate(freelance.getAvailabilityDate());
        dto.setReversionRate(freelance.getReversionRate());
        dto.setCvFilePath(freelance.getCvFilePath());

        // Computed fields
        dto.setFullName(freelance.getFullName());
        if (freelance.getProjects() != null) {
            dto.setTotalProjects(freelance.getProjects().size());
            dto.setAverageDailyRate(
                freelance.getProjects().stream()
                    .filter(p -> p.getDailyRate() != null)
                    .mapToInt(p -> p.getDailyRate())
                    .average()
                    .orElse(0.0)
            );
        }

        return dto;
    }

    public Freelance toEntity(FreelanceDto dto) {
        if (dto == null) {
            return null;
        }

        Freelance freelance = new Freelance();
        freelance.setId(dto.getId());
        freelance.setFirstName(dto.getFirstName());
        freelance.setLastName(dto.getLastName());
        freelance.setEmail(dto.getEmail());
        freelance.setPhone(dto.getPhone());
        freelance.setBirthDate(dto.getBirthDate());
        freelance.setAddress(dto.getAddress());
        freelance.setCity(dto.getCity());
        freelance.setStatus(dto.getStatus());
        freelance.setNoticePeriodInDays(dto.getNoticePeriodInDays());
        freelance.setAvailabilityDate(dto.getAvailabilityDate());
        freelance.setReversionRate(dto.getReversionRate());
        freelance.setCvFilePath(dto.getCvFilePath());

        return freelance;
    }

    public void updateEntity(FreelanceDto dto, Freelance freelance) {
        if (dto == null || freelance == null) {
            return;
        }

        freelance.setFirstName(dto.getFirstName());
        freelance.setLastName(dto.getLastName());
        freelance.setEmail(dto.getEmail());
        freelance.setPhone(dto.getPhone());
        freelance.setBirthDate(dto.getBirthDate());
        freelance.setAddress(dto.getAddress());
        freelance.setCity(dto.getCity());
        freelance.setStatus(dto.getStatus());
        freelance.setNoticePeriodInDays(dto.getNoticePeriodInDays());
        freelance.setAvailabilityDate(dto.getAvailabilityDate());
        freelance.setReversionRate(dto.getReversionRate());
        if (dto.getCvFilePath() != null) {
            freelance.setCvFilePath(dto.getCvFilePath());
        }
    }
}
