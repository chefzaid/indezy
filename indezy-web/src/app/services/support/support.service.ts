import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ReceivedSupportRequest, SupportKind, SupportRequest } from '../../models';

/** Requests to support: a member's own, and the administrators' list and answers. */
@Injectable({ providedIn: 'root' })
export class SupportService {
  private readonly http = inject(HttpClient);

  send(request: { kind: SupportKind; subject: string; description: string; context: string | null }): Observable<SupportRequest> {
    return this.http.post<SupportRequest>(`${environment.apiUrl}/support/requests`, request);
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
