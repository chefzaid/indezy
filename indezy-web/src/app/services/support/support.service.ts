import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ReceivedSupportRequest, SupportAttachment, SupportKind, SupportRequest } from '../../models';

/** Requests to support: a member's own, and the administrators' list and answers. */
@Injectable({ providedIn: 'root' })
export class SupportService {
  private readonly http = inject(HttpClient);

  send(request: { kind: SupportKind; subject: string; description: string; context: string | null }): Observable<SupportRequest> {
    return this.http.post<SupportRequest>(`${environment.apiUrl}/support/requests`, request);
  }

  /** Adds a screenshot or a PDF to a request just sent. */
  attach(requestId: number, file: File): Observable<SupportAttachment> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<SupportAttachment>(`${environment.apiUrl}/support/requests/${requestId}/attachments`, form);
  }

  /** A request's file, for its author or an administrator. */
  file(attachment: SupportAttachment): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/support/requests/${attachment.requestId}/attachments/${attachment.id}`, { responseType: 'blob' });
  }

  mine(): Observable<SupportRequest[]> {
    return this.http.get<SupportRequest[]>(`${environment.apiUrl}/support/requests`);
  }

  received(): Observable<ReceivedSupportRequest[]> {
    return this.http.get<ReceivedSupportRequest[]>(`${environment.apiUrl}/admin/support/requests`);
  }

  answer(id: number, answer: string, close: boolean): Observable<SupportRequest> {
    return this.http.put<SupportRequest>(`${environment.apiUrl}/admin/support/requests/${id}`, { answer, close });
  }
}
