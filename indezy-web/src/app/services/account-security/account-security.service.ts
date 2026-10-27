import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AccountEventPage, AccountSession, ApiToken, ApiTokenScope, CreatedApiToken } from '../../models';

/** The signed-in account's open sessions, its activity log and its personal API tokens. */
@Injectable({
  providedIn: 'root'
})
export class AccountSecurityService {
  private readonly API_URL = `${environment.apiUrl}/users`;

  constructor(private readonly http: HttpClient) {}

  sessions(): Observable<AccountSession[]> {
    return this.http.get<AccountSession[]>(`${this.API_URL}/sessions`);
  }

  end(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/sessions/${id}`);
  }

  endOthers(): Observable<{ count: number }> {
    return this.http.post<{ count: number }>(`${this.API_URL}/sessions/end-others`, null);
  }

  activity(page: number): Observable<AccountEventPage> {
    return this.http.get<AccountEventPage>(`${this.API_URL}/activity`, { params: new HttpParams().set('page', page) });
  }

  apiTokens(): Observable<ApiToken[]> {
    return this.http.get<ApiToken[]>(`${this.API_URL}/api-tokens`);
  }

  /** A token reading the given parts of the workspace, or all of it when none is given. */
  createApiToken(name: string, expiresInDays: number | null, scopes: ApiTokenScope[] = []): Observable<CreatedApiToken> {
    return this.http.post<CreatedApiToken>(`${this.API_URL}/api-tokens`, { name, expiresInDays, scopes });
  }

  revokeApiToken(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/api-tokens/${id}`);
  }
}
