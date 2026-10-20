import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProjectDto } from '../../models/project.models';
import {
  Offer,
  OfferAlerts,
  OfferFacet,
  OfferMarket,
  OfferPage,
  OfferPublisherBlock,
  OfferQuery,
  OfferRegion,
  OfferSite,
  OfferSiteHealth,
  OfferSiteInfo,
  OfferState,
  OfferStateUpdate,
  SavedOfferSearch
} from '../../models/offer.models';
import { offerQueryToHttpParams } from './offer-query';
import { SILENT_HTTP_ERRORS } from '../../interceptors/error.interceptor';

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

  /** The market of the offers the query finds: its sort and page do not matter. */
  market(query: OfferQuery): Observable<OfferMarket> {
    return this.http.get<OfferMarket>(`${this.API_URL}/market`, { params: offerQueryToHttpParams(query) });
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

  siteHealth(): Observable<OfferSiteHealth> {
    return this.http.get<OfferSiteHealth>(`${this.API_URL}/sites/health`);
  }

  /** Keeps a site out of the workspace's feed, saved searches and market snapshot, or brings it back; errors are the caller's to tell. */
  setSiteExcluded(site: OfferSite, excluded: boolean): Observable<{ site: OfferSite; excluded: boolean }> {
    return this.http.put<{ site: OfferSite; excluded: boolean }>(`${this.API_URL}/sites/${site}/exclusion`, { excluded },
      { context: new HttpContext().set(SILENT_HTTP_ERRORS, true) });
  }

  /** Reads a site now; refused (429, with Retry-After) less than 15 minutes after its last reading. Errors are the caller's to tell. */
  refreshSite(site: OfferSite): Observable<{ site: OfferSite; refreshAvailableAt: string }> {
    return this.http.post<{ site: OfferSite; refreshAvailableAt: string }>(`${this.API_URL}/sites/${site}/refresh`, null,
      { context: new HttpContext().set(SILENT_HTTP_ERRORS, true) });
  }

  blockedPublishers(): Observable<OfferPublisherBlock[]> {
    return this.http.get<OfferPublisherBlock[]>(`${this.API_URL}/blocked-publishers`);
  }

  /** Keeps a publisher's offers out of the feed; blocking one already blocked answers that block. */
  blockPublisher(name: string): Observable<OfferPublisherBlock> {
    return this.http.post<OfferPublisherBlock>(`${this.API_URL}/blocked-publishers`, { name });
  }

  unblockPublisher(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/blocked-publishers/${id}`);
  }

  regions(): Observable<OfferRegion[]> {
    return this.http.get<OfferRegion[]>(`${this.API_URL}/regions`);
  }

  savedSearches(): Observable<SavedOfferSearch[]> {
    return this.http.get<SavedOfferSearch[]>(`${this.API_URL}/searches`);
  }

  /** Saves the query's filters (the server keeps neither its view nor its page) under a name. */
  saveSearch(name: string, query: OfferQuery): Observable<SavedOfferSearch> {
    return this.http.post<SavedOfferSearch>(`${this.API_URL}/searches`, { name, filters: query });
  }

  /** Marks a saved search opened: what it found so far is no longer new. */
  openSearch(id: number): Observable<SavedOfferSearch> {
    return this.http.post<SavedOfferSearch>(`${this.API_URL}/searches/${id}/open`, null);
  }

  deleteSearch(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/searches/${id}`);
  }

  /** Counted in the background for the badge on Offers: a failure is not reported. */
  alerts(): Observable<OfferAlerts> {
    return this.http.get<OfferAlerts>(`${this.API_URL}/searches/alerts`, { context: new HttpContext().set(SILENT_HTTP_ERRORS, true) });
  }
}
