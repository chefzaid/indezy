package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferRelationDto;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OfferRelationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    private static Client company(String name) {
        Client client = new Client();
        client.setCompanyName(name);
        return client;
    }

    private static Project mission(ProjectStatus status, Client client, Client esn, Integer clauseMonths, LocalDate start, Integer months) {
        Project project = new Project();
        project.setStatus(status);
        project.setClient(client);
        project.setMiddleman(esn);
        project.setNonSolicitationMonths(clauseMonths);
        project.setStartDate(start);
        project.setDurationInMonths(months);
        return project;
    }

    @Test
    void marksThePublishersWorkedWithAndTheEndClientsAClauseCovers() {
        Map<String, OfferRelationDto> relations = OfferRelationService.of(List.of(
            // Through Conseil X for Airbus, 12 months of clause after its end on 2026-08-31: until 2027-08-31.
            mission(ProjectStatus.WON, company("Airbus"), company("Conseil X"), 12, LocalDate.of(2026, 3, 1), 6),
            // An older clause, run out.
            mission(ProjectStatus.WON, company("Thales"), company("Alten"), 3, LocalDate.of(2025, 1, 1), 3),
            // Only an opportunity: not worked with.
            mission(ProjectStatus.INTERVIEW, company("Safran"), company("Capgemini"), 12, null, null)), TODAY);

        assertThat(relations.get("conseil x")).isEqualTo(new OfferRelationDto(true, null, null));
        assertThat(relations.get("airbus")).isEqualTo(new OfferRelationDto(true, "Conseil X", LocalDate.of(2027, 8, 31)));
        assertThat(relations.get("thales")).isEqualTo(new OfferRelationDto(true, null, null));
        assertThat(relations).doesNotContainKeys("safran", "capgemini");
    }

    @Test
    void keepsTheClauseRunningLongestAnOpenEndedOneAbove() {
        Map<String, OfferRelationDto> relations = OfferRelationService.of(List.of(
            mission(ProjectStatus.WON, company("Airbus"), company("Conseil X"), 12, LocalDate.of(2026, 3, 1), 6),
            mission(ProjectStatus.WON, company("Airbus"), company("Conseil Y"), 6, LocalDate.of(2026, 10, 1), null)), TODAY);
        assertThat(relations.get("airbus")).isEqualTo(new OfferRelationDto(true, "Conseil Y", null));
    }

    @Test
    void marksThePublishersThatNeverAnswered() {
        Map<String, OfferRelationDto> relations = new java.util.HashMap<>(Map.of("conseil x", new OfferRelationDto(true, null, null)));
        OfferRelationService.withUnanswered(relations, Map.of("Conseil X", 4, "Conseil Muet", 3));
        assertThat(relations.get("conseil x")).isEqualTo(new OfferRelationDto(true, null, null, 4));
        assertThat(relations.get("conseil muet")).isEqualTo(new OfferRelationDto(false, null, null, 3));
    }

    @Test
    void guessesTheEndClientAnOfferNames() {
        dev.swirlit.indezy.model.JobOffer offer = new dev.swirlit.indezy.model.JobOffer();
        offer.setTitle("Lead Java H/F");
        offer.setCompany("Conseil X");
        offer.setDescription("Pour notre client la Société Générale, au sein de la DSI, mission Kafka.");
        List<String> endClients = List.of("Société Générale", "Airbus", "SG");

        assertThat(OfferRelationService.guessEndClient(offer, endClients)).isEqualTo("Société Générale");
        // A name inside another word is no mention; the publisher itself is not its own end client.
        offer.setDescription("Mission chez Airbusiness, en direct avec Conseil X.");
        assertThat(OfferRelationService.guessEndClient(offer, List.of("Airbus", "Conseil X"))).isNull();
        assertThat(OfferRelationService.guessEndClient(offer, List.of())).isNull();
    }

    @Test
    void addsTheGuessToWhatElseRelatesThePublisher() {
        dev.swirlit.indezy.model.JobOffer offer = new dev.swirlit.indezy.model.JobOffer();
        offer.setCompany("Conseil X");
        offer.setDescription("Client final : Airbus.");
        assertThat(OfferSearchService.relation(Map.of(), offer, List.of("Airbus"))).isEqualTo(new OfferRelationDto(false, null, null, null, "Airbus"));
        assertThat(OfferSearchService.relation(Map.of("conseil x", new OfferRelationDto(true, null, null)), offer, List.of("Airbus")))
            .isEqualTo(new OfferRelationDto(true, null, null, null, "Airbus"));
        assertThat(OfferSearchService.relation(Map.of(), offer, List.of())).isNull();
    }
}
