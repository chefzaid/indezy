import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Invoice, InvoiceDraft } from '../../models';

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

  setPaid(id: number, paidOn: string | null): Observable<Invoice> {
    return this.http.put<Invoice>(`${this.API_URL}/${id}/payment`, { paidOn });
  }

  downloadPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.API_URL}/${id}/pdf`, { responseType: 'blob' });
  }
}
