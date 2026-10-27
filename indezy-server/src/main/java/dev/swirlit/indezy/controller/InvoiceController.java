package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.InvoiceDtos;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.AccountingExportService;
import dev.swirlit.indezy.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** A workspace's invoices: drafts, issuing, payment and PDF. Each one answers 404 to another workspace. */
@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices", description = "Invoices to clients, from drafts to payment")
public class InvoiceController {

    private final InvoiceService invoices;
    private final AccountingExportService accountingExport;
    private final AccessGuard accessGuard;

    @Operation(summary = "The workspace's invoices, newest first")
    @GetMapping("/by-freelance/{freelanceId}")
    public List<InvoiceDtos.Invoice> list(@PathVariable Long freelanceId) {
        accessGuard.requireFreelance(freelanceId);
        return invoices.list(freelanceId);
    }

    @Operation(summary = "One invoice")
    @GetMapping("/{id}")
    public InvoiceDtos.Invoice get(@PathVariable Long id) {
        return invoices.get(workspace(), id);
    }

    @Operation(summary = "Write a draft by hand")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDtos.Invoice create(@Valid @RequestBody InvoiceDtos.Draft draft) {
        return invoices.create(workspace(), draft);
    }

    @Operation(summary = "Draft the invoice of a validated activity report")
    @PostMapping("/from-report")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDtos.Invoice fromReport(@Valid @RequestBody InvoiceDtos.FromReport request) {
        accessGuard.requireProject(request.projectId());
        return invoices.fromReport(workspace(), request);
    }

    @Operation(summary = "Draft a credit note of an issued invoice")
    @PostMapping("/{id}/credit-note")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDtos.Invoice creditNote(@PathVariable Long id) {
        return invoices.creditNote(workspace(), id);
    }

    @Operation(summary = "Change a draft")
    @PutMapping("/{id}")
    public InvoiceDtos.Invoice update(@PathVariable Long id, @Valid @RequestBody InvoiceDtos.Draft draft) {
        return invoices.update(workspace(), id, draft);
    }

    @Operation(summary = "Delete a draft")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        invoices.delete(workspace(), id);
    }

    @Operation(summary = "Issue a draft with the next number of the year")
    @PostMapping("/{id}/issue")
    public InvoiceDtos.Invoice issue(@PathVariable Long id, @RequestBody(required = false) InvoiceDtos.Issue request) {
        return invoices.issue(workspace(), id, request);
    }

    @Operation(summary = "Record when an issued invoice was paid, or that it is not")
    @PutMapping("/{id}/payment")
    public InvoiceDtos.Invoice pay(@PathVariable Long id, @RequestBody InvoiceDtos.Payment payment) {
        return invoices.pay(workspace(), id, payment);
    }

    @Operation(summary = "The invoice as a PDF")
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return PdfResponses.attachment(invoices.renderPdf(workspace(), id));
    }

    @Operation(summary = "A year of issued invoices for the accountant",
        description = "FEC journal entries (sales and bank), or the register of invoices and credit notes as CSV")
    @GetMapping("/export/{year}")
    public ResponseEntity<byte[]> export(@PathVariable @Min(2000) @Max(2100) int year,
                                         @RequestParam(defaultValue = "FEC") AccountingExportService.Format format,
                                         @RequestParam(defaultValue = "FR") DocumentLanguage language) {
        AccountingExportService.ExportFile file = accountingExport.export(workspace(), year, format, language);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.parseMediaType(file.contentType()))
            .body(file.content());
    }

    private Long workspace() {
        return accessGuard.requireWorkspace(null);
    }
}
