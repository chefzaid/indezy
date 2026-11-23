package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ProfileCvDto;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.media.MalwareScanner;
import dev.swirlit.indezy.media.NetworkDocuments;
import dev.swirlit.indezy.media.WordDocText;
import dev.swirlit.indezy.model.ProfileCv;
import dev.swirlit.indezy.repository.ProfileCvRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * The CV kept in the profile (see docs/features.md#profile-import): a PDF, Word ({@code .docx} or {@code .doc}) or OpenDocument file of
 * 10 MB at most, checked for malware, one per workspace.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileCvService {

    private static final Set<String> FORMATS = Set.of("pdf", "docx", "odt");

    private final ProfileCvRepository cvs;
    private final MalwareScanner scanner;

    @Transactional
    public ProfileCvDto keep(Long freelanceId, String originalName, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > ProfileCv.MAX_BYTES) {
            throw new FieldRejectedException("file", "A CV of 10 MB at most");
        }
        String name = fileName(originalName);
        NetworkDocuments.Type type;
        if (WordDocText.is(name, bytes)) {
            // Read once, so a file that only looks like a Word document is refused.
            WordDocText.text(bytes);
            type = WordDocText.TYPE;
        } else {
            try {
                type = NetworkDocuments.detect(name, bytes);
            } catch (IllegalArgumentException e) {
                throw new FieldRejectedException("file", "Keep the CV as a PDF, Word (doc or docx) or OpenDocument file");
            }
            if (!FORMATS.contains(type.extension())) {
                throw new FieldRejectedException("file", "Keep the CV as a PDF, Word (doc or docx) or OpenDocument file");
            }
        }
        scanner.scan(bytes).ifPresent(signature -> {
            log.warn("Workspace {} kept a CV holding {}: refused", freelanceId, signature);
            throw new FieldRejectedException("file", MalwareScanner.refusal(signature));
        });
        ProfileCv cv = cvs.findByFreelanceId(freelanceId).orElseGet(ProfileCv::new);
        cv.setFreelanceId(freelanceId);
        cv.setFileName(name.length() > 200 ? name.substring(name.length() - 200) : name);
        cv.setContentType(type.contentType());
        cv.setSizeBytes(bytes.length);
        cv.setContent(bytes);
        ProfileCv saved = cvs.saveAndFlush(cv);
        log.info("Workspace {} kept a CV of {} bytes", freelanceId, bytes.length);
        return dto(saved);
    }

    @Transactional(readOnly = true)
    public Optional<ProfileCvDto> info(Long freelanceId) {
        return cvs.findByFreelanceId(freelanceId).map(ProfileCvService::dto);
    }

    @Transactional(readOnly = true)
    public ProfileCv file(Long freelanceId) {
        return cvs.findByFreelanceId(freelanceId).orElseThrow(() -> new ResourceNotFoundException("No CV kept"));
    }

    @Transactional
    public void remove(Long freelanceId) {
        cvs.findByFreelanceId(freelanceId).ifPresent(cvs::delete);
    }

    private static ProfileCvDto dto(ProfileCv cv) {
        return new ProfileCvDto(cv.getFileName(), cv.getContentType(), cv.getSizeBytes(), cv.getUpdatedAt() != null ? cv.getUpdatedAt() : cv.getCreatedAt());
    }

    private static String fileName(String original) {
        if (original == null || original.isBlank()) {
            return "cv.pdf";
        }
        String path = original.replace('\\', '/');
        String name = path.substring(path.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
        return name.isEmpty() ? "cv.pdf" : name;
    }
}
