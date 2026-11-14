import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MemberCard, MemberPage, NetworkAvailabilitySetting, NetworkFields, NetworkMembership, NetworkRestrictions } from '../../models';

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
  join(handle: string, fields: NetworkFields, availability: NetworkAvailabilitySetting): Observable<NetworkMembership> {
    return this.http.put<NetworkMembership>(`${this.API_URL}/membership`, { handle, fields, availability });
  }

  /** Verifies the member against the public company registry, from the billing SIRET. */
  verify(): Observable<NetworkMembership> {
    return this.http.post<NetworkMembership>(`${this.API_URL}/membership/verification`, null);
  }

  leave(): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/membership`);
  }

  handle(handle: string): Observable<{ handle: string; available: boolean }> {
    return this.http.get<{ handle: string; available: boolean }>(`${this.API_URL}/handles/${encodeURIComponent(handle)}`);
  }

  member(handle: string): Observable<MemberCard> {
    return this.http.get<MemberCard>(`${this.member$(handle)}`);
  }

  page(handle: string): Observable<MemberPage> {
    return this.http.get<MemberPage>(`${this.member$(handle)}/page`);
  }

  follow(handle: string): Observable<MemberPage> {
    return this.http.put<MemberPage>(`${this.member$(handle)}/follow`, null);
  }

  unfollow(handle: string): Observable<MemberPage> {
    return this.http.delete<MemberPage>(`${this.member$(handle)}/follow`);
  }

  followers(handle: string): Observable<MemberCard[]> {
    return this.http.get<MemberCard[]>(`${this.member$(handle)}/followers`);
  }

  following(handle: string): Observable<MemberCard[]> {
    return this.http.get<MemberCard[]>(`${this.member$(handle)}/following`);
  }

  /** Mutes a member, or no longer. */
  mute(handle: string, muted: boolean): Observable<MemberPage> {
    const url = `${this.member$(handle)}/mute`;
    return muted ? this.http.put<MemberPage>(url, null) : this.http.delete<MemberPage>(url);
  }

  /** Blocks a member, or no longer. */
  block(handle: string, blocked: boolean): Observable<MemberPage> {
    const url = `${this.member$(handle)}/block`;
    return blocked ? this.http.put<MemberPage>(url, null) : this.http.delete<MemberPage>(url);
  }

  restrictions(): Observable<NetworkRestrictions> {
    return this.http.get<NetworkRestrictions>(`${this.API_URL}/restrictions`);
  }

  /** Stops a member following the viewer. */
  removeFollower(handle: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/followers/${encodeURIComponent(handle)}`);
  }

  private member$(handle: string): string {
    return `${this.API_URL}/members/${encodeURIComponent(handle)}`;
  }
}
