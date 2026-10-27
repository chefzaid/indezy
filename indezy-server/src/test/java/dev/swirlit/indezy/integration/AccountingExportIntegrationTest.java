package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice hands her accountant a year of invoices, over HTTP: the FEC journal entries of an invoice, its credit note and
 * its payment, then the register as a spreadsheet in French and English, with none of Bob's and no draft; a formula-like
 * client name stays text in the spreadsheets.
 */
class AccountingExportIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void exportsTheJournalAndTheRegisterOfAYear() {
        call(alice, HttpMethod.PUT, "/users/billing", Map.of("legalName", "Alice Martin", "legalForm", "EI", "siret", "55210055400012",
            "vatExempt", false, "vatNumber", "FR40552100554", "address", "Paris", "iban", "FR7630006000011234567890189"), Map.class);
        Map<String, Object> client = call(alice, HttpMethod.GET, "/clients/" + aliceClientId, null, Map.class).getBody();
        client.put("companyName", "=HYPERLINK(\"https://evil.example\")");
        client.put("address", "1 place de la Bourse");
        call(alice, HttpMethod.PUT, "/clients/" + aliceClientId, client, Map.class);
        LocalDate today = LocalDate.now();
        int year = today.getYear();

        long invoice = idOf(call(alice, HttpMethod.POST, "/invoices", Map.of("clientId", aliceClientId, "lines",
            List.of(Map.of("description", "Audit", "quantity", 2, "unitPrice", 600, "vatRate", 20))), Map.class));
        String number = (String) call(alice, HttpMethod.POST, "/invoices/" + invoice + "/issue", null, Map.class).getBody().get("number");
        long note = idOf(call(alice, HttpMethod.POST, "/invoices/" + invoice + "/credit-note", null, Map.class));
        call(alice, HttpMethod.PUT, "/invoices/" + note, Map.of("clientId", aliceClientId, "lines",
            List.of(Map.of("description", "Remise", "quantity", 1, "unitPrice", 100, "vatRate", 20))), Map.class);
        String noteNumber = (String) call(alice, HttpMethod.POST, "/invoices/" + note + "/issue", null, Map.class).getBody().get("number");
        call(alice, HttpMethod.PUT, "/invoices/" + invoice + "/payment", Map.of("paidOn", today.toString()), Map.class);
        call(alice, HttpMethod.POST, "/invoices", Map.of("clientId", aliceClientId, "lines",
            List.of(Map.of("description", "Not issued", "quantity", 1, "unitPrice", 999, "vatRate", 20))), Map.class);

        // The journal: the invoice and its credit note in sales, then what was paid, net of the credit, in the bank.
        ResponseEntity<byte[]> fec = call(alice, HttpMethod.GET, "/invoices/export/" + year, null, byte[].class);
        assertThat(fec.getHeaders().getContentDisposition().getFilename()).isEqualTo("552100554FEC" + year + "1231.txt");
        assertThat(fec.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        List<String> lines = new String(fec.getBody(), StandardCharsets.UTF_8).lines().toList();
        String date = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        String clientAccount = String.format("C%06d", aliceClientId);
        String clientName = "=HYPERLINK(\"https://evil.example\")";
        assertThat(lines.get(0)).startsWith("JournalCode\tJournalLib\tEcritureNum\tEcritureDate\tCompteNum").endsWith("Montantdevise\tIdevise");
        assertThat(lines.subList(1, lines.size())).containsExactly(
            String.join("\t", "VE", "Ventes", "1", date, "411000", "Clients", clientAccount, clientName, number, date,
                "Facture " + number + " " + clientName, "1440,00", "0,00", "", "", date, "", ""),
            String.join("\t", "VE", "Ventes", "1", date, "706000", "Prestations de services", "", "", number, date,
                "Facture " + number + " " + clientName, "0,00", "1200,00", "", "", date, "", ""),
            String.join("\t", "VE", "Ventes", "1", date, "445710", "TVA collectée", "", "", number, date,
                "Facture " + number + " " + clientName, "0,00", "240,00", "", "", date, "", ""),
            String.join("\t", "VE", "Ventes", "2", date, "411000", "Clients", clientAccount, clientName, noteNumber, date,
                "Avoir " + noteNumber + " " + clientName, "0,00", "120,00", "", "", date, "", ""),
            String.join("\t", "VE", "Ventes", "2", date, "706000", "Prestations de services", "", "", noteNumber, date,
                "Avoir " + noteNumber + " " + clientName, "100,00", "0,00", "", "", date, "", ""),
            String.join("\t", "VE", "Ventes", "2", date, "445710", "TVA collectée", "", "", noteNumber, date,
                "Avoir " + noteNumber + " " + clientName, "20,00", "0,00", "", "", date, "", ""),
            String.join("\t", "BQ", "Banque", "3", date, "512000", "Banque", "", "", number, date,
                "Règlement " + number + " " + clientName, "1320,00", "0,00", "", "", date, "", ""),
            String.join("\t", "BQ", "Banque", "3", date, "411000", "Clients", clientAccount, clientName, number, date,
                "Règlement " + number + " " + clientName, "0,00", "1320,00", "", "", date, "", ""));

        // The register, in French for a French spreadsheet, the client's name kept as text.
        ResponseEntity<byte[]> french = call(alice, HttpMethod.GET, "/invoices/export/" + year + "?format=REGISTER", null, byte[].class);
        assertThat(french.getHeaders().getContentType().toString()).startsWith("text/csv");
        assertThat(french.getHeaders().getContentDisposition().getFilename()).isEqualTo("factures-" + year + ".csv");
        String frenchDate = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertThat(new String(french.getBody(), StandardCharsets.UTF_8).lines().toList()).containsExactly(
            "﻿Numéro;Type;Date;Client;SIREN;N° TVA;Mission;Total HT;TVA;Total TTC;Échéance;Payée le;Statut;Facture créditée",
            number + ";Facture;" + frenchDate + ";\"'=HYPERLINK(\"\"https://evil.example\"\")\";;;;1200,00;240,00;1440,00;" +
                today.plusDays(30).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ";" + frenchDate + ";Payée;",
            noteNumber + ";Avoir;" + frenchDate + ";\"'=HYPERLINK(\"\"https://evil.example\"\")\";;;;-100,00;-20,00;-120,00;;;;" + number,
            "Total;;;;;;;1100,00;220,00;1320,00;;;;");
        ResponseEntity<byte[]> english = call(alice, HttpMethod.GET, "/invoices/export/" + year + "?format=REGISTER&language=EN", null,
            byte[].class);
        assertThat(english.getHeaders().getContentDisposition().getFilename()).isEqualTo("invoices-" + year + ".csv");
        assertThat(new String(english.getBody(), StandardCharsets.UTF_8)).contains("Number,Type,Date,Client,SIREN,VAT number",
            ",Invoice," + today + ",", ",-100.00,-20.00,-120.00,", "Total,,,,,,,1100.00,220.00,1320.00,,,,");

        // The projects summary keeps the client's name as text too, and is not cached either.
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Audit", "dailyRate", 600, "status", "WON", "clientId", aliceClientId,
            "startDate", today.toString()), Map.class);
        ResponseEntity<String> summary = call(alice, HttpMethod.GET, "/projects/export/csv/" + alice.freelanceId(), null, String.class);
        assertThat(summary.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(summary.getBody()).contains("Audit,WON,\"'=HYPERLINK(\"\"https://evil.example\"\")\",");

        // Another year holds nothing; Bob's export holds none of Alice's; a year or format out of range is refused.
        assertThat(new String(call(alice, HttpMethod.GET, "/invoices/export/" + (year - 1), null, byte[].class).getBody(),
            StandardCharsets.UTF_8).lines()).hasSize(1);
        assertThat(new String(call(bob, HttpMethod.GET, "/invoices/export/" + year + "?format=REGISTER", null, byte[].class).getBody(),
            StandardCharsets.UTF_8).lines()).hasSize(2);
        assertThat(call(alice, HttpMethod.GET, "/invoices/export/1999", null, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.GET, "/invoices/export/" + year + "?format=PDF", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
