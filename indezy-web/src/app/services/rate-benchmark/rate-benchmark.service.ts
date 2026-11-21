import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type RateSeniority = 'JUNIOR' | 'CONFIRMED' | 'SENIOR' | 'EXPERT';
export type RateRegion = 'ILE_DE_FRANCE' | 'REGIONS' | 'REMOTE';

/** A figure of the benchmark: how many freelancers back it and, from the threshold on, the median and the middle half. */
export interface RateFigure {
  skill: string;
  seniority: RateSeniority | null;
  region: RateRegion | null;
  freelancers: number;
  threshold: number;
  median: number | null;
  low: number | null;
  high: number | null;
  /** Whether the reader shares their own rates. */
  sharing: boolean;
}

export interface RateSkills {
  skills: { skill: string; freelancers: number }[];
  threshold: number;
  sharing: boolean;
}

/** The network's rate benchmark: daily rates members share, anonymized, by skill, seniority and region. */
@Injectable({
  providedIn: 'root'
})
export class RateBenchmarkService {
  private readonly http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/network/benchmark`;

  skills(): Observable<RateSkills> {
    return this.http.get<RateSkills>(`${this.API_URL}/skills`);
  }

  figure(skill: string, seniority: RateSeniority | null, region: RateRegion | null): Observable<RateFigure> {
    let params = new HttpParams().set('skill', skill);
    if (seniority) {
      params = params.set('seniority', seniority);
    }
    if (region) {
      params = params.set('region', region);
    }
    return this.http.get<RateFigure>(this.API_URL, { params });
  }
}
