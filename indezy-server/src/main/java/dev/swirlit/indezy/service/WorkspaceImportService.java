package dev.swirlit.indezy.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.swirlit.indezy.dto.WorkspaceImportFile;
import dev.swirlit.indezy.dto.WorkspaceImportReport;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Brings an Indezy account export into a workspace: its sources, clients and ESNs, contacts, seasons, opportunities with
 * their interview steps, and journal notes. A record the workspace already holds (same name, case and accents aside; a
 * contact by email, else by name at the same company; an opportunity by role, client and start date) is a duplicate and
 * stays as it is, the imported records that point at it pointing at the existing one. A record that breaks the API's
 * rules, or points at a record the file does not hold, is left out with why. The preview runs the same matching without
 * writing anything, and an import writes everything or, on an unexpected failure, nothing.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceImportService {

    private final ImportWriters writers;
    private final ImportWorkspaceIndex index;
    private final Validator validator;

    // Self-contained reader, as the export writes with its own mapper; sections the import does not read are skipped.
    private final ObjectMapper reader = new ObjectMapper().findAndRegisterModules()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Transactional(readOnly = true)
    public WorkspaceImportReport preview(Long freelanceId, byte[] file) {
        return new WorkspaceImportRun(freelanceId, index.of(freelanceId), validator, null).run(read(file));
    }

    @Transactional
    public WorkspaceImportReport apply(Long freelanceId, byte[] file) {
        return new WorkspaceImportRun(freelanceId, index.of(freelanceId), validator, writers).run(read(file));
    }

    WorkspaceImportFile read(byte[] file) {
        WorkspaceImportFile parsed;
        try {
            parsed = reader.readValue(file, WorkspaceImportFile.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("The file is not an Indezy export", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("The file could not be read", e);
        }
        if (parsed == null || parsed.isEmpty()) {
            throw new IllegalArgumentException("The file holds nothing an import can bring in");
        }
        return parsed;
    }
}
