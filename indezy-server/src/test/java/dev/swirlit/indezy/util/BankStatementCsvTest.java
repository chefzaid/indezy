package dev.swirlit.indezy.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BankStatementCsvTest {

    @Test
    void readsTheCreditsOfAFrenchBanksExportPastItsPreamble() {
        String statement = """
            ﻿Compte de chèques;FR76 3000 6000 0112 3456 7890 189
            Solde au 23/11/2026;12 345,67
            Date opération;Libellé;Montant (EUR)
            20/11/2026;"VIR SEPA CONSEIL; FACT F2026-0001";"1 234,56"
            21/11/2026;PRLV FREE MOBILE;-19,99
            22/11/26;VIR RECU;+450,00 €
            pas une date;VIR;10,00
            """;
        assertThat(BankStatementCsv.credits(statement)).containsExactly(
            new BankStatementCsv.Credit(LocalDate.of(2026, 11, 20), "VIR SEPA CONSEIL; FACT F2026-0001", new BigDecimal("1234.56")),
            new BankStatementCsv.Credit(LocalDate.of(2026, 11, 22), "VIR RECU", new BigDecimal("450.00")));
    }

    @Test
    void readsACreditColumnAndAnEnglishExportWithCommas() {
        String split = "Date,Description,Debit,Credit\n2026-11-20,Card payment,12.50,\n2026-11-21,Transfer from ACME,,\"1,200.00\"\n";
        assertThat(BankStatementCsv.credits(split)).containsExactly(
            new BankStatementCsv.Credit(LocalDate.of(2026, 11, 21), "Transfer from ACME", new BigDecimal("1200.00")));
        assertThat(BankStatementCsv.credits("Date\tLabel\tAmount\n20.11.2026\tIn\t99.9\n")).singleElement()
            .extracting(BankStatementCsv.Credit::amount).isEqualTo(new BigDecimal("99.9"));
    }

    @Test
    void readsNothingWithoutAHeaderItKnows() {
        assertThat(BankStatementCsv.credits("a;b;c\n1;2;3\n")).isEmpty();
        assertThat(BankStatementCsv.credits(null)).isEmpty();
        assertThat(BankStatementCsv.amount("abc")).isNull();
        assertThat(BankStatementCsv.amount("1.234,5")).isEqualTo(new BigDecimal("1234.5"));
        assertThat(BankStatementCsv.date("31/02/2026")).isNull();
    }
}
