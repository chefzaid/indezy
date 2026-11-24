**Quotes** and **Invoices** cover a freelancer's billing: the quote for fixed-price work, the monthly invoice of a time-and-materials mission from its activity report, credit notes, payment tracking and the export for your accountant.

## Complete your billing identity

A French invoice must carry specific mentions. Fill them in once in **Profile → Billing**, on the **Billing identity** card:

1. **Legal name** and **Legal form** (micro-entreprise, EI, EURL, SASU, SARL, SAS…).
2. **SIRET**, and for a company its **Share capital** and **Registration**.
3. The **VAT number** and **VAT rate** (20 % by default), or **Exempt from VAT (franchise en base, art. 293 B du CGI)**, ticked by default.
4. The **Billing address**, **IBAN** and **BIC**.
5. The **Payment term (days)** (30 by default, 60 at most) and the **Invoice number prefix** (`F` by default, giving F2026-0001).
6. Optionally a **Logo** (PNG or JPEG, 2 MB at most), on the left, centred or on the right, small, medium or large, a **Layout** (**Simple**, **Modern** with a band of your accent colour across the top, or **Classic** with a serif font, rules and framed bank details), a **Font** (sans-serif, serif, or your own with **Upload my font**: a TrueType .ttf whose licence allows embedding, with its bold file if you have one), a **Letterhead** (a PDF of one or two A4 pages your documents are drawn over, its fonts embedded, with the **Room for its header** and **Room for its footer** in millimetres so the document never covers them), a **Line under your name** (a tagline), a **Footer** line (your website, your phone) at the foot of each page, and an **Accent colour** for the title, the table header and the rules of your invoices and quotes (a colour too pale to read or print is refused); an issued document keeps the logo, footer and colour it had. **Preview** shows every page of an invoice in these settings before you save them, and **Preview a quote** a quote's; once shown, the preview is drawn again by itself a moment after each change. Tick **Quotes have their own colour and layout** to give quotes a different layout and accent colour, the rest shared, and check it with **Preview a quote**. **Header and footer of the pages** adds a centre and a right zone to the footer and a header on the pages after the first, each a text with placeholders such as `{{logo}}` (your logo, small), `{{page}}`, `{{pages}}`, `{{document.number}}` or `{{seller.siret}}` (click one in the list under them to insert it where your cursor was); leave the right footer empty to keep the page number there. **Order of the body** sets the order of the lines and totals, the mentions, the notes and the quotes' signature box: drag each by its handle, or use the arrows beside it; untick **Printed** to leave the signature box out of your quotes (the other blocks are required, so they can be moved but not removed). The lines table can fill its header with your accent colour (**Header filled with the accent colour**) and shade every other row (**Shaded rows**), whatever the layout, and the quotes' signature box a **Height** and a **Side**. The mentions and the notes also take their own **Size**, **Alignment**, **Font** (your documents', sans-serif or serif), **Colour** (the text's, your accent colour or grey), **Bold**, a frame (**Framed**) and a grey background (**Shaded**). The **Notes a new invoice starts with** and **Notes a new quote starts with** fill the notes of each new draft.
7. The **Late-payment interest rate (per year)** your terms state, for instance 12.15 %. French law asks at least three times the legal interest rate; left empty, invoices state the ECB rate plus 10 points, without a figure.

You can write drafts at any time, but issuing an invoice needs a SIRET, an address, an IBAN and, when VAT applies, a VAT number. A malformed IBAN, SIRET, VAT number or BIC is refused.

> **Tip:** under portage salarial, the portage company invoices your clients: Indezy does not issue invoices for you.

## The invoices list

**Invoices** lists your invoices with their status and totals what is **Awaiting payment** and **Overdue**. The **Show** filter offers **All**, **Drafts**, **Awaiting payment**, **Overdue** or **Paid**.

![The Invoices page with the awaiting and overdue totals, and the list of invoices](/assets/help/screens/invoices-en.jpg)

An invoice goes through these statuses: **Draft**, **Issued**, **Overdue** once past its due date, **Paid**, or **Credited**.

## Invoice an activity report

1. On the **Invoices** page, click **From an activity report** (or **Create the invoice** on the mission's activity report card).
2. Pick the month among those with days worked and not invoiced yet.
3. Click **Create the invoice**.

The draft is addressed to the mission's ESN, else its client, in the language of its activity report template, with the days at the mission's daily rate. A rejected month cannot be invoiced, and a month is invoiced only once. See [Timesheets and purchase orders](/help/activity-reports).

## Create an invoice by hand

1. Click **New invoice**.
2. Pick the **Client**, optionally the **Mission**, the period (**Service from**, **Service to**) and the **Language**.
3. For each line, enter the **Description**, **Quantity**, **Unit price** and **VAT**. Click **Add a line** to add more (50 at most).
4. Optionally add **Notes (printed on the invoice)**, then save.

A line with a negative quantity takes an amount off (a discount, a deposit already invoiced). An invoice totalling less than zero cannot be issued: that is what a credit note is for.

## Issue, get paid, credit

1. Review the draft: its PDF says it is not an invoice.
2. Open the invoice's menu, click **Issue**, then pick the **Issue date** (never later than today, never before the last invoice).
3. The invoice gets the year's next number, with no gap, and its due date. It can no longer change or be deleted.
4. When the client pays, click **Mark as paid** and enter **Paid on**. **Mark as unpaid** fixes a mistake.
5. Or click **Match my bank statement** and choose the CSV your bank exports: each credit is matched with the invoice it pays, by its number in the label, by the client's name with the same amount, or by the amount alone when only one invoice awaits it. Untick any match that is wrong, then click **Mark N invoices paid**: each is marked paid on its credit's day. Nothing is recorded before that, and the statement is not kept.

To cancel all or part of an issued invoice, click **Create a credit note**: Indezy drafts one with the same lines, to reduce for a partial credit, then to issue. Fully credited, the invoice reads **Credited**.

## Invoicing in another currency

For an international client, choose **Currency** in the draft: euro, US dollar, pound sterling or Swiss franc. In another currency than the euro, type **Value of 1 USD in euros** (or GBP, CHF) at the issue date, as your bank or the ECB gives it: the invoice cannot be issued without it.

- The lines and totals are in that currency, with the total in euros below.
- The PDF states the rate and the amounts in euros, VAT included, as French law asks.
- The totals awaiting payment, overdue and by client are counted in euros, and so is the accounting export, with each amount in the currency beside it.
- A credit note keeps its invoice's currency and rate.

## Late payments

- An issued invoice becomes **Overdue** after its due date. It shows under **Overdue invoices** on the [dashboard](/help/dashboard) and in the weekly digest.
- The PDF carries the late payment penalties French law asks for, at your rate when you set one.
- In the list, an overdue invoice says what it is owed on top of its amount: the interest at the rate it states, per day late, and the €40 recovery indemnity, for instance "12 day(s) late: interest 2.00 € and recovery indemnity 40.00 €".
- Each client's page, and its opportunities' pages, says how it pays from its paid invoices, for example "Pays in 52 days on average, 12 days late".
- **Ceilings** compares the revenue you received this year (excluding VAT, on the day each invoice was paid) with the micro-entreprise ceiling (83,600 € in 2026, for services) and the VAT franchise thresholds (37,500 €, and 41,250 € within the year). It warns from 80 % and says on which day the regime or the franchise ends once you pass them. It shows for a micro-entreprise, and while you are exempt from VAT.
- **URSSAF declarations** estimates your contributions on the revenue received. For a micro-entreprise, it lists each month or quarter with its deadline, the one to declare now first, at the official rate of your activity. For an EI under the real regime (SSI), it computes the year's contributions on your profit so far (revenue less your deductible expenses), line by line at the 2026 official rates; as an EURL's majority manager, give your pay and dividends of the year in Profile → Billing for the same; as a SASU's president, give your gross pay and the company's accident-at-work rate to see the payslip's contributions, the company's share and yours, and your net pay. Set your activity, monthly or quarterly declarations, the versement libératoire or your own rate in Profile → Billing → **URSSAF contributions**.
- **Tax calendar** lists the CFE (15 December), your income-tax instalments (the 15th, monthly or quarterly as set in Profile → Billing, unless you pay the versement libératoire) and, if you charge VAT, its July and December instalments and the annual return in May, with the VAT estimated from your payments received. Under the normal VAT regime (**VAT regime** in Profile → Billing), it lists instead your monthly or quarterly VAT return (CA3) on the day the tax administration sets for your place, legal form and name or SIREN, taken from your billing details.
- **Revenue by client, last 12 months** splits your revenue and warns when one client makes up half of it or more, a risk of your contract being reclassified as employment.

## Factur-X

Issued invoices and credit notes download with **Download PDF** as Factur-X: a PDF/A-3 carrying the same data as embedded XML (EN 16931 profile). Drafts stay plain PDFs. Sending through an approved e-invoicing platform is not available yet.

**Download as Word**, in an invoice's or a quote's menu, gives the same document as a Word file (.docx) with your style, to read, adapt or archive. The invoice you send stays the PDF: it is the one with its frozen number and contents.

**Word templates**, in Profile → Billing, let **Download as Word** fill your own layout:

1. In Word, lay out an invoice (or a quote) as you like and type placeholders where the values go, such as `{{invoice.number}}`, `{{client.name}}` or `{{totals.toPay}}`; **Placeholders to copy** lists them all.
2. For the lines, put `{{#lines}}` at the start of a table row and `{{/lines}}` at its end, with `{{line.description}}`, `{{line.quantity}}`, `{{line.unitPrice}}` and `{{line.amount}}` in its cells: the row is repeated for each line.
3. Click **Upload a template** under **Invoices and credit notes** or **Quotes**. Indezy shows the placeholders it found, those it does not know, and the mentions your template lacks: those are added at its end, so nothing French law requires goes missing.

Every placeholder, with an example, is in [Document templates](/help/document-templates). **Preview** downloads your template filled with your latest invoice or quote (a sample one before the first). **Back to the built-in layout** removes the template. The PDF keeps the built-in layout.

## The export for your accountant

1. On the **Invoices** page, click **Export for the accountant**.
2. Pick the year.
3. Choose **Journal entries (FEC)**, the file French accounting tools import (sales journal `VE`, clients on `411000`, services on `706000`, VAT on `445710`, payments in the bank journal on `512000`), or **Invoice register (spreadsheet)**.

The export holds the year's issued invoices and credit notes.

## Quotes

**Quotes** prepares fixed-price work: an audit, a training, a development.

1. Click **New quote**.
2. Pick the client, optionally the mission, the language, how long it is **Valid for** (30 days by default, 365 at most), an optional **Deposit on order** (1 to 100 %), notes and the lines.
3. Click **Send** and pick the **Quote date**. The quote gets its own number (D2026-0001) and is frozen. Then send its PDF to the client: Indezy does not send it itself.
4. Depending on the answer, click **Accepted by the client** or **Refused by the client**. Without an answer, it reads **Expired** once its validity ends.

The PDF states the VAT or its exemption, the validity, the deposit, the payment term, the late payment penalties and a "Bon pour accord" box for the client's signature. Your **Terms and conditions of sale**, written in **Profile → Billing** or added there as a PDF, follow on their own pages; a sent quote keeps those it was sent with.

For an accepted quote:

- **Invoice the deposit** drafts a deposit invoice for the agreed share;
- **Invoice the balance** drafts, once the deposit invoice is issued, the final invoice with the quote's lines, less the deposits already invoiced.

The page totals the sent quotes awaiting an answer and the share of answered quotes accepted.

## Expenses

**Expenses**, in the menu, records your business expenses: the date, what for, a category, the amount VAT included and the VAT it includes, optionally a mission. The page totals the year by month and by category, and says what your expenses change under your status, set in **Profile → Billing**:

- in a **micro-entreprise**, nothing: the flat allowance on your turnover already counts them, so they cost their full amount;
- in an **EI au réel** or a **company** (EURL, SASU, SARL, SAS), they lower your taxable profit (less the VAT you recover if you charge VAT), so they cost you only the share you would have kept, at your reversion rate;
- in **portage**, the portage company reimburses them out of your turnover before charges, which costs you the same share.
