package dev.swirlit.indezy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Outcome of a contact import: how many were created versus skipped as duplicates. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactImportResultDto {
    private int imported;
    private int skipped;
    private int total;
}
