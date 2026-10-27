package dev.swirlit.indezy.pdf;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** The Liberation Sans fonts embedded in the generated PDFs (ADR 0008), read from the classpath. */
final class PdfFonts {

    static final String REGULAR = "fonts/LiberationSans-Regular.ttf";
    static final String BOLD = "fonts/LiberationSans-Bold.ttf";

    private PdfFonts() {
    }

    static byte[] read(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Missing PDF font " + path, e);
        }
    }
}
