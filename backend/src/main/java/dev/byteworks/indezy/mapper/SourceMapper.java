package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.SourceDto;
import dev.byteworks.indezy.model.Project;
import dev.byteworks.indezy.model.Source;
import org.springframework.stereotype.Component;

@Component
public class SourceMapper {

    public SourceDto toDto(Source source) {
        if (source == null) {
            return null;
        }

        SourceDto dto = new SourceDto();
        dto.setId(source.getId());
        dto.setName(source.getName());
        dto.setType(source.getType());
        dto.setLink(source.getLink());
        dto.setIsListing(source.getIsListing());
        dto.setPopularityRating(source.getPopularityRating());
        dto.setUsefulnessRating(source.getUsefulnessRating());
        dto.setNotes(source.getNotes());
        dto.setCreatedAt(source.getCreatedAt());
        dto.setUpdatedAt(source.getUpdatedAt());
        
        if (source.getFreelance() != null) {
            dto.setFreelanceId(source.getFreelance().getId());
        }

        // Computed fields - getProjects() never returns null due to defensive copying
        dto.setTotalProjects(source.getProjects().size());
        dto.setAverageDailyRate(
            source.getProjects().stream()
                .filter(p -> p.getDailyRate() != null)
                .mapToInt(Project::getDailyRate)
                .average()
                .orElse(0.0)
        );
        // Note: Projects collection mapping would be handled by ProjectMapper if needed
        dto.setProjects(new java.util.ArrayList<>());

        return dto;
    }

    public Source toEntity(SourceDto dto) {
        if (dto == null) {
            return null;
        }

        Source source = new Source();
        source.setId(dto.getId());
        source.setName(dto.getName());
        source.setType(dto.getType());
        source.setLink(dto.getLink());
        source.setIsListing(dto.getIsListing());
        source.setPopularityRating(dto.getPopularityRating());
        source.setUsefulnessRating(dto.getUsefulnessRating());
        source.setNotes(dto.getNotes());

        return source;
    }

    public void updateEntity(SourceDto dto, Source source) {
        if (dto == null || source == null) {
            return;
        }

        source.setName(dto.getName());
        source.setType(dto.getType());
        source.setLink(dto.getLink());
        source.setIsListing(dto.getIsListing());
        source.setPopularityRating(dto.getPopularityRating());
        source.setUsefulnessRating(dto.getUsefulnessRating());
        source.setNotes(dto.getNotes());
    }
}
