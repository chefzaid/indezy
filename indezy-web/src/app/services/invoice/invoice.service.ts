import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BankMatch, ContributionEstimate, TaxDeadline, Invoice, InvoiceDraft, InvoiceableReport, PaymentDelay, RevenueCeilings } from '../../models';

/** An accountant's export: the FEC journal entries, or the register of invoices and credit notes as CSV. */
export type AccountingExportFormat = 'FEC' | 'REGISTER';

/** The workspace's invoices: drafts, issuing, payment and PDF. */
@Injectable({
  providedIn: 'root'
})
export class InvoiceService {
  private readonly API_URL = `${environment.apiUrl}/invoices`;

  constructor(private readonly http: HttpClient) {}

  list(freelanceId: number): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(`${this.API_URL}/by-freelance/${freelanceId}`);
  }

  create(draft: InvoiceDraft): Observable<Invoice> {
    return this.http.post<Invoice>(this.API_URL, draft);
  }

  /** The months of activity reports with days worked, not rejected and not invoiced yet, latest first. */
  /** How each client pays: its paid invoices, average days to payment and past the due date. */
  paymentDelays(): Observable<PaymentDelay[]> {
    return this.http.get<PaymentDelay[]>(`${this.API_URL}/payment-delays`);
  }

  /** The year's revenue received against the micro-entreprise ceiling and the VAT franchise thresholds. */
  ceilings(): Observable<RevenueCeilings> {
    return this.http.get<RevenueCeilings>(`${this.API_URL}/ceilings`);
  }

  /** The year's URSSAF contributions on the revenue received, with each declaration's deadline for a micro-entreprise. */
  contributions(): Observable<ContributionEstimate> {
    return this.http.get<ContributionEstimate>(`${this.API_URL}/contributions`);
  }

  /** The CFE and VAT deadlines of the next twelve months, the VAT estimated from the payments received. */
  taxCalendar(): Observable<TaxDeadline[]> {
    return this.http.get<TaxDeadline[]>(`${this.API_URL}/tax-calendar`);
  }

  invoiceableReports(): Observable<InvoiceableReport[]> {
    return this.http.get<InvoiceableReport[]>(`${this.API_URL}/invoiceable-reports`);
  }

  fromReport(projectId: number, year: number, month: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.API_URL}/from-report`, { projectId, year, month });
  }

  creditNote(invoiceId: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.API_URL}/${invoiceId}/credit-note`, null);
  }

  update(id: number, draft: InvoiceDraft): Observable<Invoice> {
    return this.http.put<Invoice>(`${this.API_URL}/${id}`, draft);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  issue(id: number, issueDate: string | null): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.API_URL}/${id}/issue`, { issueDate });
  }

  /** Matches a bank statement's credits (its CSV export) with the invoices awaiting payment; nothing is recorded. */
  reconcile(content: string): Observable<BankMatch[]> {
    return this.http.post<BankMatch[]>(`${this.API_URL}/reconciliation`, { content });
  }

  setPaid(id: number, paidOn: string | null): Observable<Invoice> {
    return this.http.put<Invoice>(`${this.API_URL}/${id}/payment`, { paidOn });
  }

  /** A year of issued invoices for the accountant; the register's headings and numbers follow the language. */
  exportYear(year: number, format: AccountingExportFormat, language: 'FR' | 'EN'): Observable<Blob> {
    return this.http.get(`${this.API_URL}/export/${year}`, { params: { format, language }, responseType: 'blob' });
  }

  downloadPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.API_URL}/${id}/pdf`, { responseType: 'blob' });
  }

  /** A Word copy laid out like the PDF, to read or archive. */
  downloadDocx(id: number): Observable<Blob> {
    return this.http.get(`${this.API_URL}/${id}/docx`, { responseType: 'blob' });
  }
}
