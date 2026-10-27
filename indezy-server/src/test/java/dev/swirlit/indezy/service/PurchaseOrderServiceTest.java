package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.dto.PurchaseOrderDtos;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.PurchaseOrder;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.repository.ActivityReportRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseOrderServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private final PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ActivityReportRepository reports = mock(ActivityReportRepository.class);
    private final PurchaseOrderService service = new PurchaseOrderService(orders, projects, reports,
        Clock.fixed(TODAY.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    private Project project;

    @BeforeEach
    void mission() {
        Freelance freelance = new Freelance();
        freelance.setId(7L);
        project = new Project();
        project.setId(2L);
        project.setRole("Architecte");
        project.setFreelance(freelance);
        project.setDailyRate(500);
        when(projects.findById(2L)).thenReturn(Optional.of(project));
        ActivityReport report = new ActivityReport();
        report.setDays(List.of(new ActivityDay(LocalDate.of(2026, 10, 5), ActivityDayType.ONSITE, false),
            new ActivityDay(LocalDate.of(2026, 10, 6), ActivityDayType.REMOTE, false),
            new ActivityDay(LocalDate.of(2026, 10, 7), ActivityDayType.ONSITE, false),
            new ActivityDay(LocalDate.of(2026, 10, 8), ActivityDayType.REMOTE, false)));
        when(reports.findByProjectIdOrderByReportYearDescReportMonthDesc(2L)).thenReturn(List.of(report));
    }

    @Test
    void takesTheClosestOfBothCeilings() {
        // Four days of ten (40 %), but €2,000 of €2,100 (95 %), so 0.2 days left at €500.
        PurchaseOrder order = order(1L, LocalDate.of(2026, 10, 1), null, "10", "2100");
        when(orders.findByProjectIdOrderByIdAsc(2L)).thenReturn(List.of(order));

        PurchaseOrderDtos.Usage usage = service.list(7L, 2L).get(0).usage();

        assertThat(usage.share()).isEqualByComparingTo("0.9524");
        assertThat(usage.daysLeft()).isEqualByComparingTo("0.20");
        assertThat(usage.level()).isEqualTo(PurchaseOrderDtos.Level.NEAR);
    }

    @Test
    void cannotFollowEurosWithoutADailyRate() {
        project.setDailyRate(null);
        PurchaseOrder order = order(1L, LocalDate.of(2026, 10, 1), null, null, "1000");
        when(orders.findByProjectIdOrderByIdAsc(2L)).thenReturn(List.of(order));

        PurchaseOrderDtos.Usage usage = service.list(7L, 2L).get(0).usage();

        assertThat(usage.amount()).isNull();
        assertThat(usage.share()).isNull();
        assertThat(usage.daysLeft()).isNull();
        assertThat(usage.level()).isEqualTo(PurchaseOrderDtos.Level.OK);
    }

    @Test
    void alertsOnlyOnOrdersRunningToday() {
        // All used in full (EXCEEDED needs more than the ceiling), but one starts tomorrow and one ended yesterday.
        PurchaseOrder full = order(1L, LocalDate.of(2026, 10, 1), null, "4", null);
        PurchaseOrder future = order(2L, TODAY.plusDays(1), null, "1", null);
        PurchaseOrder ended = order(3L, LocalDate.of(2026, 10, 1), TODAY.minusDays(1), "1", null);
        PurchaseOrder endsToday = order(4L, LocalDate.of(2026, 10, 1), TODAY, "3", null);
        when(orders.findByProjectFreelanceIdOrderByIdAsc(7L)).thenReturn(List.of(full, future, ended, endsToday));

        List<DashboardStatsDto.PurchaseOrderAlert> alerts = service.alerts(7L);

        assertThat(alerts).extracting(DashboardStatsDto.PurchaseOrderAlert::getOrderId).containsExactly(4L, 1L);
        assertThat(alerts.get(0).isExceeded()).isTrue();
        assertThat(alerts.get(1).isExceeded()).isFalse();
        assertThat(alerts.get(1).getPercentUsed()).isEqualTo(100);
    }

    @Test
    void refusesAnAmendmentEndingBeforeItsOrderStarts() {
        PurchaseOrder order = order(1L, LocalDate.of(2026, 10, 1), null, "4", null);
        PurchaseOrder amendment = order(5L, null, null, "1", null);
        amendment.setAmendsId(1L);
        when(orders.findByIdAndProjectFreelanceId(5L, 7L)).thenReturn(Optional.of(amendment));
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThatThrownBy(() -> service.update(7L, 5L, new PurchaseOrderDtos.Write(null, "A", null, LocalDate.of(2026, 9, 30), null,
            null, null))).isInstanceOf(FieldRejectedException.class).hasMessage("The end comes before the start");
    }

    private PurchaseOrder order(Long id, LocalDate start, LocalDate end, String days, String amount) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(id);
        order.setProject(project);
        order.setNumber("BC-" + id);
        order.setPeriodStart(start);
        order.setPeriodEnd(end);
        order.setDayCeiling(days == null ? null : new BigDecimal(days));
        order.setAmountCeiling(amount == null ? null : new BigDecimal(amount));
        return order;
    }
}
