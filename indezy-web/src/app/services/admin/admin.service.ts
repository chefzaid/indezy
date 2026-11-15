import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdminAccount, AdminAccountPage, AdminOverview, FeatureFlag, FeatureState, ModerationQueue, ReportOutcome } from '../../models';

/** The admin console's calls, answered only to the administrators the deployment names. */
@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private readonly API_URL = `${environment.apiUrl}/admin`;

  constructor(private readonly http: HttpClient) {}

  overview(): Observable<AdminOverview> {
    return this.http.get<AdminOverview>(`${this.API_URL}/overview`);
  }

  accounts(query: string, page: number): Observable<AdminAccountPage> {
    return this.http.get<AdminAccountPage>(`${this.API_URL}/accounts`, { params: new HttpParams().set('query', query).set('page', page) });
  }

  setDisabled(id: number, disabled: boolean): Observable<AdminAccount> {
    return this.http.post<AdminAccount>(`${this.API_URL}/accounts/${id}/${disabled ? 'disable' : 'enable'}`, null);
  }

  features(): Observable<FeatureState[]> {
    return this.http.get<FeatureState[]>(`${this.API_URL}/features`);
  }

  setFeature(flag: FeatureFlag, enabled: boolean): Observable<FeatureState[]> {
    return this.http.put<FeatureState[]>(`${this.API_URL}/features/${flag}`, { enabled });
  }

  /** The open reports of the member network, the oldest first, and the latest decisions. */
  reports(): Observable<ModerationQueue> {
    return this.http.get<ModerationQueue>(`${this.API_URL}/network/reports`);
  }

  /** Decides a report and every open report of the same target; the days for a suspension. */
  decide(id: number, outcome: ReportOutcome, note: string | null, days: number | null): Observable<ModerationQueue> {
    return this.http.put<ModerationQueue>(`${this.API_URL}/network/reports/${id}`, { outcome, note, days });
  }
}
