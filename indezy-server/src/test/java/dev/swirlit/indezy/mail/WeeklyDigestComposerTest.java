package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyDigestComposerTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 10, 26);

    private final WeeklyDigestComposer composer = new WeeklyDigestComposer(
        new MailProperties("re_test", "Indezy <noreply@swirlit.dev>", "https://indezy.example/", "https://api.resend.test"));

    private static DashboardStatsDto stats() {
        return DashboardStatsDto.builder()
            .projectsByStatus(Map.of("CONTACT", 2L, "INTERVIEW", 1L, "OFFER", 0L, "WON", 4L))
            .upcomingInterviews(List.of(DashboardStatsDto.UpcomingInterview.builder().title("Entretien <technique>").role("Lead Java")
                .clientName("Acme").date(LocalDateTime.of(2026, 10, 29, 14, 0)).build()))
            .followUpsDue(List.of(DashboardStatsDto.FollowUpDue.builder().name("Léa Martin").clientName("Alten").daysWaiting(6).build()))
            .staleOpportunities(List.of(DashboardStatsDto.StaleOpportunity.builder().role("Architecte").daysSinceActivity(21).build()))
            .missionsEndingSoon(List.of(DashboardStatsDto.MissionEndingSoon.builder().role("Dev Kotlin").clientName("Thales")
                .endDate(LocalDate.of(2026, 11, 30)).build()))
            .overdueInvoices(List.of(DashboardStatsDto.OverdueInvoice.builder().number("F2026-0003").clientName("Alten")
                .total(new BigDecimal("4200.00")).daysLate(12).build()))
            .build();
    }

    private static DashboardStatsDto quiet() {
        return DashboardStatsDto.builder().projectsByStatus(Map.of("WON", 3L)).upcomingInterviews(List.of()).followUpsDue(List.of())
            .staleOpportunities(List.of()).missionsEndingSoon(List.of()).build();
    }

    @Test
    void writesTheWeekInFrenchWithEverySectionEscaped() {
        OfferDto offer = mock(OfferDto.class);
        when(offer.id()).thenReturn(42L);
        when(offer.title()).thenReturn("Lead Java");
        Email email = composer.compose(new WeeklyDigestComposer.Digest(7L, "Zaid", "zaid@example.com", DocumentLanguage.FR, "tok", WEEK,
            WeeklyDigestComposer.Week.of(stats()), List.of(offer), 8));

        assertThat(email.subject()).isEqualTo("Votre semaine Indezy du 26 octobre 2026");
        assertThat(email.text()).contains("Bonjour Zaid, voici votre semaine", "3 opportunités en cours : 2 premier contact, 1 entretiens, 0 offres",
            "jeu. 29 oct., 14h00 · Entretien <technique> · Lead Java (Acme)", "Léa Martin (Alten), sans réponse depuis 6 jours",
            "Architecte, rien depuis 21 jours", "Factures en retard de paiement",
            "F2026-0003, Alten, 4\u202F200,00\u00A0€, en retard de 12 jours", "Dev Kotlin (Thales), fin le 30 novembre 2026", "8 nouvelles offres correspondent",
            "https://indezy.example/offers/42", "Et 7 de plus");
        assertThat(email.html()).contains("Entretien &lt;technique&gt;").doesNotContain("<technique>");
        assertThat(email.headers()).containsEntry("List-Unsubscribe", "<https://indezy.example/api/public/weekly-digest/unsubscribe/tok>")
            .containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        assertThat(email.idempotencyKey()).isEqualTo("weekly-digest/7/2026-10-26");
        assertThat(email.toString()).doesNotContain("zaid@example.com");
    }

    @Test
    void leavesOutWhatHasNothingToSay() {
        DashboardStatsDto stats = stats();
        stats.setUpcomingInterviews(List.of());
        stats.setFollowUpsDue(List.of());
        Email email = composer.compose(new WeeklyDigestComposer.Digest(7L, "Zaid", "zaid@example.com", DocumentLanguage.EN, "tok", WEEK,
            WeeklyDigestComposer.Week.of(stats), List.of(), 0));

        assertThat(email.subject()).isEqualTo("Your Indezy week of 26 October 2026");
        assertThat(email.text()).contains("3 open opportunities", "Opportunities gone quiet").doesNotContain("Interviews", "Follow-ups",
            "match your profile");
    }

    @Test
    void tellsAnEmptyWeek() {
        assertThat(new WeeklyDigestComposer.Digest(7L, "Zaid", "z@example.com", DocumentLanguage.EN, "tok", WEEK, WeeklyDigestComposer.Week.of(quiet()), List.of(), 0)
            .isEmpty()).isTrue();
        assertThat(new WeeklyDigestComposer.Digest(7L, "Zaid", "z@example.com", DocumentLanguage.EN, "tok", WEEK, WeeklyDigestComposer.Week.of(quiet()), List.of(), 2)
            .isEmpty()).isFalse();
        assertThat(WeeklyDigestComposer.Week.of(DashboardStatsDto.builder().build()).open()).isZero();
    }
}
