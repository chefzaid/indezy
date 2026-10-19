import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProjectDto } from '../../models/project.models';
import {
  Offer,
  OfferFacet,
  OfferPage,
  OfferQuery,
  OfferRegion,
  OfferSiteInfo,
  OfferState,
  OfferStateUpdate
} from '../../models/offer.models';
import { offerQueryToHttpParams } from './offer-query';

/** The freelance offer feed: search, per-workspace state and import into the pipeline. */
@Injectable({
  providedIn: 'root'
})
export class OfferService {
  private readonly API_URL = `${environment.apiUrl}/offers`;

  constructor(private readonly http: HttpClient) {}

  search(query: OfferQuery): Observable<OfferPage> {
    return this.http.get<OfferPage>(this.API_URL, { params: offerQueryToHttpParams(query) });
  }

  get(id: number): Observable<Offer> {
    return this.http.get<Offer>(`${this.API_URL}/${id}`);
  }

  updateState(id: number, update: OfferStateUpdate): Observable<OfferState> {
    return this.http.put<OfferState>(`${this.API_URL}/${id}/state`, update);
  }

  importOffer(id: number): Observable<ProjectDto> {
    return this.http.post<ProjectDto>(`${this.API_URL}/${id}/import`, null);
  }

  sites(): Observable<OfferSiteInfo[]> {
    return this.http.get<OfferSiteInfo[]>(`${this.API_URL}/sites`);
  }

  skills(prefix: string): Observable<OfferFacet[]> {
    return this.http.get<OfferFacet[]>(`${this.API_URL}/skills`, { params: new HttpParams().set('q', prefix) });
  }

  regions(): Observable<OfferRegion[]> {
    return this.http.get<OfferRegion[]>(`${this.API_URL}/regions`);
  }
}
