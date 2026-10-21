package dev.swirlit.indezy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A posting read from a site the feed does not collect, added to the pipeline as the project form corrected its draft:
 * an unset client is the one named {@code clientName} and an unset source the one named {@code sourceName} (at
 * {@code sourceLink}), each created when the workspace has none of that name (see {@link OfferDraftDto}).
 */
@Data
public class PostingImportRequest {

    @Valid
    @NotNull
    private ProjectDto project;

    @NotBlank
    @Size(max = 200)
    private String clientName;

    @NotBlank
    @Size(max = 255)
    private String sourceName;

    @Size(max = 1000)
    @Pattern(regexp = "https?://\\S+")
    private String sourceLink;
}
