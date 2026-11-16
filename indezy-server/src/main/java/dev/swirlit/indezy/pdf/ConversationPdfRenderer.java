package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

/**
 * Renders a conversation between two members as an A4 PDF, for the reader to keep: who it was with, then each message
 * with its sender and time, the message it replies to, and whether it was edited or deleted (ADR 0008 fonts).
 */
@Component
public final class ConversationPdfRenderer {

    private static final float TITLE = 16;
    private static final float BODY = 10;
    private static final float SMALL = 8.5f;

    private final byte[] regularFont = PdfFonts.read(PdfFonts.REGULAR);
    private final byte[] boldFont = PdfFonts.read(PdfFonts.BOLD);

    /** A message as the PDF writes it. */
    public record Line(String sender, LocalDateTime sentAt, String text, boolean edited, boolean deleted, String replyTo) {

        @Override
        public String toString() {
            return "Line[sentAt=" + sentAt + "]";
        }
    }

    /** What the PDF is about: the reader's name, the other member's name and handle, and the messages, the oldest first. */
    public record Conversation(String readerName, String withName, String withHandle, List<Line> lines) {

        public Conversation {
            lines = List.copyOf(lines);
        }

        @Override
        public String toString() {
            return "Conversation[lines=" + lines.size() + "]";
        }
    }

    public byte[] render(Conversation conversation, DocumentLanguage language, LocalDate today) {
        boolean french = language == DocumentLanguage.FR;
        DateTimeFormatter day = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(language.locale());
        DateTimeFormatter time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(language.locale());
        String title = (french ? "Conversation avec " : "Conversation with ") + conversation.withName() + " (@" + conversation.withHandle() + ")";
        try (PDDocument document = new PDDocument()) {
            PDFont regular = PDType0Font.load(document, new ByteArrayInputStream(regularFont));
            PDFont bold = PDType0Font.load(document, new ByteArrayInputStream(boldFont));
            try (PdfPageWriter writer = new PdfPageWriter(document, PDRectangle.A4, regular, bold)) {
                writer.paragraph(title, true, TITLE, writer.width());
                writer.paragraph((french ? "Exportée le " : "Exported on ") + day.format(today) + " · Indezy", false, SMALL, writer.width());
                writer.moveDown(BODY);
                for (Line line : conversation.lines()) {
                    writer.ensureSpace(BODY * 4);
                    String sender = line.sender() == null ? (french ? "Vous" : "You") : line.sender();
                    String meta = time.format(line.sentAt()) + (line.edited() && !line.deleted() ? french ? " · modifié" : " · edited" : "");
                    writer.paragraph(sender + "  —  " + meta, true, SMALL, writer.width());
                    if (line.replyTo() != null) {
                        writer.paragraph((french ? "> En réponse à : " : "> Replying to: ") + line.replyTo(), false, SMALL, writer.width());
                    }
                    String text = line.deleted() ? french ? "Message supprimé" : "Message deleted" : line.text();
                    writer.paragraph(text, false, BODY, writer.width());
                    writer.moveDown(BODY * 0.8f);
                }
            }
            PDDocumentInformation information = document.getDocumentInformation();
            information.setTitle(title);
            information.setAuthor(conversation.readerName());
            information.setCreator("Indezy");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render the conversation", e);
        }
    }
}
