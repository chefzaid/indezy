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
        return serif(seller) ? new Faces(serif, serifBold) : new Faces(sans, sansBold);
    }

    /** The bundled faces of a family a block asks for. */
    Faces family(dev.swirlit.indezy.model.BlockStyle.Family family) {
        return family == dev.swirlit.indezy.model.BlockStyle.Family.SERIF ? new Faces(serif, serifBold) : new Faces(sans, sansBold);
    }

    /** Serif when chosen, and for the classic theme unless an uploaded font is. */
    private static boolean serif(InvoiceSeller seller) {
        return seller.getFont() == DocumentFont.SERIF || seller.getTheme() == dev.swirlit.indezy.model.enums.DocumentTheme.CLASSIC &&
            (seller.getFont() == null || seller.getFont() == DocumentFont.SANS || seller.getFontFileId() == null);
    }

    /** The family the Word copy names. */
    static String family(InvoiceSeller seller) {
        if (seller.getFont() == DocumentFont.CUSTOM && seller.getFontFamily() != null && seller.getFontFileId() != null) {
            return seller.getFontFamily();
        }
        return serif(seller) ? "Liberation Serif" : "Liberation Sans";
    }
}
