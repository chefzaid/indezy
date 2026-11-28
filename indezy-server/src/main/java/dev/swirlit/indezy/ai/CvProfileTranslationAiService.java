package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.dto.CvProfileDtos;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.service.CvProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A CV profile's twin in the other language (see docs/features.md#cv-profiles): its headline, summary and the wording of
 * each mission and position it shows translated by AI, then kept as the twin, each edited on its own afterwards. Only
 * the wording is sent: no name, contact detail or client.
 */
@Service
@RequiredArgsConstructor
public class CvProfileTranslationAiService {

    private static final long MAX_TOKENS = 12_000;
    private static final String SYSTEM = """
        You translate the blocks of a freelance IT consultant's CV (dossier de compétences) into the language asked. Translate
        only: keep every fact, figure, name, tool and technical term as it is, add nothing and leave nothing out. Keep the
        style of dossiers: in the third person or without a subject, plainly. Answer each block by its key.""";

    private final AiService ai;
    private final CvProfileService profiles;

    /** A block translated, by its key. */
    public record Block(@JsonPropertyDescription("The block's key as given") String key,
                        @JsonPropertyDescription("The block translated") String text) {
    }

    /** The blocks the AI translated. */
    public record Translation(@JsonPropertyDescription("Each block given, translated") List<Block> blocks) {

        public Translation {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }

    /** The profile's twin written (or rewritten) in the other language. */
    public CvProfileDtos.Profile translate(Long userId, Long freelanceId, Long id) {
        CvProfileDtos.Wording wording = profiles.wording(userId, freelanceId, id);
        Map<String, String> translated = wording.blocks().isEmpty() ? Map.of() : translated(wording, ai.structured(userId, AiFeature.DOSSIER, SYSTEM,
            context(wording), Translation.class, MAX_TOKENS, OutputConfig.Effort.LOW));
        return profiles.saveTwin(freelanceId, id, translated);
    }

    static String context(CvProfileDtos.Wording wording) {
        StringBuilder context = new StringBuilder("Translate into ").append(wording.language() == DocumentLanguage.EN ? "English" : "French").append(".\n");
        wording.blocks().forEach(block -> context.append("<block key=\"").append(block.key()).append("\">\n").append(block.text()).append("\n</block>\n"));
        return context.toString();
    }

    /** The translation by key, only for the blocks given; a block the AI left out keeps its own wording. */
    static Map<String, String> translated(CvProfileDtos.Wording wording, Translation translation) {
        Set<String> keys = wording.blocks().stream().map(CvProfileDtos.Block::key).collect(Collectors.toSet());
        Map<String, String> byKey = new LinkedHashMap<>();
        wording.blocks().forEach(block -> byKey.put(block.key(), block.text()));
        translation.blocks().stream().filter(block -> block != null && block.key() != null && keys.contains(block.key().strip()))
            .filter(block -> block.text() != null && !block.text().isBlank())
            .forEach(block -> byKey.put(block.key().strip(), Objects.requireNonNull(block.text()).strip()));
        return byKey;
    }
}
