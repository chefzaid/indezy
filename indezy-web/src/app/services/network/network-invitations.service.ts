import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SILENT_HTTP_ERRORS } from '../../interceptors/error.interceptor';
import { ContactStanding, PublicNetworkInvitation } from '../../models';

/** Invitations to the member network: a member emails one of their contacts, who reads it or declines from its link. */
@Injectable({ providedIn: 'root' })
export class NetworkInvitationsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/network/invitations`;
  private readonly publicUrl = `${environment.apiUrl}/public/network-invitations`;

  /** Where a contact stands toward the network; silent, a visitor of the network reading none. */
  standing(contactId: number): Observable<ContactStanding> {
    return this.http.get<ContactStanding>(`${this.url}/contacts/${contactId}`, { context: new HttpContext().set(SILENT_HTTP_ERRORS, true) });
  }

  /** Emails the contact an invitation, once. */
  invite(contactId: number): Observable<ContactStanding> {
    return this.http.post<ContactStanding>(`${this.url}/contacts/${contactId}`, null);
  }

  read(token: string): Observable<PublicNetworkInvitation> {
    return this.http.get<PublicNetworkInvitation>(`${this.publicUrl}/${encodeURIComponent(token)}`);
  }

  /** Never to be invited again. */
  decline(token: string): Observable<PublicNetworkInvitation> {
    return this.http.post<PublicNetworkInvitation>(`${this.publicUrl}/${encodeURIComponent(token)}/decline`, null);
  }
}
