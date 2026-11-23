package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.DocumentFontFile;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.DocumentFont;
import dev.swirlit.indezy.repository.DocumentFontFileRepository;

import java.util.Optional;

/**
 * The faces invoices and quotes print in: the bundled Liberation Sans or Serif, or the TrueType font the seller uploaded
 * (its bold face, or its regular one when it has none), the sans-serif one when that file is gone.
 */
final class BillingFonts {

    private final byte[] sans = PdfFonts.read(PdfFonts.REGULAR);
    private final byte[] sansBold = PdfFonts.read(PdfFonts.BOLD);
    private final byte[] serif = PdfFonts.read(PdfFonts.SERIF_REGULAR);
    private final byte[] serifBold = PdfFonts.read(PdfFonts.SERIF_BOLD);

    /** A regular face and a bold one. */
    record Faces(byte[] regular, byte[] bold) {

        Faces {
            regular = regular.clone();
            bold = bold.clone();
        }
    }

    Faces of(InvoiceSeller seller, DocumentFontFileRepository uploaded) {
        if (seller.getFont() == DocumentFont.CUSTOM && seller.getFontFileId() != null && uploaded != null) {
            Optional<DocumentFontFile> file = uploaded.findById(seller.getFontFileId());
            if (file.isPresent()) {
                return new Faces(file.get().getRegular(), file.get().getBold() != null ? file.get().getBold() : file.get().getRegular());
            }
        }
        return seller.getFont() == DocumentFont.SERIF ? new Faces(serif, serifBold) : new Faces(sans, sansBold);
    }

    /** The family the Word copy names. */
    static String family(InvoiceSeller seller) {
        return switch (seller.getFont() == null ? DocumentFont.SANS : seller.getFont()) {
            case SERIF -> "Liberation Serif";
            case CUSTOM -> seller.getFontFamily() != null ? seller.getFontFamily() : "Liberation Sans";
            case SANS -> "Liberation Sans";
        };
    }
}
