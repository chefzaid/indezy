# ADR 0008: Generate PDF Documents With Apache PDFBox

- Status: Accepted
- Date: 2026-10-19

## Context

Monthly activity reports (CRA) must be downloadable as PDF by the freelance and by the client or ESN that validates
them through a link, laid out with the client's template (daily table or month grid, French or English, its own title
and footer) and carrying the electronic validation. The same server-side capability will produce invoices, and the
French e-invoicing rules then need Factur-X: a PDF/A-3 file with embedded structured data.

A browser print stylesheet cannot be attached, stored or reproduced on the server, and HTML-to-PDF engines bring a
browser or a layout engine into the backend image.

## Decision

- Render PDF documents on the server with Apache PDFBox (`org.apache.pdfbox:pdfbox`, Apache License 2.0), drawn
  directly in the `pdf` package: `ActivityReportPdfRenderer` builds the document, `ActivityReportPage` lays out an
  activity report, and `PdfPageWriter` writes text, lines and shading and starts a new page when a block does not fit.
- Embed Liberation Sans Regular and Bold (SIL Open Font License 1.1, bundled under `src/main/resources/fonts` with its
  license) as subset fonts, so accents, ligatures such as "œ" and names print the same in every viewer. Characters
  the font cannot draw are replaced by a question mark instead of failing the document.
- Keep documents free of persistence: the service reads the report, its mission and the client template into an
  `ActivityReportDocument`, and the renderer only turns it into bytes, which keeps it unit-testable through PDFBox's
  text extraction.

## Rationale

PDFBox is the reference Java PDF library, maintained by the Apache Software Foundation, has no native dependency, and
already supports the PDF/A-3 attachments Factur-X needs. Drawing with its content streams keeps full control of the
one-page layouts ESNs expect, at the cost of writing the layout code ourselves.

## Consequences

- The backend gains `pdfbox`, `pdfbox-io`, `fontbox` and `commons-logging` (about 3.8 MB) and two fonts (about 0.8 MB).
- New documents reuse `PdfPageWriter`; a layout change is code, reviewed and tested like any other.
- Invoice and Factur-X work starts from this renderer instead of choosing a library again.
