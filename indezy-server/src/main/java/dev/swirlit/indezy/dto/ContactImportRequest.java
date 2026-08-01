package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Raw CSV or vCard payload to import contacts from. */
@Data
public class ContactImportRequest {
    @NotBlank(message = "Import content is required")
    private String content;
}
