import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ActivityReport,
  ActivityReportDecision,
  ActivityReportLink,
  ActivityReportUpdate,
  PublicActivityReport
} from '../../models/activity-report.models';

/** Header carrying a validation link's token, so the token never appears in a URL the server logs. */
export const REPORT_TOKEN_HEADER = 'X-Report-Token';

/** Activity reports of a mission, and their public validation through a link. */
@Injectable({
  providedIn: 'root'
})
export class ActivityReportService {
  private readonly API_URL = environment.apiUrl;

  constructor(private readonly http: HttpClient) {}

  /** Saved reports of the mission, latest month first. */
  getReports(projectId: number): Observable<ActivityReport[]> {
    return this.http.get<ActivityReport[]>(this.reportsUrl(projectId));
  }

  /** The month's report, or an empty draft with its calendar when nothing was saved yet. */
  getReport(projectId: number, year: number, month: number): Observable<ActivityReport> {
    return this.http.get<ActivityReport>(this.monthUrl(projectId, year, month));
  }

  save(projectId: number, year: number, month: number, update: ActivityReportUpdate): Observable<ActivityReport> {
    return this.http.put<ActivityReport>(this.monthUrl(projectId, year, month), update);
  }

  /** Locks the report and answers its validation link token, shown once. */
  submit(projectId: number, year: number, month: number): Observable<ActivityReportLink> {
    return this.http.post<ActivityReportLink>(`${this.monthUrl(projectId, year, month)}/submit`, null);
  }

  /** Replaces the link of a submitted report; the previous one stops working. */
  renewLink(projectId: number, year: number, month: number): Observable<ActivityReportLink> {
    return this.http.post<ActivityReportLink>(`${this.monthUrl(projectId, year, month)}/link`, null);
  }

  /** Back to draft: revokes the link and clears any approval or rejection. */
  reopen(projectId: number, year: number, month: number): Observable<ActivityReport> {
    return this.http.post<ActivityReport>(`${this.monthUrl(projectId, year, month)}/reopen`, null);
  }

  downloadPdf(projectId: number, year: number, month: number): Observable<Blob> {
    return this.http.get(`${this.monthUrl(projectId, year, month)}/pdf`, { responseType: 'blob' });
  }

  getPublicReport(token: string): Observable<PublicActivityReport> {
    return this.http.get<PublicActivityReport>(`${this.API_URL}/public/activity-reports`, { headers: tokenHeaders(token) });
  }

  downloadPublicPdf(token: string): Observable<Blob> {
    return this.http.get(`${this.API_URL}/public/activity-reports/pdf`, { headers: tokenHeaders(token), responseType: 'blob' });
  }

  decide(token: string, decision: ActivityReportDecision): Observable<PublicActivityReport> {
    return this.http.post<PublicActivityReport>(`${this.API_URL}/public/activity-reports/decision`, decision,
      { headers: tokenHeaders(token) });
  }

  private reportsUrl(projectId: number): string {
    return `${this.API_URL}/projects/${projectId}/activity-reports`;
  }

  private monthUrl(projectId: number, year: number, month: number): string {
    return `${this.reportsUrl(projectId)}/${year}/${month}`;
  }
}

function tokenHeaders(token: string): HttpHeaders {
  return new HttpHeaders({ [REPORT_TOKEN_HEADER]: token });
}
