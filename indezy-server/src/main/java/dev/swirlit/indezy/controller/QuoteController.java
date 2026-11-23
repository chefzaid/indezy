package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.InvoiceDtos;
import dev.swirlit.indezy.dto.QuoteDtos;
import dev.swirlit.indezy.service.ActivityReportService;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** A workspace's quotes: drafts, sending, the client's answer, the invoice and the PDF. Each one answers 404 to another workspace. */
@RestController
@RequestMapping("/quotes")
@RequiredArgsConstructor
@Tag(name = "Quotes", description = "Quotes for fixed-price work, from drafts to the invoice")
public class QuoteController {

    private final QuoteService quotes;
    private final AccessGuard accessGuard;

    @Operation(summary = "The workspace's quotes, newest first")
    @GetMapping
    public List<QuoteDtos.Quote> list() {
        return quotes.list(workspace());
    }

    @Operation(summary = "Write a draft")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteDtos.Quote create(@Valid @RequestBody QuoteDtos.Draft draft) {
        return quotes.create(workspace(), draft);
    }

    @Operation(summary = "Change a draft")
    @PutMapping("/{id}")
    public QuoteDtos.Quote update(@PathVariable Long id, @Valid @RequestBody QuoteDtos.Draft draft) {
        return quotes.update(workspace(), id, draft);
    }

    @Operation(summary = "Delete a draft")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        quotes.delete(workspace(), id);
    }

    @Operation(summary = "Send a draft with the next quote number of the year")
    @PostMapping("/{id}/send")
    public QuoteDtos.Quote send(@PathVariable Long id, @RequestBody(required = false) QuoteDtos.Send request) {
        return quotes.send(workspace(), id, request);
    }

    @Operation(summary = "Record that the client accepted or refused a sent quote")
    @PutMapping("/{id}/decision")
    public QuoteDtos.Quote decide(@PathVariable Long id, @Valid @RequestBody QuoteDtos.Decision decision) {
        return quotes.decide(workspace(), id, decision);
    }

    @Operation(summary = "Draft the invoice of an accepted quote")
    @PostMapping("/{id}/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDtos.Invoice invoice(@PathVariable Long id) {
        return quotes.invoice(workspace(), id);
    }

    @Operation(summary = "Draft the deposit invoice of an accepted quote that asks one")
    @PostMapping("/{id}/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDtos.Invoice deposit(@PathVariable Long id) {
        return quotes.deposit(workspace(), id);
    }

    @Operation(summary = "The quote as a PDF")
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return PdfResponses.attachment(quotes.renderPdf(workspace(), id));
    }

    @Operation(summary = "The Word template filled with the latest document, or a sample before the first")
    @GetMapping("/template-preview")
    public ResponseEntity<byte[]> templatePreview() {
        ActivityReportService.PdfFile file = quotes.previewTemplate(workspace());
        return PdfResponses.attachment(file.fileName(), PdfResponses.DOCX, file.content());
    }

    @Operation(summary = "The quote as a Word file laid out like its PDF")
    @GetMapping("/{id}/docx")
    public ResponseEntity<byte[]> docx(@PathVariable Long id) {
        ActivityReportService.PdfFile file = quotes.renderDocx(workspace(), id);
        return PdfResponses.attachment(file.fileName(), PdfResponses.DOCX, file.content());
    }

    private Long workspace() {
        return accessGuard.requireWorkspace(null);
    }
}
