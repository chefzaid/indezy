package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/** What each webhook event says of its object: ids, the business fields a tool acts on, never a person's contact details. */
final class WebhookPayloads {

    private WebhookPayloads() {
    }

    static Map<String, Object> project(Project project, ProjectStatus previous) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", project.getId());
        data.put("role", project.getRole());
        data.put("status", project.getStatus());
        data.put("previousStatus", previous);
        data.put("clientName", project.getClient() == null ? null : project.getClient().getCompanyName());
        data.put("dailyRate", project.getDailyRate());
        return data;
    }

    static Map<String, Object> report(ActivityReport report) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportId", report.getId());
        data.put("projectId", report.getProject().getId());
        data.put("role", report.getProject().getRole());
        data.put("year", report.getReportYear());
        data.put("month", report.getReportMonth());
        data.put("workedDays", ActivityDay.workedDays(report.getDays()));
        data.put("status", report.getStatus());
        return data;
    }

    static Map<String, Object> invoice(Invoice invoice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", invoice.getId());
        data.put("kind", invoice.isCreditNote() ? "CREDIT_NOTE" : "INVOICE");
        data.put("number", invoice.getNumber());
        data.put("status", invoice.getStatus());
        data.put("clientName", invoice.getBuyer() == null ? null : invoice.getBuyer().getName());
        data.put("issueDate", invoice.getIssueDate() == null ? null : invoice.getIssueDate().toString());
        data.put("dueDate", invoice.getDueDate() == null ? null : invoice.getDueDate().toString());
        data.put("total", InvoiceTotals.of(invoice.getLines()).total());
        data.put("paidOn", invoice.getPaidOn() == null ? null : invoice.getPaidOn().toString());
        data.put("creditedNumber", invoice.getCreditedNumber());
        return data;
    }
}
