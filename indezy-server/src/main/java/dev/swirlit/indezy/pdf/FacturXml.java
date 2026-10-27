package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.service.InvoiceTotals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Cross Industry Invoice (UN/CEFACT D16B) an issued invoice or credit note carries in its Factur-X PDF, at the
 * EN 16931 profile (see docs/features.md#factur-x): the seller and the buyer as frozen at issue, the lines, the VAT
 * breakdown per rate (exempt as {@code E} with the franchise reason), the totals, the due date and the IBAN. Amounts
 * are the ones the PDF prints, computed by {@link InvoiceTotals}; a credit note's are positive with type code 381.
 */
final class FacturXml {

    static final String FILE_NAME = "factur-x.xml";
    static final String PROFILE = "urn:cen.eu:en16931:2017";
    static final String FRANCHISE_CODE = "VATEX-FR-FRANCHISE";

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern POSTCODE_CITY = Pattern.compile("^(\\d{5})\\s+(.+)$");

    private final StringBuilder xml = new StringBuilder();

    private FacturXml() {
    }

    static byte[] of(Invoice invoice, InvoiceSeller seller, InvoiceBuyer buyer) {
        return new FacturXml().write(invoice, seller, buyer).xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    private FacturXml write(Invoice invoice, InvoiceSeller seller, InvoiceBuyer buyer) {
        boolean exempt = Boolean.TRUE.equals(seller.getVatExempt());
        boolean english = invoice.getLanguage() == DocumentLanguage.EN;
        InvoiceTotals totals = InvoiceTotals.of(invoice.getLines());
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            .append("<rsm:CrossIndustryInvoice xmlns:rsm=\"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100\"")
            .append(" xmlns:qdt=\"urn:un:unece:uncefact:data:standard:QualifiedDataType:100\"")
            .append(" xmlns:ram=\"urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100\"")
            .append(" xmlns:udt=\"urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100\">\n");
        open("rsm:ExchangedDocumentContext").open("ram:GuidelineSpecifiedDocumentContextParameter").text("ram:ID", PROFILE)
            .close("ram:GuidelineSpecifiedDocumentContextParameter").close("rsm:ExchangedDocumentContext");

        open("rsm:ExchangedDocument").text("ram:ID", invoice.getNumber()).text("ram:TypeCode", invoice.isCreditNote() ? "381" : "380");
        date("ram:IssueDateTime", "udt", invoice.getIssueDate());
        if (invoice.getNotes() != null) {
            open("ram:IncludedNote").text("ram:Content", invoice.getNotes()).close("ram:IncludedNote");
        }
        close("rsm:ExchangedDocument");

        open("rsm:SupplyChainTradeTransaction");
        int number = 0;
        for (InvoiceLine line : invoice.getLines()) {
            line(++number, line, exempt);
        }
        open("ram:ApplicableHeaderTradeAgreement");
        // A seller under the VAT franchise has no VAT number: its SIREN is its tax registration (BT-32, rule BR-E-02).
        String taxRegistration = exempt && seller.getVatNumber() == null && seller.getSiret() != null && seller.getSiret().length() >= 9 ?
            seller.getSiret().substring(0, 9) : null;
        party("ram:SellerTradeParty", seller.getName(), seller.getSiret(), seller.getAddress(), seller.getVatNumber(), taxRegistration);
        party("ram:BuyerTradeParty", buyer.getName(), buyer.getSiren(), buyer.getAddress(), buyer.getVatNumber(), null);
        close("ram:ApplicableHeaderTradeAgreement");
        xml.append("<ram:ApplicableHeaderTradeDelivery/>\n");

        open("ram:ApplicableHeaderTradeSettlement").text("ram:InvoiceCurrencyCode", "EUR");
        if (seller.getIban() != null && !invoice.isCreditNote()) {
            // 58: SEPA credit transfer.
            open("ram:SpecifiedTradeSettlementPaymentMeans").text("ram:TypeCode", "58")
                .open("ram:PayeePartyCreditorFinancialAccount").text("ram:IBANID", seller.getIban()).close("ram:PayeePartyCreditorFinancialAccount");
            if (seller.getBic() != null) {
                open("ram:PayeeSpecifiedCreditorFinancialInstitution").text("ram:BICID", seller.getBic())
                    .close("ram:PayeeSpecifiedCreditorFinancialInstitution");
            }
            close("ram:SpecifiedTradeSettlementPaymentMeans");
        }
        for (Map.Entry<BigDecimal, BigDecimal[]> rate : byRate(invoice.getLines()).entrySet()) {
            tax(rate.getKey(), rate.getValue()[0], rate.getValue()[1], exempt);
        }
        if (invoice.getPeriodStart() != null && invoice.getPeriodEnd() != null) {
            open("ram:BillingSpecifiedPeriod");
            date("ram:StartDateTime", "udt", invoice.getPeriodStart());
            date("ram:EndDateTime", "udt", invoice.getPeriodEnd());
            close("ram:BillingSpecifiedPeriod");
        }
        open("ram:SpecifiedTradePaymentTerms").text("ram:Description", terms(invoice, english));
        if (invoice.getDueDate() != null) {
            date("ram:DueDateDateTime", "udt", invoice.getDueDate());
        }
        close("ram:SpecifiedTradePaymentTerms");
        open("ram:SpecifiedTradeSettlementHeaderMonetarySummation")
            .text("ram:LineTotalAmount", amount(totals.excludingVat()))
            .text("ram:TaxBasisTotalAmount", amount(totals.excludingVat()));
        xml.append("<ram:TaxTotalAmount currencyID=\"EUR\">").append(amount(totals.vat())).append("</ram:TaxTotalAmount>\n");
        text("ram:GrandTotalAmount", amount(totals.total())).text("ram:DuePayableAmount", amount(totals.total()))
            .close("ram:SpecifiedTradeSettlementHeaderMonetarySummation");
        if (invoice.isCreditNote() && invoice.getCreditedNumber() != null) {
            open("ram:InvoiceReferencedDocument").text("ram:IssuerAssignedID", invoice.getCreditedNumber());
            if (invoice.getCreditedIssueDate() != null) {
                date("ram:FormattedIssueDateTime", "qdt", invoice.getCreditedIssueDate());
            }
            close("ram:InvoiceReferencedDocument");
        }
        close("ram:ApplicableHeaderTradeSettlement").close("rsm:SupplyChainTradeTransaction");
        xml.append("</rsm:CrossIndustryInvoice>\n");
        return this;
    }

    private void line(int number, InvoiceLine line, boolean exempt) {
        open("ram:IncludedSupplyChainTradeLineItem")
            .open("ram:AssociatedDocumentLineDocument").text("ram:LineID", String.valueOf(number)).close("ram:AssociatedDocumentLineDocument")
            .open("ram:SpecifiedTradeProduct").text("ram:Name", line.getDescription()).close("ram:SpecifiedTradeProduct")
            .open("ram:SpecifiedLineTradeAgreement").open("ram:NetPriceProductTradePrice")
            .text("ram:ChargeAmount", amount(line.getUnitPrice())).close("ram:NetPriceProductTradePrice").close("ram:SpecifiedLineTradeAgreement")
            .open("ram:SpecifiedLineTradeDelivery");
        // DAY (UN/ECE recommendation 20): the quantity billed is days of work.
        xml.append("<ram:BilledQuantity unitCode=\"DAY\">").append(line.getQuantity().stripTrailingZeros().toPlainString())
            .append("</ram:BilledQuantity>\n");
        close("ram:SpecifiedLineTradeDelivery").open("ram:SpecifiedLineTradeSettlement").open("ram:ApplicableTradeTax")
            .text("ram:TypeCode", "VAT").text("ram:CategoryCode", category(line.getVatRate(), exempt))
            .text("ram:RateApplicablePercent", amount(line.getVatRate())).close("ram:ApplicableTradeTax")
            .open("ram:SpecifiedTradeSettlementLineMonetarySummation").text("ram:LineTotalAmount", amount(InvoiceTotals.lineAmount(line)))
            .close("ram:SpecifiedTradeSettlementLineMonetarySummation").close("ram:SpecifiedLineTradeSettlement")
            .close("ram:IncludedSupplyChainTradeLineItem");
    }

    /** A party: its name, legal identifier (SIRET or SIREN, scheme 0002), address in France, VAT number or tax registration. */
    private void party(String element, String name, String legalId, String address, String vatNumber, String taxRegistration) {
        open(element).text("ram:Name", name == null ? "" : name);
        if (legalId != null) {
            open("ram:SpecifiedLegalOrganization");
            xml.append("<ram:ID schemeID=\"0002\">").append(escape(legalId)).append("</ram:ID>\n");
            close("ram:SpecifiedLegalOrganization");
        }
        address(address);
        if (vatNumber != null) {
            open("ram:SpecifiedTaxRegistration");
            xml.append("<ram:ID schemeID=\"VA\">").append(escape(vatNumber)).append("</ram:ID>\n");
            close("ram:SpecifiedTaxRegistration");
        }
        if (taxRegistration != null) {
            open("ram:SpecifiedTaxRegistration");
            xml.append("<ram:ID schemeID=\"FC\">").append(escape(taxRegistration)).append("</ram:ID>\n");
            close("ram:SpecifiedTaxRegistration");
        }
        close(element);
    }

    /** The typed address split as well as it can be: a last line "75002 Paris" gives the postcode and the city. */
    private void address(String address) {
        List<String> lines = address == null ? List.of() : Arrays.stream(address.split("\\R")).map(String::strip)
            .filter(line -> !line.isEmpty()).toList();
        open("ram:PostalTradeAddress");
        String city = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
        Matcher postcodeCity = POSTCODE_CITY.matcher(city);
        if (postcodeCity.matches()) {
            text("ram:PostcodeCode", postcodeCity.group(1));
            city = postcodeCity.group(2);
        }
        if (lines.size() > 1) {
            text("ram:LineOne", String.join(", ", lines.subList(0, lines.size() - 1)));
        }
        text("ram:CityName", city).text("ram:CountryID", "FR").close("ram:PostalTradeAddress");
    }

    /** One rate of the VAT breakdown: its basis and amount; a zero rate is exempt, with the franchise reason when it applies. */
    private void tax(BigDecimal rate, BigDecimal basis, BigDecimal vat, boolean exempt) {
        open("ram:ApplicableTradeTax").text("ram:CalculatedAmount", amount(vat)).text("ram:TypeCode", "VAT");
        boolean zero = rate.signum() == 0;
        if (zero) {
            text("ram:ExemptionReason", exempt ? "TVA non applicable, art. 293 B du CGI" : "Exonération de TVA");
        }
        text("ram:BasisAmount", amount(basis)).text("ram:CategoryCode", category(rate, exempt));
        if (zero && exempt) {
            text("ram:ExemptionReasonCode", FRANCHISE_CODE);
        }
        text("ram:RateApplicablePercent", amount(rate)).close("ram:ApplicableTradeTax");
    }

    /** Per rate, its lines' amounts and their VAT, each line's VAT rounded as the totals round it. */
    private static Map<BigDecimal, BigDecimal[]> byRate(List<InvoiceLine> lines) {
        Map<BigDecimal, BigDecimal[]> rates = new TreeMap<>();
        for (InvoiceLine line : lines) {
            BigDecimal amount = InvoiceTotals.lineAmount(line);
            BigDecimal vat = InvoiceTotals.of(List.of(line)).vat();
            rates.merge(line.getVatRate().setScale(2, RoundingMode.HALF_UP), new BigDecimal[] {amount, vat},
                (a, b) -> new BigDecimal[] {a[0].add(b[0]), a[1].add(b[1])});
        }
        return rates;
    }

    private static String category(BigDecimal rate, boolean exempt) {
        return rate.signum() == 0 || exempt ? "E" : "S";
    }

    private static String terms(Invoice invoice, boolean english) {
        if (invoice.isCreditNote()) {
            return english ? "Credit note: deducted from what is owed, or refunded" : "Avoir : à déduire de ce qui est dû, ou remboursé";
        }
        return english ? "Payment by bank transfer by the due date" : "Paiement par virement à l'échéance";
    }

    private FacturXml date(String element, String prefix, LocalDate date) {
        open(element);
        xml.append('<').append(prefix).append(":DateTimeString format=\"102\">").append(date.format(DATE)).append("</").append(prefix)
            .append(":DateTimeString>\n");
        return close(element);
    }

    private FacturXml open(String element) {
        xml.append('<').append(element).append(">\n");
        return this;
    }

    private FacturXml close(String element) {
        xml.append("</").append(element).append(">\n");
        return this;
    }

    private FacturXml text(String element, String value) {
        xml.append('<').append(element).append('>').append(escape(value)).append("</").append(element).append(">\n");
        return this;
    }

    private static String amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** XML text: the five reserved characters escaped, and the control characters XML 1.0 forbids dropped. */
    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> {
                    if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
