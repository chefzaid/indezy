import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MemberCard, NetworkFields, NetworkMembership } from '../../models';

/** The member network: the account's membership, handles and the members' cards. */
@Injectable({
  providedIn: 'root'
})
export class NetworkService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/network`;

  membership(): Observable<NetworkMembership> {
    return this.http.get<NetworkMembership>(`${this.API_URL}/membership`);
  }

  /** Joins, or changes the handle and the card. */
  join(handle: string, fields: NetworkFields): Observable<NetworkMembership> {
    return this.http.put<NetworkMembership>(`${this.API_URL}/membership`, { handle, fields });
  }

  leave(): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/membership`);
  }

  handle(handle: string): Observable<{ handle: string; available: boolean }> {
    return this.http.get<{ handle: string; available: boolean }>(`${this.API_URL}/handles/${encodeURIComponent(handle)}`);
  }

  member(handle: string): Observable<MemberCard> {
    return this.http.get<MemberCard>(`${this.API_URL}/members/${encodeURIComponent(handle)}`);
  }
}
