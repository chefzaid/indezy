package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.dto.PurchaseOrderDtos;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.PurchaseOrder;
import dev.swirlit.indezy.repository.ActivityReportRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A mission's purchase orders and their amendments (see docs/features.md#purchase-orders), with what its activity
 * reports used of each: the days worked over the order's period, their amount at the mission's daily rate, and an
 * alert from 80 % of the closest ceiling.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final BigDecimal NEAR = new BigDecimal("0.8");

    private final PurchaseOrderRepository orders;
    private final ProjectRepository projects;
    private final ActivityReportRepository reports;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PurchaseOrderDtos.Order> list(Long freelanceId, Long projectId) {
        Project project = project(freelanceId, projectId);
        return views(project, orders.findByProjectIdOrderByIdAsc(projectId));
    }

    /** Every mission's orders with their amendments and usage, for the account data export. */
    @Transactional(readOnly = true)
    public List<PurchaseOrderDtos.Order> export(Long freelanceId) {
        return orders.findByProjectFreelanceIdOrderByIdAsc(freelanceId).stream()
            .collect(Collectors.groupingBy(PurchaseOrder::getProject)).entrySet().stream()
            .flatMap(entry -> views(entry.getKey(), entry.getValue()).stream())
            .sorted(Comparator.comparing(PurchaseOrderDtos.Order::id)).toList();
    }

    /** Writes an order, or an amendment to one of the mission's orders, and answers with the order as it now stands. */
    @Transactional
    public PurchaseOrderDtos.Order create(Long freelanceId, Long projectId, PurchaseOrderDtos.Write write) {
        Project project = project(freelanceId, projectId);
        PurchaseOrder order = new PurchaseOrder();
        order.setProject(project);
        if (write.amendsId() != null) {
            PurchaseOrder amended = orders.findById(write.amendsId())
                .filter(found -> found.getProject().getId().equals(projectId) && !found.isAmendment())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found"));
            order.setAmendsId(amended.getId());
        }
        apply(order, write);
        orders.save(order);
        return orderOf(project, order);
    }

    /** Changes an order or an amendment (an amendment stays with its order), answering with the order as it now stands. */
    @Transactional
    public PurchaseOrderDtos.Order update(Long freelanceId, Long id, PurchaseOrderDtos.Write write) {
        PurchaseOrder order = order(freelanceId, id);
        apply(order, write);
        return orderOf(order.getProject(), order);
    }

    /** Deletes an amendment, or an order with its amendments. */
    @Transactional
    public void delete(Long freelanceId, Long id) {
        PurchaseOrder order = order(freelanceId, id);
        if (!order.isAmendment()) {
            orders.deleteAll(orders.findByAmendsId(order.getId()));
        }
        orders.delete(order);
    }

    /** The workspace's orders running today that used 80 % of a ceiling or more, the most used first. */
    @Transactional(readOnly = true)
    public List<DashboardStatsDto.PurchaseOrderAlert> alerts(Long freelanceId) {
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        Map<Project, List<PurchaseOrder>> byProject = orders.findByProjectFreelanceIdOrderByIdAsc(freelanceId).stream()
            .collect(Collectors.groupingBy(PurchaseOrder::getProject));
        return byProject.entrySet().stream()
            .flatMap(entry -> views(entry.getKey(), entry.getValue()).stream()
                .filter(order -> order.usage().level() != PurchaseOrderDtos.Level.OK && !order.periodStart().isAfter(today) &&
                    (order.usage().periodEnd() == null || !order.usage().periodEnd().isBefore(today)))
                .map(order -> new DashboardStatsDto.PurchaseOrderAlert(order.id(), entry.getKey().getId(), entry.getKey().getRole(),
                    order.number(), order.usage().share().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).intValue(),
                    order.usage().daysLeft(), order.usage().level() == PurchaseOrderDtos.Level.EXCEEDED)))
            .sorted(Comparator.comparing(DashboardStatsDto.PurchaseOrderAlert::getPercentUsed).reversed())
            .toList();
    }

    private void apply(PurchaseOrder order, PurchaseOrderDtos.Write write) {
        LocalDate start;
        if (order.isAmendment()) {
            if (write.dayCeiling() == null && write.amountCeiling() == null && write.periodEnd() == null) {
                throw new FieldRejectedException("dayCeiling", "An amendment adds days, euros or a later end");
            }
            start = orders.findById(order.getAmendsId()).map(PurchaseOrder::getPeriodStart).orElse(null);
            order.setPeriodStart(null);
        } else {
            if (write.periodStart() == null) {
                throw new FieldRejectedException("periodStart", "An order starts on a date");
            }
            if (write.dayCeiling() == null && write.amountCeiling() == null) {
                throw new FieldRejectedException("dayCeiling", "An order has a ceiling in days or in euros");
            }
            start = write.periodStart();
            order.setPeriodStart(start);
        }
        if (write.periodEnd() != null && start != null && write.periodEnd().isBefore(start)) {
            throw new FieldRejectedException("periodEnd", "The end comes before the start");
        }
        order.setNumber(write.number().strip());
        order.setPeriodEnd(write.periodEnd());
        order.setDayCeiling(cents(write.dayCeiling()));
        order.setAmountCeiling(cents(write.amountCeiling()));
        order.setNotes(write.notes() == null || write.notes().isBlank() ? null : write.notes().strip());
    }

    /** The order an order or an amendment belongs to, as it now stands. */
    private PurchaseOrderDtos.Order orderOf(Project project, PurchaseOrder written) {
        Long orderId = written.isAmendment() ? written.getAmendsId() : written.getId();
        return views(project, orders.findByProjectIdOrderByIdAsc(project.getId())).stream()
            .filter(order -> order.id().equals(orderId)).findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found"));
    }

    private List<PurchaseOrderDtos.Order> views(Project project, List<PurchaseOrder> all) {
        Map<Long, List<PurchaseOrder>> amendments = all.stream().filter(PurchaseOrder::isAmendment)
            .collect(Collectors.groupingBy(PurchaseOrder::getAmendsId));
        List<ActivityDay> days = all.isEmpty() ? List.of() : reports.findByProjectIdOrderByReportYearDescReportMonthDesc(project.getId())
            .stream().flatMap(report -> report.getDays().stream()).toList();
        return all.stream().filter(order -> !order.isAmendment())
            .map(order -> view(project, order, amendments.getOrDefault(order.getId(), List.of()), days)).toList();
    }

    private static PurchaseOrderDtos.Order view(Project project, PurchaseOrder order, List<PurchaseOrder> amendments,
                                                List<ActivityDay> days) {
        List<PurchaseOrder> parts = Stream.concat(Stream.of(order), amendments.stream()).toList();
        BigDecimal dayCeiling = sum(parts.stream().map(PurchaseOrder::getDayCeiling));
        BigDecimal amountCeiling = sum(parts.stream().map(PurchaseOrder::getAmountCeiling));
        LocalDate end = parts.stream().map(PurchaseOrder::getPeriodEnd).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        BigDecimal worked = BigDecimal.valueOf(days.stream()
            .filter(day -> !day.getDate().isBefore(order.getPeriodStart()) && (end == null || !day.getDate().isAfter(end)))
            .mapToDouble(ActivityDay::getWorkedDuration).sum()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = project.getDailyRate() == null ? null : BigDecimal.valueOf(project.getDailyRate());
        BigDecimal amount = rate == null ? null : worked.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        BigDecimal share = null;
        BigDecimal daysLeft = null;
        if (dayCeiling != null) {
            share = worked.divide(dayCeiling, 4, RoundingMode.HALF_UP);
            daysLeft = dayCeiling.subtract(worked);
        }
        if (amountCeiling != null && amount != null) {
            BigDecimal byAmount = amount.divide(amountCeiling, 4, RoundingMode.HALF_UP);
            share = share == null ? byAmount : share.max(byAmount);
            BigDecimal left = amountCeiling.subtract(amount).divide(rate, 2, RoundingMode.FLOOR);
            daysLeft = daysLeft == null ? left : daysLeft.min(left);
        }
        PurchaseOrderDtos.Level level = PurchaseOrderDtos.Level.OK;
        if (share != null && share.compareTo(BigDecimal.ONE) > 0) {
            level = PurchaseOrderDtos.Level.EXCEEDED;
        } else if (share != null && share.compareTo(NEAR) >= 0) {
            level = PurchaseOrderDtos.Level.NEAR;
        }
        List<PurchaseOrderDtos.Amendment> amendmentViews = amendments.stream().map(amendment -> new PurchaseOrderDtos.Amendment(
            amendment.getId(), amendment.getNumber(), amendment.getPeriodEnd(), amendment.getDayCeiling(), amendment.getAmountCeiling(),
            amendment.getNotes())).toList();
        return new PurchaseOrderDtos.Order(order.getId(), project.getId(), order.getNumber(), order.getPeriodStart(), order.getPeriodEnd(),
            order.getDayCeiling(), order.getAmountCeiling(), order.getNotes(), amendmentViews,
            new PurchaseOrderDtos.Usage(dayCeiling, amountCeiling, end, worked, amount, share, daysLeft, level));
    }

    /** As stored: to the hundredth. */
    private static BigDecimal cents(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** The sum of the values given, or empty when none is. */
    private static BigDecimal sum(Stream<BigDecimal> values) {
        return values.filter(Objects::nonNull).reduce(BigDecimal::add).orElse(null);
    }

    private Project project(Long freelanceId, Long projectId) {
        return projects.findById(projectId)
            .filter(project -> project.getFreelance() != null && freelanceId.equals(project.getFreelance().getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private PurchaseOrder order(Long freelanceId, Long id) {
        return orders.findByIdAndProjectFreelanceId(id, freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found"));
    }
}
