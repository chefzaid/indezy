package dev.swirlit.indezy.dto;

import java.time.LocalDateTime;

/** A file kept with an opportunity, without its content; {@code inline} when the browser may show it in the page. */
public record ProjectAttachmentDto(Long id, String fileName, String contentType, long sizeBytes, LocalDateTime createdAt,
                                   boolean inline) {
}
