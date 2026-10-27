import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { YearlyReview } from '../../models';

/** A freelancer's year in review, as figures and as a PDF report. */
@Injectable({
  providedIn: 'root'
})
export class YearlyReviewService {
  private readonly API_URL = `${environment.apiUrl}/projects/review`;

  constructor(private readonly http: HttpClient) {}

  get(freelanceId: number, year: number): Observable<YearlyReview> {
    return this.http.get<YearlyReview>(`${this.API_URL}/${freelanceId}`, { params: new HttpParams().set('year', year) });
  }

  downloadPdf(freelanceId: number, year: number, language: 'FR' | 'EN'): Observable<Blob> {
    return this.http.get(`${this.API_URL}/${freelanceId}/pdf`, {
      params: new HttpParams().set('year', year).set('language', language),
      responseType: 'blob'
    });
  }
}
