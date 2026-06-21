package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectNoteDto {
    private Long id;
    private Long projectId;

    @NotBlank
    private String content;

    private LocalDateTime createdAt;
}
