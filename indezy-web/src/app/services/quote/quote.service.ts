import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Invoice, Quote, QuoteDraft } from '../../models';

/** The workspace's quotes: drafts, sending, the client's answer, the invoice and the PDF. */
@Injectable({
  providedIn: 'root'
})
export class QuoteService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/quotes`;

  list(): Observable<Quote[]> {
    return this.http.get<Quote[]>(this.API_URL);
  }

  create(draft: QuoteDraft): Observable<Quote> {
    return this.http.post<Quote>(this.API_URL, draft);
  }

  update(id: number, draft: QuoteDraft): Observable<Quote> {
    return this.http.put<Quote>(`${this.API_URL}/${id}`, draft);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  send(id: number, issueDate: string | null): Observable<Quote> {
    return this.http.post<Quote>(`${this.API_URL}/${id}/send`, { issueDate });
  }

  decide(id: number, accepted: boolean): Observable<Quote> {
    return this.http.put<Quote>(`${this.API_URL}/${id}/decision`, { accepted });
  }

  /** A draft invoice of an accepted quote. */
  invoice(id: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.API_URL}/${id}/invoice`, null);
  }

  downloadPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.API_URL}/${id}/pdf`, { responseType: 'blob' });
  }
}
