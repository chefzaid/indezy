package dev.swirlit.indezy.dto;

import java.time.LocalDateTime;

/** The CV kept in the profile, without its content. */
public record ProfileCvDto(String fileName, String contentType, int sizeBytes, LocalDateTime keptAt) {
}
