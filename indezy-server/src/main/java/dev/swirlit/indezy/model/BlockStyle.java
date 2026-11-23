package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.BodyBlock;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How a text block of an invoice's or a quote's body is drawn (see docs/features.md#billing-identity): its text size,
 * its alignment, a frame around it and a grey shading under it. Only the mentions and the notes take one; the others
 * keep the layout's.
 */
public record BlockStyle(Size size, Align align, boolean framed, boolean shaded) {

    /** The text size: small (the usual), normal (the body's) or large. */
    public enum Size { SMALL, NORMAL, LARGE }

    public enum Align { LEFT, CENTER, RIGHT }

    /** The blocks a style applies to. */
    public static final Set<BodyBlock> STYLED = Set.of(BodyBlock.MENTIONS, BodyBlock.NOTES);

    /** As the layout draws a text block. */
    public static final BlockStyle USUAL = new BlockStyle(Size.SMALL, Align.LEFT, false, false);

    public BlockStyle {
        size = size == null ? Size.SMALL : size;
        align = align == null ? Align.LEFT : align;
    }

    /** The styles of the blocks that take one, the usual ones left out; none for null. */
    public static Map<BodyBlock, BlockStyle> normalize(Map<BodyBlock, BlockStyle> styles) {
        Map<BodyBlock, BlockStyle> kept = new EnumMap<>(BodyBlock.class);
        if (styles != null) {
            styles.forEach((block, style) -> {
                if (block != null && style != null && STYLED.contains(block) && !style.equals(USUAL)) {
                    kept.put(block, style);
                }
            });
        }
        return Map.copyOf(kept);
    }

    /** The styles as a column stores them, as {@code NOTES=LARGE,CENTER,FRAMED;MENTIONS=SMALL,LEFT,SHADED}; null for none. */
    public static String store(Map<BodyBlock, BlockStyle> styles) {
        Map<BodyBlock, BlockStyle> kept = normalize(styles);
        if (kept.isEmpty()) {
            return null;
        }
        return kept.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue().size() + "," +
                entry.getValue().align() + (entry.getValue().framed() ? ",FRAMED" : "") + (entry.getValue().shaded() ? ",SHADED" : ""))
            .collect(Collectors.joining(";"));
    }

    /** The styles a column holds, what it cannot read skipped. */
    public static Map<BodyBlock, BlockStyle> read(String stored) {
        Map<BodyBlock, BlockStyle> styles = new EnumMap<>(BodyBlock.class);
        if (stored == null || stored.isBlank()) {
            return Map.of();
        }
        for (String entry : stored.split(";")) {
            String[] parts = entry.split("=", 2);
            if (parts.length < 2) {
                continue;
            }
            try {
                BodyBlock block = BodyBlock.valueOf(parts[0].strip().toUpperCase(Locale.ROOT));
                String[] values = parts[1].toUpperCase(Locale.ROOT).split(",");
                Set<String> flags = Set.of(values);
                styles.put(block, new BlockStyle(Size.valueOf(values[0].strip()), values.length > 1 ? Align.valueOf(values[1].strip()) : null,
                    flags.contains("FRAMED"), flags.contains("SHADED")));
            } catch (IllegalArgumentException unknown) {
                // A block or a value no longer known is skipped.
            }
        }
        return normalize(styles);
    }

    /** The style of a block, the usual one without its own. */
    public static BlockStyle of(Map<BodyBlock, BlockStyle> styles, BodyBlock block) {
        return styles.getOrDefault(block, USUAL);
    }
}
