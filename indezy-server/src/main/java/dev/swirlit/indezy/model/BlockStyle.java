package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.BodyBlock;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How a text block of an invoice's or a quote's body is drawn (see docs/features.md#billing-identity): its text size,
 * its alignment, a frame around it, a grey shading under it, its colour (the text's, the accent or grey), bold and its
 * font family. The mentions and the notes take one, the lines table its shaded rows and filled header; the others keep
 * the layout's.
 */
public record BlockStyle(Size size, Align align, Boolean framed, Boolean shaded, Color color, Boolean bold, Family family) {

    /** The text size: small (the usual), normal (the body's) or large. */
    public enum Size { SMALL, NORMAL, LARGE }

    public enum Align { LEFT, CENTER, RIGHT }

    /** The colour of the block's words: the text's, the documents' accent colour (black without one), or grey. */
    public enum Color { TEXT, ACCENT, GREY }

    /** The block's font family: the document's, or the bundled sans-serif or serif one whatever the document's. */
    public enum Family { DOCUMENT, SANS, SERIF }

    /**
     * The blocks a style applies to: the mentions and the notes wholly; of the lines table, only {@code shaded}
     * (alternate rows shaded) and {@code framed} (its header row filled with the accent colour).
     */
    public static final Set<BodyBlock> STYLED = Set.of(BodyBlock.MENTIONS, BodyBlock.NOTES, BodyBlock.LINES);

    /** As the layout draws a text block. */
    public static final BlockStyle USUAL = new BlockStyle(Size.SMALL, Align.LEFT, false, false, Color.TEXT, false, Family.DOCUMENT);

    public BlockStyle {
        size = size == null ? Size.SMALL : size;
        align = align == null ? Align.LEFT : align;
        color = color == null ? Color.TEXT : color;
        family = family == null ? Family.DOCUMENT : family;
        framed = Boolean.TRUE.equals(framed);
        shaded = Boolean.TRUE.equals(shaded);
        bold = Boolean.TRUE.equals(bold);
    }


    /** The styles of the blocks that take one, the usual ones left out; none for null. */
    public static Map<BodyBlock, BlockStyle> normalize(Map<BodyBlock, BlockStyle> styles) {
        Map<BodyBlock, BlockStyle> kept = new EnumMap<>(BodyBlock.class);
        if (styles != null) {
            styles.forEach((block, style) -> {
                BlockStyle kept1 = block == BodyBlock.LINES && style != null ? new BlockStyle(null, null, style.framed(), style.shaded(), null, null, null) :
                    style;
                if (block != null && kept1 != null && STYLED.contains(block) && !kept1.equals(USUAL)) {
                    kept.put(block, kept1);
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
                entry.getValue().align() + (entry.getValue().framed() ? ",FRAMED" : "") + (entry.getValue().shaded() ? ",SHADED" : "") +
                (entry.getValue().color() == Color.TEXT ? "" : "," + entry.getValue().color()) + (entry.getValue().bold() ? ",BOLD" : "") +
                (entry.getValue().family() == Family.DOCUMENT ? "" : "," + entry.getValue().family()))
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
                    flags.contains("FRAMED"), flags.contains("SHADED"), flags.contains("ACCENT") ? Color.ACCENT : flags.contains("GREY") ? Color.GREY :
                    Color.TEXT, flags.contains("BOLD"), flags.contains("SERIF") ? Family.SERIF : flags.contains("SANS") ? Family.SANS : Family.DOCUMENT));
            } catch (IllegalArgumentException unknown) {
                // A block or a value no longer known is skipped.
            }
        }
        return normalize(styles);
    }

    /** The font families the stored styles ask for besides the document's. */
    public static java.util.Set<Family> families(String stored) {
        java.util.Set<Family> families = java.util.EnumSet.noneOf(Family.class);
        read(stored).values().stream().map(BlockStyle::family).filter(family -> family != Family.DOCUMENT).forEach(families::add);
        return families;
    }

    /** The style of a block, the usual one without its own. */
    public static BlockStyle of(Map<BodyBlock, BlockStyle> styles, BodyBlock block) {
        return styles.getOrDefault(block, USUAL);
    }
}
