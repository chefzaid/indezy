package dev.swirlit.indezy.model.enums;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The blocks of an invoice's or a quote's body, under the seller, the heading and the client (see
 * docs/features.md#billing-identity): the lines with their totals, the mentions (payment terms, bank details, penalties),
 * the notes, and a quote's signature box. Their order is the workspace's; the blocks French law asks for (the lines and
 * totals, the mentions) and the notes typed on each document always print, only the signature box can be left out.
 */
public enum BodyBlock {
    LINES,
    MENTIONS,
    NOTES,
    SIGNATURE;

    /** The order the documents print in when the workspace chose none. */
    public static final List<BodyBlock> USUAL = List.of(LINES, MENTIONS, NOTES, SIGNATURE);

    /** Never left out of a document, wherever the workspace places them. */
    public static final Set<BodyBlock> REQUIRED = EnumSet.of(LINES, MENTIONS, NOTES);

    /** The blocks in the order given, each once, the required ones missing added where they usually are; null for the usual order. */
    public static List<BodyBlock> normalize(List<BodyBlock> blocks) {
        if (blocks == null) {
            return USUAL;
        }
        List<BodyBlock> kept = new ArrayList<>();
        blocks.stream().filter(block -> block != null && !kept.contains(block)).forEach(kept::add);
        for (BodyBlock required : USUAL) {
            if (REQUIRED.contains(required) && !kept.contains(required)) {
                int before = USUAL.indexOf(required);
                // Right after the last block kept that usually comes before it, else first.
                int at = 0;
                for (int index = 0; index < kept.size(); index++) {
                    if (USUAL.indexOf(kept.get(index)) < before) {
                        at = index + 1;
                    }
                }
                kept.add(at, required);
            }
        }
        return List.copyOf(kept);
    }

    /** The order as stored on a column, comma-separated; null for the usual one. */
    public static String store(List<BodyBlock> blocks) {
        List<BodyBlock> normalized = normalize(blocks);
        return normalized.equals(USUAL) ? null : String.join(",", normalized.stream().map(Enum::name).toList());
    }

    /** The order a column holds, unknown names skipped; the usual one when null or blank. */
    public static List<BodyBlock> read(String stored) {
        if (stored == null || stored.isBlank()) {
            return USUAL;
        }
        List<BodyBlock> blocks = new ArrayList<>();
        for (String name : stored.split(",")) {
            try {
                blocks.add(valueOf(name.strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException unknown) {
                // A block no longer known is skipped.
            }
        }
        return normalize(blocks);
    }
}
