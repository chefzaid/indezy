A **Word template** lays out your invoices or your quotes as you like: you write it in Word, put placeholders where the values go, and Indezy fills it for each document when you click **Download as Word**. This page lists every placeholder and how to use them.

## Upload a template

1. In Word, lay out an invoice (or a quote) and type the placeholders below where the values go, braces included, such as `{{invoice.number}}`. Or start from **Download a template to start from**: the built-in layout in your style, every placeholder already in place.
2. Save it as a `.docx` (5 MB at most, without macros).
3. Open **Profile → Billing → Word templates** and click **Upload a template** under **Invoices and credit notes** or **Quotes**.
4. Indezy shows the placeholders it found, those it does not know (left as written in the document), and the mentions French law requires that your template lacks: those are added at its end, so nothing required goes missing.
5. Click **Preview** to download it filled with your latest invoice or quote, or with a sample one before the first.

The PDF keeps the built-in layout and its style; the Word file is a copy to read, adapt or archive. See [Quotes and invoices](/help/invoices-quotes).

## The document

`{{invoice.…}}` and `{{quote.…}}` work exactly as `{{document.…}}`: write whichever reads best in your template.

| Placeholder | What it gives | Example |
|---|---|---|
| `{{document.title}}` | The heading: Invoice, Credit note, Deposit invoice or Quote | Invoice |
| `{{document.number}}` | The number, once issued or sent | F2026-0042 |
| `{{document.issueDate}}` | The issue date | 17 November 2026 |
| `{{document.dueDate}}` | An invoice's due date | 17 December 2026 |
| `{{document.period}}` | An invoice's period | Period: 1 November 2026 to 30 November 2026 |
| `{{document.validUntil}}` | When a quote stops holding | 17 December 2026 |
| `{{document.draft}}` | What a draft says it is not, empty once issued | DRAFT — this document is not an invoice |

## You, the seller

| Placeholder | What it gives | Example |
|---|---|---|
| `{{seller.name}}` | Your name, "EI" after it for an individual business | Alice Martin EI |
| `{{seller.details}}` | Every line under your name on the PDF: address, legal form and capital, SIRET, VAT number | 18 rue du Sentier, 75002 Paris, SIRET : 552 100 554 00012 |
| `{{seller.address}}` | Your billing address | 18 rue du Sentier, 75002 Paris |
| `{{seller.siret}}` | Your SIRET | 55210055400012 |
| `{{seller.vatNumber}}` | Your VAT number, empty when VAT does not apply | FR40552100554 |
| `{{seller.iban}}` | Your IBAN, in groups of four | FR76 3000 6000 0112 3456 7890 189 |
| `{{seller.bic}}` | Your BIC | BNPAFRPP |
| `{{logo}}` | Your logo, at the size chosen in Billing; alone in its paragraph or cell | |

## The client

| Placeholder | What it gives | Example |
|---|---|---|
| `{{client.name}}` | The client's name | Banque Exemple |
| `{{client.details}}` | Its address, SIREN and VAT number, as on the PDF | 29 boulevard Haussmann, Paris |
| `{{client.address}}` | Its address | 29 boulevard Haussmann, Paris |
| `{{client.siren}}` | Its SIREN | 552120222 |
| `{{client.vatNumber}}` | Its VAT number | FR27552120222 |

## The lines

Put `{{#lines}}` at the start of a table row and `{{/lines}}` at its end: that row is repeated for each line of the document. Between the two, these placeholders give the line's values.

| Placeholder | What it gives | Example |
|---|---|---|
| `{{#lines}}` | Starts the row repeated for each line | |
| `{{line.description}}` | The line's description | Java development |
| `{{line.quantity}}` | Its quantity, days or units | 18.5 |
| `{{line.unitPrice}}` | Its unit price before VAT | €650.00 |
| `{{line.vatRate}}` | Its VAT rate | 20 % |
| `{{line.amount}}` | Its amount before VAT | €12,025.00 |
| `{{/lines}}` | Ends the repeated row | |

## Totals, mentions and notes

| Placeholder | What it gives | Example |
|---|---|---|
| `{{totals.excludingVat}}` | The total before VAT | €12,025.00 |
| `{{totals.vat}}` | The VAT, empty when VAT does not apply | €2,405.00 |
| `{{totals.toPay}}` | The amount to pay | €14,430.00 |
| `{{mentions}}` | The mentions French law requires: VAT or its exemption, the payment term and bank details, no discount, the late penalties and the €40 recovery indemnity (a quote's validity and deposit) | Payment within 30 days… |
| `{{notes}}` | The document's notes | Purchase order 4500012345 |
| `{{acceptance}}` | A quote's words above the client's signature | Approved — date, signature and stamp… |

## Show a part only in a case

Put a part between `{{#vatExempt}}` and `{{/vatExempt}}` to show it only when VAT does not apply to you, or between `{{^vatExempt}}` and `{{/vatExempt}}` to show it only when it does.

| Placeholder | What it gives | Example |
|---|---|---|
| `{{#vatExempt}}` | Starts a part shown only without VAT | `{{#vatExempt}}VAT not applicable{{/vatExempt}}` |
| `{{^vatExempt}}` | Starts a part shown only with VAT | `{{^vatExempt}}VAT {{seller.vatNumber}}{{/vatExempt}}` |
| `{{/vatExempt}}` | Ends the part | |

## How values are written

- Dates and amounts are written in the document's language: `17 novembre 2026` and `1 250,00 €` in French, `17 November 2026` and `€1,250.00` in English.
- A value the document does not have (a quote's due date, a VAT number without VAT) leaves nothing.
- A placeholder Indezy does not know stays as written, and is listed when you upload the template.
- Values on several lines, such as an address, keep their line breaks.

> **Tip:** Word splits a placeholder typed with mixed formatting (part bold, part not) into pieces. Indezy joins them back, but give a whole placeholder the same formatting: the filled value takes the formatting of its paragraph's first piece.

## A fillable PDF form instead

You can also upload a **fillable PDF form** (made in Acrobat, LibreOffice or any PDF editor) in place of a Word document. Name its fields after the placeholders, without braces: a field `number` inside a field `invoice` (written `invoice.number` by most editors), or simply `invoice_number`. A line's fields carry its row: `line_description_1` and `line_amount_1` for the first line, `_2` for the second. A check box named `vatExempt` is ticked when you charge no VAT.

**Download as Word** then gives a PDF: your form filled in and flattened. Lines beyond the form's rows, and the mentions it has no field for, go on a page added at the end. A protected PDF, one with scripts or one without fields is refused. Like the Word copy, it is a copy for you: the invoice you send stays the built-in Factur-X PDF.
